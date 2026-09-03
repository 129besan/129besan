package dev.besan.browserbrake.rules

import android.content.Context
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.RuleConfig
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import dev.besan.browserbrake.TargetApps
import org.json.JSONArray
import java.util.Calendar

data class DailyRecord(
    val dayKey: String,
    val label: String,
    val usageMs: Long,
    val sessions: Int,
    val hasData: Boolean
)

object RuleRepository {
    private const val KEY_RULES = "v04_rules_json"
    private const val KEY_ACTIVE_RUNTIME_RULE = "v04_active_runtime_rule_id"
    private const val KEY_RUNTIME_SNAPSHOT_AT = "v041_runtime_snapshot_at"

    @JvmStatic
    fun ensureMigrated(context: Context) {
        val prefs = Prefs.p(context)
        if (prefs.contains(KEY_RULES)) return

        val migrated = BrowserRule(
            name = RuleConfig.ruleName(context),
            enabled = Prefs.isLockEnabled(context),
            fullLock = RuleConfig.fullLock(context),
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
        RuleRuntimeStore.clearRuntime(context, id)
    }

    @JvmStatic
    fun createRule(context: Context, name: String = "新しい制限"): BrowserRule {
        val rule = BrowserRule(name = name, browsers = false, challengePhoneBreak = true)
        saveRule(context, rule)
        return rule
    }

    @JvmStatic
    @JvmOverloads
    fun isEffective(rule: BrowserRule, now: Long = System.currentTimeMillis()): Boolean =
        rule.enabled && (rule.pausedUntilMs <= 0L || rule.pausedUntilMs <= now)

    @JvmStatic
    fun pauseRule(context: Context, id: String, untilMs: Long) {
        getRule(context, id)?.let { saveRule(context, it.copy(pausedUntilMs = untilMs)) }
        if (untilMs > System.currentTimeMillis()) {
            RuleRuntimeStore.clearRuntime(context, id)
        }
    }

    @JvmStatic
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        getRule(context, id)?.let {
            saveRule(context, it.copy(enabled = enabled, pausedUntilMs = if (enabled) 0L else it.pausedUntilMs))
        }
        if (!enabled) {
            RuleRuntimeStore.clearRuntime(context, id)
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
            .putLong(KEY_RUNTIME_SNAPSHOT_AT, System.currentTimeMillis())
            .putString("rule_name", rule.name)
            .putBoolean("full_lock", rule.fullLock)
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
        Prefs.p(context).edit()
            .remove(KEY_ACTIVE_RUNTIME_RULE)
            .remove(KEY_RUNTIME_SNAPSHOT_AT)
            .apply()
    }

    @JvmStatic
    fun runtimeSnapshotStartedAt(context: Context): Long =
        Prefs.p(context).getLong(KEY_RUNTIME_SNAPSHOT_AT, 0L)

    @JvmStatic
    fun isRuleRuntimeActive(context: Context, ruleId: String): Boolean =
        RuleRuntimeStore.state(context, ruleId) != RuleRuntimeStore.STATE_LOCKED

    @JvmStatic
    fun ruleMetricKey(ruleId: String, base: String): String =
        "rule:$ruleId:$base"

    @JvmStatic
    fun dailyUsageRaw(context: Context, ruleId: String): Long {
        ensureRuleDay(context, ruleId)
        return Prefs.p(context).getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)
    }

    @JvmStatic
    fun dailySessionsRaw(context: Context, ruleId: String): Int {
        ensureRuleDay(context, ruleId)
        return Prefs.p(context).getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)
    }

    @JvmStatic
    fun storedEscalationLevel(context: Context, ruleId: String): Int =
        Prefs.p(context).getInt(ruleMetricKey(ruleId, "escalation_level"), 0)

    private fun ensureRuleDay(context: Context, ruleId: String) {
        val cal = currentBudgetCalendar()
        val key = budgetDayKey(cal)
        val dayKey = ruleMetricKey(ruleId, "daily_key")
        val prefs = Prefs.p(context)
        val stored = prefs.getString(dayKey, "") ?: ""
        if (stored != key) {
            if (stored.isNotBlank()) {
                archiveDay(
                    context,
                    ruleId,
                    stored,
                    prefs.getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L),
                    prefs.getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)
                )
            }
            prefs.edit()
                .putString(dayKey, key)
                .putLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)
                .putInt(ruleMetricKey(ruleId, "daily_sessions"), 0)
                .apply()
        }
    }

    @JvmStatic
    fun archiveDay(context: Context, ruleId: String, dayKey: String, usageMs: Long, sessions: Int) {
        if (ruleId.isBlank() || dayKey.isBlank()) return
        Prefs.p(context).edit()
            .putLong(ruleMetricKey(ruleId, "history:$dayKey:usage_ms"), usageMs)
            .putInt(ruleMetricKey(ruleId, "history:$dayKey:sessions"), sessions)
            .putBoolean(ruleMetricKey(ruleId, "history:$dayKey:present"), true)
            .apply()
    }

    @JvmStatic
    fun weekRecords(context: Context, ruleId: String): List<DailyRecord> =
        historyRecords(context, ruleId, 7)

    @JvmStatic
    fun historyRecords(context: Context, ruleId: String, days: Int): List<DailyRecord> {
        ensureRuleDay(context, ruleId)
        val count = days.coerceIn(1, 365)
        val prefs = Prefs.p(context)
        val today = currentBudgetCalendar()
        val todayKey = budgetDayKey(today)

        return (count - 1 downTo 0).map { offset ->
            val cal = today.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val key = budgetDayKey(cal)
            val label = if (offset == 0) "今日" else "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"

            if (key == todayKey) {
                val usage = prefs.getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)
                val sessions = prefs.getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)
                DailyRecord(key, label, usage, sessions, usage > 0L || sessions > 0)
            } else {
                val usageKey = ruleMetricKey(ruleId, "history:$key:usage_ms")
                val sessionsKey = ruleMetricKey(ruleId, "history:$key:sessions")
                val presentKey = ruleMetricKey(ruleId, "history:$key:present")
                DailyRecord(
                    key,
                    label,
                    prefs.getLong(usageKey, 0L),
                    prefs.getInt(sessionsKey, 0),
                    prefs.getBoolean(presentKey, false)
                )
            }
        }
    }

    private fun currentBudgetCalendar(): Calendar =
        Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) < 4) add(Calendar.DAY_OF_YEAR, -1)
        }

    private fun budgetDayKey(cal: Calendar): String =
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"

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
