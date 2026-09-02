package dev.besan.browserbrake;

import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RuleConfig {
    public static final String ESC_OFF = "off";
    public static final String ESC_STANDARD = "standard";
    public static final String ESC_STRONG = "strong";

    private RuleConfig() {}

    public static String ruleName(Context c) {
        return Prefs.p(c).getString("rule_name", "ブラウザ");
    }

    public static void setRuleName(Context c, String value) {
        Prefs.p(c).edit().putString("rule_name", value == null || value.isBlank() ? "ブラウザ" : value.trim()).apply();
    }

    public static boolean includeBrowsers(Context c) {
        return Prefs.p(c).getBoolean("include_browsers", true);
    }

    public static void setIncludeBrowsers(Context c, boolean value) {
        Prefs.p(c).edit().putBoolean("include_browsers", value).apply();
    }

    public static Set<String> customPackages(Context c) {
        return new HashSet<>(Prefs.p(c).getStringSet("custom_packages", new HashSet<>()));
    }

    public static void setCustomPackages(Context c, Set<String> packages) {
        Prefs.p(c).edit().putStringSet("custom_packages", new HashSet<>(packages)).apply();
    }

    public static boolean challengeWait(Context c) {
        return Prefs.p(c).getBoolean("challenge_wait_enabled", false);
    }
    public static void setChallengeWait(Context c, boolean v) { Prefs.p(c).edit().putBoolean("challenge_wait_enabled", v).apply(); }

    public static boolean challengePhoneBreak(Context c) {
        return Prefs.p(c).getBoolean("challenge_phone_enabled", true);
    }
    public static void setChallengePhoneBreak(Context c, boolean v) { Prefs.p(c).edit().putBoolean("challenge_phone_enabled", v).apply(); }

    public static boolean challengeWalk(Context c) {
        return Prefs.p(c).getBoolean("challenge_walk_enabled", false);
    }
    public static void setChallengeWalk(Context c, boolean v) { Prefs.p(c).edit().putBoolean("challenge_walk_enabled", v).apply(); }

    public static boolean challengeAll(Context c) {
        return Prefs.p(c).getBoolean("challenge_all", true);
    }
    public static void setChallengeAll(Context c, boolean v) { Prefs.p(c).edit().putBoolean("challenge_all", v).apply(); }

    public static long waitMs(Context c) { return Prefs.p(c).getLong("wait_ms", 60_000L); }
    public static void setWaitMs(Context c, long v) { Prefs.p(c).edit().putLong("wait_ms", Math.max(0L, v)).apply(); }

    public static long phoneBreakMs(Context c) { return Prefs.p(c).getLong("phone_break_ms", 3L * 60_000L); }
    public static void setPhoneBreakMs(Context c, long v) { Prefs.p(c).edit().putLong("phone_break_ms", Math.max(0L, v)).apply(); }

    public static int walkSteps(Context c) { return Prefs.p(c).getInt("walk_steps", 100); }
    public static void setWalkSteps(Context c, int v) { Prefs.p(c).edit().putInt("walk_steps", Math.max(1, v)).apply(); }

    public static boolean askSessionDuration(Context c) { return Prefs.p(c).getBoolean("ask_session_duration", true); }
    public static void setAskSessionDuration(Context c, boolean v) { Prefs.p(c).edit().putBoolean("ask_session_duration", v).apply(); }

    public static long defaultSessionUsageMs(Context c) { return Prefs.p(c).getLong("default_session_usage_ms", 10L * 60_000L); }
    public static void setDefaultSessionUsageMs(Context c, long v) { Prefs.p(c).edit().putLong("default_session_usage_ms", Math.max(60_000L, v)).apply(); }

    public static long sessionWindowMs(Context c) { return Prefs.p(c).getLong("session_window_ms", 30L * 60_000L); }
    public static void setSessionWindowMs(Context c, long v) { Prefs.p(c).edit().putLong("session_window_ms", Math.max(60_000L, v)).apply(); }

    public static long recoveryMs(Context c) { return Prefs.p(c).getLong("recovery_ms", 5L * 60_000L); }
    public static void setRecoveryMs(Context c, long v) { Prefs.p(c).edit().putLong("recovery_ms", Math.max(0L, v)).apply(); }

    public static long dailyUsageLimitMs(Context c) { return Prefs.p(c).getLong("daily_usage_limit_ms", 60L * 60_000L); }
    public static void setDailyUsageLimitMs(Context c, long v) { Prefs.p(c).edit().putLong("daily_usage_limit_ms", v).apply(); }

    public static int dailySessionLimit(Context c) { return Prefs.p(c).getInt("daily_session_limit", 5); }
    public static void setDailySessionLimit(Context c, int v) { Prefs.p(c).edit().putInt("daily_session_limit", v).apply(); }

    public static String escalationMode(Context c) { return Prefs.p(c).getString("escalation_mode", ESC_STANDARD); }
    public static void setEscalationMode(Context c, String v) {
        if (!Arrays.asList(ESC_OFF, ESC_STANDARD, ESC_STRONG).contains(v)) v = ESC_STANDARD;
        Prefs.p(c).edit().putString("escalation_mode", v).apply();
    }

    public static long escalationDecayMs(Context c) {
        String m = escalationMode(c);
        if (ESC_STRONG.equals(m)) return 3L * 60L * 60_000L;
        if (ESC_OFF.equals(m)) return 0L;
        return 90L * 60_000L;
    }

    public static double escalationMultiplier(Context c, int level) {
        if (level <= 0 || ESC_OFF.equals(escalationMode(c))) return 1.0;
        int i = Math.min(level, 4);
        if (ESC_STRONG.equals(escalationMode(c))) {
            double[] values = {1.0, 2.0, 3.5, 5.0, 7.0};
            return values[i];
        }
        double[] values = {1.0, 1.5, 2.5, 3.5, 5.0};
        return values[i];
    }

    public static double overLimitMultiplier(Context c) { return Prefs.p(c).getFloat("over_limit_multiplier", 5f); }
    public static void setOverLimitMultiplier(Context c, float v) { Prefs.p(c).edit().putFloat("over_limit_multiplier", Math.max(1f, v)).apply(); }

    public static long overLimitMinTimeMs(Context c) { return Prefs.p(c).getLong("over_limit_min_time_ms", 10L * 60_000L); }
    public static long overLimitMaxTimeMs(Context c) { return Prefs.p(c).getLong("over_limit_max_time_ms", 30L * 60_000L); }
    public static long overLimitSessionMs(Context c) { return Prefs.p(c).getLong("over_limit_session_ms", 3L * 60_000L); }

    public static long readyTimeoutMs(Context c) { return Prefs.p(c).getLong("ready_timeout_ms", 0L); }
    public static void setReadyTimeoutMs(Context c, long v) { Prefs.p(c).edit().putLong("ready_timeout_ms", Math.max(0L, v)).apply(); }
}
