package dev.besan.browserbrake.runtime

import android.content.Context
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.RuntimeMath
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object RuleRuntimeStore {
    const val STATE_LOCKED = "LOCKED"
    const val STATE_CHALLENGING = "CHALLENGING"
    const val STATE_READY = "READY"
    const val STATE_SESSION = "SESSION"
    const val STATE_RECOVERY = "RECOVERY"

    private const val ACTIVE_IDS = "v05_runtime_active_rule_ids"
    private const val MIGRATED = "v05_runtime_migrated"

    private fun key(ruleId: String, base: String) = "runtime:$ruleId:$base"

    @JvmStatic
    fun ensureMigrated(context: Context) {
        val prefs = Prefs.p(context)
        if (prefs.getBoolean(MIGRATED, false)) return

        // v0.4 had exactly one transient runtime. Do not try to reinterpret it as
        // multiple independent runtimes. Charge any live foreground use first,
        // then start v0.5 from a clean transient state while preserving durable rules
        // and per-rule daily/history metrics.
        if (Prefs.state(context) != Prefs.STATE_LOCKED) {
            Prefs.clearTransientState(context)
        }
        prefs.edit()
            .putStringSet(ACTIVE_IDS, emptySet())
            .putBoolean(MIGRATED, true)
            .apply()
    }

    @JvmStatic
    fun activeRuleIds(context: Context): Set<String> {
        ensureMigrated(context)
        return HashSet(Prefs.p(context).getStringSet(ACTIVE_IDS, emptySet()) ?: emptySet())
    }

    private fun setActive(context: Context, ruleId: String, active: Boolean) {
        val next = activeRuleIds(context).toMutableSet()
        if (active) next += ruleId else next -= ruleId
        Prefs.p(context).edit().putStringSet(ACTIVE_IDS, next).apply()
    }

    @JvmStatic
    fun state(context: Context, ruleId: String): String =
        Prefs.p(context).getString(key(ruleId, "state"), STATE_LOCKED) ?: STATE_LOCKED

    @JvmStatic
    fun pendingTarget(context: Context, ruleId: String): String =
        Prefs.p(context).getString(key(ruleId, "pending_target"), "") ?: ""

    @JvmStatic
    fun snapshot(context: Context, ruleId: String): BrowserRule? {
        val raw = Prefs.p(context).getString(key(ruleId, "snapshot"), null) ?: return null
        return runCatching { BrowserRule.fromJson(JSONObject(raw)) }.getOrNull()
    }

    @JvmStatic
    fun ruleForRuntime(context: Context, ruleId: String): BrowserRule? =
        snapshot(context, ruleId) ?: RuleRepository.getRule(context, ruleId)

    @JvmStatic
    fun startChallenge(context: Context, rule: BrowserRule, targetPkg: String, stepBaseline: Float) {
        RuleRepository.dailyUsageRaw(context, rule.id)
        RuleRepository.dailySessionsRaw(context, rule.id)

        val now = System.currentTimeMillis()
        recordTargetAttempt(context, rule.id, rule, now)
        val over = isOverDailyLimit(context, rule)
        val level = effectiveEscalationLevel(context, rule.id, rule, now)
        var multiplier = escalationMultiplier(rule.escalationMode, level)
        if (over) multiplier *= overLimitMultiplier(context)

        val wait = effectiveTime(context, rule.waitMs, multiplier, over)
        val phone = effectiveTime(context, rule.phoneBreakMs, multiplier, over)
        val steps = min(3000, ceil(rule.walkSteps * multiplier).toInt())

        Prefs.p(context).edit()
            .putString(key(rule.id, "state"), STATE_CHALLENGING)
            .putString(key(rule.id, "snapshot"), rule.toJson().toString())
            .putString(key(rule.id, "pending_target"), targetPkg)
            .putLong(key(rule.id, "challenge_started_at"), now)
            .putLong(key(rule.id, "challenge_wait_deadline"), if (rule.challengeWait) now + wait else 0L)
            .putLong(key(rule.id, "challenge_phone_deadline"), if (rule.challengePhoneBreak) now + phone else 0L)
            .putInt(key(rule.id, "challenge_required_steps"), if (rule.challengeWalk) steps else 0)
            .putFloat(key(rule.id, "challenge_walk_baseline"), stepBaseline)
            .putBoolean(key(rule.id, "challenge_over_limit"), over)
            .putFloat(key(rule.id, "challenge_multiplier"), multiplier.toFloat())
            .putLong(key(rule.id, "ready_since"), 0L)
            .putLong(key(rule.id, "ready_deadline"), 0L)
            .apply()
        setActive(context, rule.id, true)
    }

    private fun effectiveTime(context: Context, base: Long, multiplier: Double, over: Boolean): Long {
        if (base <= 0L) return 0L
        var value = ceil(base * multiplier).toLong()
        if (over) {
            value = max(value, Prefs.p(context).getLong("over_limit_min_time_ms", 10L * 60_000L))
            value = min(value, Prefs.p(context).getLong("over_limit_max_time_ms", 30L * 60_000L))
        } else {
            value = min(value, 60L * 60_000L)
        }
        return value
    }

    private fun overLimitMultiplier(context: Context): Double =
        Prefs.p(context).getFloat("over_limit_multiplier", 5f).coerceAtLeast(1f).toDouble()

    private fun overLimitSessionMs(context: Context): Long =
        Prefs.p(context).getLong("over_limit_session_ms", 3L * 60_000L)

    private fun escalationMultiplier(mode: String, level: Int): Double {
        if (level <= 0 || mode == "off") return 1.0
        val i = level.coerceIn(0, 4)
        return if (mode == "strong") {
            doubleArrayOf(1.0, 2.0, 3.5, 5.0, 7.0)[i]
        } else {
            doubleArrayOf(1.0, 1.5, 2.5, 3.5, 5.0)[i]
        }
    }

    private fun escalationDecayMs(mode: String): Long = when (mode) {
        "strong" -> 3L * 60L * 60_000L
        "off" -> 0L
        else -> 90L * 60_000L
    }

    @JvmStatic
    fun challengeWaitDeadline(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "challenge_wait_deadline"), 0L)

    @JvmStatic
    fun challengePhoneDeadline(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "challenge_phone_deadline"), 0L)

    @JvmStatic
    fun challengeRequiredSteps(context: Context, ruleId: String): Int =
        Prefs.p(context).getInt(key(ruleId, "challenge_required_steps"), 0)

    @JvmStatic
    fun challengeOverLimit(context: Context, ruleId: String): Boolean =
        Prefs.p(context).getBoolean(key(ruleId, "challenge_over_limit"), false)

    @JvmStatic
    fun resetPhoneBreakDeadline(context: Context, ruleId: String) {
        if (state(context, ruleId) != STATE_CHALLENGING) return
        val rule = ruleForRuntime(context, ruleId) ?: return
        if (!rule.challengePhoneBreak) return
        val multiplier = Prefs.p(context).getFloat(key(ruleId, "challenge_multiplier"), 1f).toDouble()
        val duration = effectiveTime(
            context,
            rule.phoneBreakMs,
            multiplier,
            challengeOverLimit(context, ruleId)
        )
        Prefs.p(context).edit()
            .putLong(key(ruleId, "challenge_phone_deadline"), System.currentTimeMillis() + duration)
            .apply()
    }

    @JvmStatic
    fun updateWalkBaselineIfNeeded(context: Context, ruleId: String, totalSteps: Float) {
        if (state(context, ruleId) != STATE_CHALLENGING) return
        val rule = ruleForRuntime(context, ruleId) ?: return
        if (!rule.challengeWalk) return
        if (Prefs.p(context).getFloat(key(ruleId, "challenge_walk_baseline"), -1f) < 0f) {
            Prefs.p(context).edit().putFloat(key(ruleId, "challenge_walk_baseline"), totalSteps).apply()
        }
    }

    @JvmStatic
    fun walkedSteps(context: Context, ruleId: String, totalSteps: Float): Int {
        val baseline = Prefs.p(context).getFloat(key(ruleId, "challenge_walk_baseline"), -1f)
        if (baseline < 0f) return 0
        return max(0, (totalSteps - baseline).toInt())
    }

    @JvmStatic
    fun markReady(context: Context, ruleId: String) {
        val rule = ruleForRuntime(context, ruleId) ?: return
        val now = System.currentTimeMillis()
        Prefs.p(context).edit()
            .putString(key(ruleId, "state"), STATE_READY)
            .putLong(key(ruleId, "ready_since"), now)
            .putLong(key(ruleId, "ready_deadline"), if (rule.readyTimeoutMs > 0L) now + rule.readyTimeoutMs else 0L)
            .putLong(key(ruleId, "challenge_started_at"), 0L)
            .putLong(key(ruleId, "challenge_wait_deadline"), 0L)
            .putLong(key(ruleId, "challenge_phone_deadline"), 0L)
            .putInt(key(ruleId, "challenge_required_steps"), 0)
            .putFloat(key(ruleId, "challenge_walk_baseline"), -1f)
            .apply()
    }

    @JvmStatic
    fun readyDeadline(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "ready_deadline"), 0L)

    @JvmStatic
    fun declineReady(context: Context, ruleId: String) = clearRuntime(context, ruleId)

    @JvmStatic
    fun startSession(context: Context, ruleId: String, selectedUsageMs: Long) {
        val rule = ruleForRuntime(context, ruleId) ?: return
        val now = System.currentTimeMillis()
        val over = challengeOverLimit(context, ruleId) || isOverDailyLimit(context, rule)
        var usage = max(1_000L, selectedUsageMs)

        if (over) {
            usage = min(usage, overLimitSessionMs(context))
        } else {
            val remaining = dailyUsageRemainingMs(context, rule)
            if (remaining >= 0L) usage = min(usage, remaining)
        }

        val currentLevel = effectiveEscalationLevel(context, ruleId, rule, now)
        val nextLevel = min(4, currentLevel + 1)
        val sessions = RuleRepository.dailySessionsRaw(context, ruleId)

        Prefs.p(context).edit()
            .putString(key(ruleId, "state"), STATE_SESSION)
            .putLong(key(ruleId, "session_usage_remaining_ms"), usage)
            .putLong(key(ruleId, "session_wall_deadline"), now + rule.sessionWindowMs)
            .putLong(key(ruleId, "session_foreground_since"), 0L)
            .putLong(key(ruleId, "session_last_use_end"), 0L)
            .putBoolean(key(ruleId, "session_over_limit"), over)
            .putInt(RuleRepository.ruleMetricKey(ruleId, "daily_sessions"), sessions + 1)
            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), nextLevel)
            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)
            .apply()
    }

    @JvmStatic
    fun sessionUsageRemainingMs(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "session_usage_remaining_ms"), 0L)

    @JvmStatic
    fun sessionWallDeadline(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "session_wall_deadline"), 0L)

    @JvmStatic
    fun sessionForegroundSince(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "session_foreground_since"), 0L)

    @JvmStatic
    fun sessionLastUseEnd(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "session_last_use_end"), 0L)

    @JvmStatic
    fun sessionOverLimit(context: Context, ruleId: String): Boolean =
        Prefs.p(context).getBoolean(key(ruleId, "session_over_limit"), false)

    @JvmStatic
    fun sessionForegroundEnter(context: Context, ruleId: String) {
        if (state(context, ruleId) != STATE_SESSION) return
        if (sessionForegroundSince(context, ruleId) != 0L) return
        val rule = ruleForRuntime(context, ruleId) ?: return
        val now = System.currentTimeMillis()
        val level = effectiveEscalationLevel(context, ruleId, rule, now)
        Prefs.p(context).edit()
            .putLong(key(ruleId, "session_foreground_since"), now)
            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), level)
            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)
            .apply()
    }

    @JvmStatic
    fun suspendForegroundAccounting(context: Context, ruleId: String) {
        if (state(context, ruleId) != STATE_SESSION) return
        Prefs.p(context).edit().putLong(key(ruleId, "session_foreground_since"), 0L).apply()
    }

    @JvmStatic
    fun sessionForegroundLeave(context: Context, ruleId: String) {
        if (state(context, ruleId) != STATE_SESSION) return
        val since = sessionForegroundSince(context, ruleId)
        if (since <= 0L) return

        val now = System.currentTimeMillis()
        val storedRemaining = sessionUsageRemainingMs(context, ruleId)
        val consumed = RuntimeMath.consumedForeground(storedRemaining, since, now)
        val remaining = max(0L, storedRemaining - consumed)
        val budgetStart = currentBudgetDayStartMillis(now)
        val overlapToday = RuntimeMath.usageBelongingToCurrentBudgetDay(since, now, budgetStart)
        RuleRepository.dailyUsageRaw(context, ruleId)
        val charged = min(consumed, overlapToday)
        val currentDaily = RuleRepository.dailyUsageRaw(context, ruleId)

        Prefs.p(context).edit()
            .putLong(key(ruleId, "session_usage_remaining_ms"), remaining)
            .putLong(key(ruleId, "session_foreground_since"), 0L)
            .putLong(key(ruleId, "session_last_use_end"), now)
            .putLong(RuleRepository.ruleMetricKey(ruleId, "daily_usage_ms"), currentDaily + charged)
            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)
            .apply()
    }

    @JvmStatic
    fun liveSessionUsageRemainingMs(context: Context, ruleId: String): Long =
        RuntimeMath.liveRemaining(
            sessionUsageRemainingMs(context, ruleId),
            sessionForegroundSince(context, ruleId),
            System.currentTimeMillis()
        )

    @JvmStatic
    fun finishSession(context: Context, ruleId: String) {
        sessionForegroundLeave(context, ruleId)
        val rule = ruleForRuntime(context, ruleId)
        if (rule == null) {
            clearRuntime(context, ruleId)
            return
        }
        val now = System.currentTimeMillis()
        val recoveryDeadline = RuntimeMath.recoveryDeadline(
            sessionLastUseEnd(context, ruleId),
            rule.recoveryMs,
            now
        )
        if (recoveryDeadline > 0L) {
            Prefs.p(context).edit()
                .putString(key(ruleId, "state"), STATE_RECOVERY)
                .putLong(key(ruleId, "recovery_deadline"), recoveryDeadline)
                .putLong(key(ruleId, "session_usage_remaining_ms"), 0L)
                .putLong(key(ruleId, "session_wall_deadline"), 0L)
                .putLong(key(ruleId, "session_foreground_since"), 0L)
                .putBoolean(key(ruleId, "session_over_limit"), false)
                .putString(key(ruleId, "pending_target"), "")
                .apply()
        } else {
            clearRuntime(context, ruleId)
        }
    }

    @JvmStatic
    fun recoveryDeadline(context: Context, ruleId: String): Long =
        Prefs.p(context).getLong(key(ruleId, "recovery_deadline"), 0L)

    @JvmStatic
    fun finishRecovery(context: Context, ruleId: String) = clearRuntime(context, ruleId)

    @JvmStatic
    fun clearRuntime(context: Context, ruleId: String) {
        sessionForegroundLeave(context, ruleId)
        val editor = Prefs.p(context).edit()
        listOf(
            "state", "snapshot", "pending_target",
            "challenge_started_at", "challenge_wait_deadline", "challenge_phone_deadline",
            "challenge_required_steps", "challenge_walk_baseline", "challenge_over_limit",
            "challenge_multiplier", "ready_since", "ready_deadline",
            "session_usage_remaining_ms", "session_wall_deadline", "session_foreground_since",
            "session_last_use_end", "session_over_limit", "recovery_deadline"
        ).forEach { editor.remove(key(ruleId, it)) }
        editor.apply()
        setActive(context, ruleId, false)
    }

    @JvmStatic
    fun clearAll(context: Context) {
        activeRuleIds(context).toList().forEach { clearRuntime(context, it) }
    }

    @JvmStatic
    fun recordTargetAttempt(context: Context, ruleId: String, rule: BrowserRule? = null, now: Long = System.currentTimeMillis()) {
        val config = rule ?: ruleForRuntime(context, ruleId) ?: RuleRepository.getRule(context, ruleId) ?: return
        val level = effectiveEscalationLevel(context, ruleId, config, now)
        Prefs.p(context).edit()
            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), level)
            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)
            .apply()
    }

    @JvmStatic
    fun effectiveEscalationLevel(context: Context, ruleId: String, rule: BrowserRule, now: Long): Int {
        val level = Prefs.p(context).getInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), 0)
        if (state(context, ruleId) == STATE_SESSION && sessionForegroundSince(context, ruleId) > 0L) {
            return max(0, level)
        }
        val last = Prefs.p(context).getLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), 0L)
        val decay = escalationDecayMs(rule.escalationMode)
        if (level <= 0 || last <= 0L || decay <= 0L) return max(0, level)
        val quiet = max(0L, now - last)
        return max(0, level - (quiet / decay).toInt())
    }

    @JvmStatic
    fun isOverDailyLimit(context: Context, rule: BrowserRule): Boolean {
        val usage = RuleRepository.dailyUsageRaw(context, rule.id)
        val sessions = RuleRepository.dailySessionsRaw(context, rule.id)
        val timeOver = rule.dailyUsageLimitMs >= 0L && usage >= rule.dailyUsageLimitMs
        val sessionOver = rule.dailySessionLimit >= 0 && sessions >= rule.dailySessionLimit
        return timeOver || sessionOver
    }

    @JvmStatic
    fun dailyUsageRemainingMs(context: Context, rule: BrowserRule): Long {
        if (rule.dailyUsageLimitMs < 0L) return -1L
        return max(0L, rule.dailyUsageLimitMs - RuleRepository.dailyUsageRaw(context, rule.id))
    }

    private fun currentBudgetDayStartMillis(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 4)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
