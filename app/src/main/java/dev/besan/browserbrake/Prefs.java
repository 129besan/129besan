package dev.besan.browserbrake;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public final class Prefs {
    private static final String FILE = "browser_brake";

    public static final String STATE_LOCKED = "LOCKED";
    public static final String STATE_CHALLENGING = "CHALLENGING";
    public static final String STATE_READY = "READY";
    public static final String STATE_SESSION = "SESSION";
    public static final String STATE_RECOVERY = "RECOVERY";

    private Prefs() {}

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isLockEnabled(Context c) { return p(c).getBoolean("lock_enabled", false); }
    public static void setLockEnabled(Context c, boolean value) {
        p(c).edit().putBoolean("lock_enabled", value).apply();
        if (!value) clearTransientState(c);
    }

    public static String state(Context c) { return p(c).getString("runtime_state", STATE_LOCKED); }
    public static void setState(Context c, String s) { p(c).edit().putString("runtime_state", s).apply(); }

    public static String pendingTarget(Context c) { return p(c).getString("pending_target_package", ""); }
    public static void setPendingTarget(Context c, String pkg) { p(c).edit().putString("pending_target_package", pkg == null ? "" : pkg).apply(); }

    public static long challengeStartedAt(Context c) { return p(c).getLong("challenge_started_at", 0L); }
    public static long challengeWaitDeadline(Context c) { return p(c).getLong("challenge_wait_deadline", 0L); }
    public static long challengePhoneDeadline(Context c) { return p(c).getLong("challenge_phone_deadline", 0L); }
    public static float challengeWalkBaseline(Context c) { return p(c).getFloat("challenge_walk_baseline", -1f); }
    public static int challengeRequiredSteps(Context c) { return p(c).getInt("challenge_required_steps", 0); }
    public static boolean challengeOverLimit(Context c) { return p(c).getBoolean("challenge_over_limit", false); }
    public static double challengeMultiplier(Context c) { return p(c).getFloat("challenge_multiplier", 1f); }

    public static void startChallenge(Context c, String targetPkg, float stepBaseline) {
        ensureDailyReset(c);
        recordTargetAttempt(c);
        long now = System.currentTimeMillis();
        boolean over = isOverDailyLimit(c);
        int level = effectiveEscalationLevel(c, now);
        double multiplier = RuleConfig.escalationMultiplier(c, level);
        if (over) multiplier *= RuleConfig.overLimitMultiplier(c);

        long wait = effectiveTime(RuleConfig.waitMs(c), multiplier, over, c);
        long phone = effectiveTime(RuleConfig.phoneBreakMs(c), multiplier, over, c);
        int steps = (int) Math.ceil(RuleConfig.walkSteps(c) * multiplier);
        steps = Math.min(steps, 3000);

        SharedPreferences.Editor e = p(c).edit()
                .putString("runtime_state", STATE_CHALLENGING)
                .putString("pending_target_package", targetPkg == null ? "" : targetPkg)
                .putLong("challenge_started_at", now)
                .putLong("challenge_wait_deadline", RuleConfig.challengeWait(c) ? now + wait : 0L)
                .putLong("challenge_phone_deadline", RuleConfig.challengePhoneBreak(c) ? now + phone : 0L)
                .putInt("challenge_required_steps", RuleConfig.challengeWalk(c) ? steps : 0)
                .putFloat("challenge_walk_baseline", stepBaseline)
                .putBoolean("challenge_over_limit", over)
                .putFloat("challenge_multiplier", (float) multiplier)
                .putLong("ready_since", 0L)
                .putLong("ready_deadline", 0L);
        e.apply();
    }

    private static long effectiveTime(long base, double multiplier, boolean over, Context c) {
        if (base <= 0L) return 0L;
        long value = (long) Math.ceil(base * multiplier);
        if (over) {
            value = Math.max(value, RuleConfig.overLimitMinTimeMs(c));
            value = Math.min(value, RuleConfig.overLimitMaxTimeMs(c));
        } else {
            value = Math.min(value, 60L * 60_000L);
        }
        return value;
    }

    public static void resetPhoneBreakDeadline(Context c) {
        if (!STATE_CHALLENGING.equals(state(c)) || !RuleConfig.challengePhoneBreak(c)) return;
        long now = System.currentTimeMillis();
        boolean over = challengeOverLimit(c);
        long base = RuleConfig.phoneBreakMs(c);
        long duration = effectiveTime(base, challengeMultiplier(c), over, c);
        p(c).edit().putLong("challenge_phone_deadline", now + duration).apply();
    }

    public static void updateWalkBaselineIfNeeded(Context c, float totalSteps) {
        if (!STATE_CHALLENGING.equals(state(c)) || !RuleConfig.challengeWalk(c)) return;
        if (challengeWalkBaseline(c) < 0f) {
            p(c).edit().putFloat("challenge_walk_baseline", totalSteps).apply();
        }
    }

    public static int walkedSteps(Context c, float totalSteps) {
        float baseline = challengeWalkBaseline(c);
        if (baseline < 0f) return 0;
        return Math.max(0, (int) (totalSteps - baseline));
    }

    public static void markReady(Context c) {
        long now = System.currentTimeMillis();
        long timeout = RuleConfig.readyTimeoutMs(c);
        p(c).edit()
                .putString("runtime_state", STATE_READY)
                .putLong("ready_since", now)
                .putLong("ready_deadline", timeout > 0 ? now + timeout : 0L)
                .apply();
    }

    public static long readyDeadline(Context c) { return p(c).getLong("ready_deadline", 0L); }

    public static void declineReady(Context c) {
        p(c).edit()
                .putString("runtime_state", STATE_LOCKED)
                .putString("pending_target_package", "")
                .putLong("ready_since", 0L)
                .putLong("ready_deadline", 0L)
                .apply();
    }

    public static void startSession(Context c, long selectedUsageMs) {
        ensureDailyReset(c);
        long now = System.currentTimeMillis();
        boolean over = challengeOverLimit(c) || isOverDailyLimit(c);
        long usage = Math.max(1_000L, selectedUsageMs);
        if (over) {
            usage = Math.min(usage, RuleConfig.overLimitSessionMs(c));
        } else {
            long dailyRemaining = dailyUsageRemainingMs(c);
            if (dailyRemaining >= 0L) usage = Math.min(usage, dailyRemaining);
        }

        int currentLevel = effectiveEscalationLevel(c, now);
        int nextLevel = Math.min(4, currentLevel + 1);

        p(c).edit()
                .putString("runtime_state", STATE_SESSION)
                .putLong("session_usage_remaining_ms", usage)
                .putLong("session_wall_deadline", now + RuleConfig.sessionWindowMs(c))
                .putLong("session_foreground_since", 0L)
                .putLong("session_last_use_end", 0L)
                .putBoolean("session_over_limit", over)
                .putInt("daily_sessions", dailySessions(c) + 1)
                .putInt("escalation_level", nextLevel)
                .putLong("last_target_attempt", now)
                .apply();
    }

    public static long sessionUsageRemainingMs(Context c) { return p(c).getLong("session_usage_remaining_ms", 0L); }
    public static long sessionWallDeadline(Context c) { return p(c).getLong("session_wall_deadline", 0L); }
    public static long sessionForegroundSince(Context c) { return p(c).getLong("session_foreground_since", 0L); }
    public static long sessionLastUseEnd(Context c) { return p(c).getLong("session_last_use_end", 0L); }
    public static boolean sessionOverLimit(Context c) { return p(c).getBoolean("session_over_limit", false); }

    public static void sessionForegroundEnter(Context c) {
        if (!STATE_SESSION.equals(state(c))) return;
        if (sessionForegroundSince(c) == 0L) {
            long now = System.currentTimeMillis();
            int decayedLevel = effectiveEscalationLevel(c, now);
            p(c).edit()
                    .putLong("session_foreground_since", now)
                    .putInt("escalation_level", decayedLevel)
                    .putLong("last_target_attempt", now)
                    .apply();
        }
    }

    public static void suspendForegroundAccounting(Context c) {
        if (!STATE_SESSION.equals(state(c))) return;
        p(c).edit().putLong("session_foreground_since", 0L).apply();
    }

    public static void sessionForegroundLeave(Context c) {
        if (!STATE_SESSION.equals(state(c))) return;
        long since = sessionForegroundSince(c);
        if (since <= 0L) return;
        long now = System.currentTimeMillis();
        long elapsedForeground = Math.max(0L, now - since);
        long storedRemaining = sessionUsageRemainingMs(c);
        long consumed = Math.min(elapsedForeground, storedRemaining);
        long remaining = Math.max(0L, storedRemaining - consumed);

        long budgetStart = currentBudgetDayStartMillis(now);
        long overlapToday = RuntimeMath.usageBelongingToCurrentBudgetDay(since, now, budgetStart);
        ensureDailyReset(c);
        long charged = Math.min(consumed, overlapToday);

        p(c).edit()
                .putLong("session_usage_remaining_ms", remaining)
                .putLong("session_foreground_since", 0L)
                .putLong("session_last_use_end", now)
                .putLong("daily_usage_ms", dailyUsageMs(c) + charged)
                .putLong("last_target_attempt", now)
                .apply();
    }

    public static long liveSessionUsageRemainingMs(Context c) {
        return RuntimeMath.liveRemaining(
                sessionUsageRemainingMs(c),
                sessionForegroundSince(c),
                System.currentTimeMillis());
    }

    public static void finishSession(Context c) {
        sessionForegroundLeave(c);
        long now = System.currentTimeMillis();
        long recoveryDeadline = RuntimeMath.recoveryDeadline(
                sessionLastUseEnd(c),
                RuleConfig.recoveryMs(c),
                now);
        boolean recovering = recoveryDeadline > 0L;

        p(c).edit()
                .putString("runtime_state", recovering ? STATE_RECOVERY : STATE_LOCKED)
                .putLong("recovery_deadline", recoveryDeadline)
                .putLong("session_usage_remaining_ms", 0L)
                .putLong("session_wall_deadline", 0L)
                .putLong("session_foreground_since", 0L)
                .putBoolean("session_over_limit", false)
                .putString("pending_target_package", "")
                .apply();
    }

    public static long recoveryDeadline(Context c) { return p(c).getLong("recovery_deadline", 0L); }
    public static void finishRecovery(Context c) {
        p(c).edit().putString("runtime_state", STATE_LOCKED).putLong("recovery_deadline", 0L).apply();
    }

    public static void clearChallenge(Context c) {
        p(c).edit()
                .putLong("challenge_started_at", 0L)
                .putLong("challenge_wait_deadline", 0L)
                .putLong("challenge_phone_deadline", 0L)
                .putInt("challenge_required_steps", 0)
                .putFloat("challenge_walk_baseline", -1f)
                .putBoolean("challenge_over_limit", false)
                .putFloat("challenge_multiplier", 1f)
                .apply();
    }

    public static void clearTransientState(Context c) {
        sessionForegroundLeave(c);
        p(c).edit()
                .putString("runtime_state", STATE_LOCKED)
                .putString("pending_target_package", "")
                .putLong("challenge_started_at", 0L)
                .putLong("challenge_wait_deadline", 0L)
                .putLong("challenge_phone_deadline", 0L)
                .putInt("challenge_required_steps", 0)
                .putFloat("challenge_walk_baseline", -1f)
                .putBoolean("challenge_over_limit", false)
                .putFloat("challenge_multiplier", 1f)
                .putLong("ready_since", 0L)
                .putLong("ready_deadline", 0L)
                .putLong("session_usage_remaining_ms", 0L)
                .putLong("session_wall_deadline", 0L)
                .putLong("session_foreground_since", 0L)
                .putLong("session_last_use_end", 0L)
                .putBoolean("session_over_limit", false)
                .putLong("recovery_deadline", 0L)
                .apply();
    }

    public static void recordTargetAttempt(Context c) {
        long now = System.currentTimeMillis();
        int level = effectiveEscalationLevel(c, now);
        p(c).edit().putInt("escalation_level", level).putLong("last_target_attempt", now).apply();
    }

    public static int effectiveEscalationLevel(Context c, long now) {
        int level = p(c).getInt("escalation_level", 0);
        if (STATE_SESSION.equals(state(c)) && sessionForegroundSince(c) > 0L) {
            return Math.max(0, level);
        }
        long last = p(c).getLong("last_target_attempt", 0L);
        long decay = RuleConfig.escalationDecayMs(c);
        if (level <= 0 || last <= 0L || decay <= 0L) return Math.max(0, level);
        long quiet = Math.max(0L, now - last);
        int steps = (int) (quiet / decay);
        return Math.max(0, level - steps);
    }

    public static int escalationLevel(Context c) { return effectiveEscalationLevel(c, System.currentTimeMillis()); }

    public static void ensureDailyReset(Context c) {
        String key = currentBudgetDayKey();
        String stored = p(c).getString("daily_key", "");
        if (!key.equals(stored)) {
            p(c).edit()
                    .putString("daily_key", key)
                    .putLong("daily_usage_ms", 0L)
                    .putInt("daily_sessions", 0)
                    .apply();
        }
    }

    private static String currentBudgetDayKey() {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) cal.add(Calendar.DAY_OF_YEAR, -1);
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
    }

    private static long currentBudgetDayStartMillis(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) cal.add(Calendar.DAY_OF_YEAR, -1);
        cal.set(Calendar.HOUR_OF_DAY, 4);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long dailyUsageMs(Context c) { ensureDailyReset(c); return p(c).getLong("daily_usage_ms", 0L); }
    public static int dailySessions(Context c) { ensureDailyReset(c); return p(c).getInt("daily_sessions", 0); }

    public static long dailyUsageRemainingMs(Context c) {
        long limit = RuleConfig.dailyUsageLimitMs(c);
        if (limit < 0L) return -1L;
        return Math.max(0L, limit - dailyUsageMs(c));
    }

    public static boolean isOverDailyLimit(Context c) {
        ensureDailyReset(c);
        long timeLimit = RuleConfig.dailyUsageLimitMs(c);
        int sessionLimit = RuleConfig.dailySessionLimit(c);
        boolean timeOver = timeLimit >= 0L && dailyUsageMs(c) >= timeLimit;
        boolean sessionOver = sessionLimit >= 0 && dailySessions(c) >= sessionLimit;
        return timeOver || sessionOver;
    }
}
