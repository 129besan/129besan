package dev.besan.browserbrake.rules

import android.content.Context
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.RuleConfig
import dev.besan.browserbrake.TargetApps
import org.json.JSONArray

object RuleRepository {
    private const val KEY_RULES = "v04_rules_json"
    private const val KEY_ACTIVE_RUNTIME_RULE = "v04_active_runtime_rule_id"

    @JvmStatic
    fun ensureMigrated(context: Context) {
        val prefs = Prefs.p(context)
        if (prefs.contains(KEY_RULES)) return

        val migrated = BrowserRule(
            name = RuleConfig.ruleName(context),
            enabled = Prefs.isLockEnabled(context),
            browsers = RuleConfig.includeBrowsers(context),
            customPackages = RuleConfig.customPackages(context),
            allPlaces = PlaceStore.isAllPlaces(context),
            placeIds = PlaceStore.selectedIds(context),
            challengeWait = RuleConfig.challengeWait(context),
            challengePhoneBreak = RuleConfig.challengePhoneBreak(context),
            challengeWalk = RuleConfig.challengeWalk(context),
            challengeAll = RuleConfig.challengeAll(context),
            waitMs = RuleConfig.waitMs(context),
            phoneBreakMs = RuleConfig.phoneBreakMs(context),
            walkSteps = RuleConfig.walkSteps(context),
            readyTimeoutMs = RuleConfig.readyTimeoutMs(context),
            askSessionDuration = RuleConfig.askSessionDuration(context),
            defaultSessionUsageMs = RuleConfig.defaultSessionUsageMs(context),
            sessionWindowMs = RuleConfig.sessionWindowMs(context),
            dailyUsageLimitMs = RuleConfig.dailyUsageLimitMs(context),
            dailySessionLimit = RuleConfig.dailySessionLimit(context),
            recoveryMs = RuleConfig.recoveryMs(context),
            escalationMode = RuleConfig.escalationMode(context)
        )
        writeRules(context, listOf(migrated))
        syncGlobalEnabled(context)
    }

    @JvmStatic
    fun getRules(context: Context): List<BrowserRule> {
        ensureMigrated(context)
        val raw = Prefs.p(context).getString(KEY_RULES, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(BrowserRule.fromJson(arr.getJSONObject(i)))
            }
        }.getOrDefault(emptyList())
    }

    @JvmStatic
    fun getRule(context: Context, id: String?): BrowserRule? =
        if (id.isNullOrBlank()) null else getRules(context).firstOrNull { it.id == id }

    @JvmStatic
    fun saveRule(context: Context, rule: BrowserRule) {
        val current = getRules(context).toMutableList()
        val index = current.indexOfFirst { it.id == rule.id }
        if (index >= 0) current[index] = rule else current += rule
        writeRules(context, current)
        syncGlobalEnabled(context)
    }

    @JvmStatic
    fun deleteRule(context: Context, id: String) {
        writeRules(context, getRules(context).filterNot { it.id == id })
        syncGlobalEnabled(context)
        if (activeRuntimeRuleId(context) == id) {
            Prefs.clearTransientState(context)
            clearActiveRuntimeRule(context)
        }
    }

    @JvmStatic
    fun createRule(context: Context, name: String = "新しいルール"): BrowserRule {
        val rule = BrowserRule(name = name, browsers = false, challengePhoneBreak = true)
        saveRule(context, rule)
        return rule
    }

    @JvmStatic
    fun isEffective(rule: BrowserRule, now: Long = System.currentTimeMillis()): Boolean =
        rule.enabled && (rule.pausedUntilMs <= 0L || rule.pausedUntilMs <= now)

    @JvmStatic
    fun pauseRule(context: Context, id: String, untilMs: Long) {
        getRule(context, id)?.let { saveRule(context, it.copy(pausedUntilMs = untilMs)) }
    }

    @JvmStatic
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        getRule(context, id)?.let {
            saveRule(context, it.copy(enabled = enabled, pausedUntilMs = if (enabled) 0L else it.pausedUntilMs))
        }
    }

    @JvmStatic
    fun findMatchingRule(context: Context, pkg: String?): BrowserRule? =
        getRules(context).firstOrNull { rule ->
            isEffective(rule) && TargetGroupCatalog.packageBelongs(context, rule, pkg)
        }

    @JvmStatic
    fun packageBelongsToRule(context: Context, ruleId: String?, pkg: String?): Boolean =
        getRule(context, ruleId)?.let { TargetGroupCatalog.packageBelongs(context, it, pkg) } == true

    @JvmStatic
    fun conflicts(context: Context, candidate: BrowserRule): List<String> {
        if (!candidate.enabled) return emptyList()
        val browserPkgs by lazy { TargetApps.browserPackages(context) }
        return getRules(context)
            .asSequence()
            .filter { it.id != candidate.id && it.enabled }
            .filter { other ->
                val direct = candidate.customPackages.intersect(other.customPackages).isNotEmpty()
                val browserGroup = candidate.browsers && other.browsers
                val snsGroup = candidate.sns && other.sns
                val candidateCustomHitsOtherBrowser =
                    other.browsers && candidate.customPackages.any { it in browserPkgs }
                val otherCustomHitsCandidateBrowser =
                    candidate.browsers && other.customPackages.any { it in browserPkgs }
                val candidateCustomHitsOtherSns =
                    other.sns && candidate.customPackages.any(TargetGroupCatalog::isSnsPackage)
                val otherCustomHitsCandidateSns =
                    candidate.sns && other.customPackages.any(TargetGroupCatalog::isSnsPackage)
                direct || browserGroup || snsGroup ||
                    candidateCustomHitsOtherBrowser || otherCustomHitsCandidateBrowser ||
                    candidateCustomHitsOtherSns || otherCustomHitsCandidateSns
            }
            .map { it.name }
            .toList()
    }

    @JvmStatic
    fun activateForRuntime(context: Context, ruleId: String): Boolean {
        val rule = getRule(context, ruleId) ?: return false
        Prefs.p(context).edit()
            .putString(KEY_ACTIVE_RUNTIME_RULE, rule.id)
            .putString("rule_name", rule.name)
            .putBoolean("include_browsers", rule.browsers)
            .putStringSet("custom_packages", rule.customPackages)
            .putBoolean("challenge_wait_enabled", rule.challengeWait)
            .putBoolean("challenge_phone_enabled", rule.challengePhoneBreak)
            .putBoolean("challenge_walk_enabled", rule.challengeWalk)
            .putBoolean("challenge_all", rule.challengeAll)
            .putLong("wait_ms", rule.waitMs)
            .putLong("phone_break_ms", rule.phoneBreakMs)
            .putInt("walk_steps", rule.walkSteps)
            .putLong("ready_timeout_ms", rule.readyTimeoutMs)
            .putBoolean("ask_session_duration", rule.askSessionDuration)
            .putLong("default_session_usage_ms", rule.defaultSessionUsageMs)
            .putLong("session_window_ms", rule.sessionWindowMs)
            .putLong("daily_usage_limit_ms", rule.dailyUsageLimitMs)
            .putInt("daily_session_limit", rule.dailySessionLimit)
            .putLong("recovery_ms", rule.recoveryMs)
            .putString("escalation_mode", rule.escalationMode)
            .apply()

        PlaceStore.setAllPlaces(context, rule.allPlaces)
        PlaceStore.setSelectedIds(context, rule.placeIds)
        return true
    }

    @JvmStatic
    fun activeRuntimeRuleId(context: Context): String =
        Prefs.p(context).getString(KEY_ACTIVE_RUNTIME_RULE, "") ?: ""

    @JvmStatic
    fun clearActiveRuntimeRule(context: Context) {
        Prefs.p(context).edit().remove(KEY_ACTIVE_RUNTIME_RULE).apply()
    }

    @JvmStatic
    fun ruleMetricKey(ruleId: String, base: String): String =
        "rule:$ruleId:$base"

    @JvmStatic
    fun dailyUsageRaw(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)

    @JvmStatic
    fun dailySessionsRaw(context: Context, ruleId: String): Int =
        Prefs.p(context).getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)

    @JvmStatic
    fun storedEscalationLevel(context: Context, ruleId: String): Int =
        Prefs.p(context).getInt(ruleMetricKey(ruleId, "escalation_level"), 0)

    @JvmStatic
    fun syncGlobalEnabled(context: Context) {
        val any = getRules(context).any { it.enabled }
        val current = Prefs.isLockEnabled(context)
        if (current != any) Prefs.setLockEnabled(context, any)
    }

    private fun writeRules(context: Context, rules: List<BrowserRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        Prefs.p(context).edit().putString(KEY_RULES, arr.toString()).apply()
    }
}
