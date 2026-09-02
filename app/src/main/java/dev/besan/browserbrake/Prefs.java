package dev.besan.browserbrake;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

public final class Prefs {
    private static final String FILE = "browser_brake";
    public static final long WAIT_MS = 5L * 60L * 1000L;
    public static final long UNLOCK_MS = 15L * 60L * 1000L;
    public static final float ENTER_RADIUS_M = 200f;
    public static final float EXIT_RADIUS_M = 350f;

    private Prefs() {}

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isLockEnabled(Context c) {
        return p(c).getBoolean("lock_enabled", false);
    }

    public static void setLockEnabled(Context c, boolean value) {
        p(c).edit().putBoolean("lock_enabled", value).apply();
    }

    public static boolean isHomeSet(Context c) {
        return p(c).getBoolean("home_set", false);
    }

    public static void setHome(Context c, double lat, double lon) {
        p(c).edit()
                .putBoolean("home_set", true)
                .putLong("home_lat", Double.doubleToRawLongBits(lat))
                .putLong("home_lon", Double.doubleToRawLongBits(lon))
                .putBoolean("last_home_state", true)
                .apply();
    }

    public static double homeLat(Context c) {
        return Double.longBitsToDouble(p(c).getLong("home_lat", Double.doubleToRawLongBits(0.0)));
    }

    public static double homeLon(Context c) {
        return Double.longBitsToDouble(p(c).getLong("home_lon", Double.doubleToRawLongBits(0.0)));
    }

    public static boolean lastHomeState(Context c) {
        return p(c).getBoolean("last_home_state", false);
    }

    public static boolean updateHomeState(Context c, Location loc) {
        if (loc == null || !isHomeSet(c)) return lastHomeState(c);
        float[] result = new float[1];
        Location.distanceBetween(homeLat(c), homeLon(c), loc.getLatitude(), loc.getLongitude(), result);
        boolean previous = lastHomeState(c);
        boolean next = previous;
        if (result[0] <= ENTER_RADIUS_M) next = true;
        else if (result[0] >= EXIT_RADIUS_M) next = false;
        p(c).edit()
                .putBoolean("last_home_state", next)
                .putFloat("last_distance_m", result[0])
                .putLong("last_location_time", System.currentTimeMillis())
                .apply();
        return next;
    }

    public static float lastDistance(Context c) {
        return p(c).getFloat("last_distance_m", -1f);
    }

    public static boolean isChallengeActive(Context c) {
        return p(c).getBoolean("challenge_active", false);
    }

    public static void startChallenge(Context c) {
        long now = System.currentTimeMillis();
        p(c).edit()
                .putBoolean("challenge_active", true)
                .putLong("last_touch_at", now)
                .putLong("challenge_deadline", now + WAIT_MS)
                .apply();
    }

    public static void resetChallengeFromTouch(Context c) {
        long now = System.currentTimeMillis();
        p(c).edit()
                .putBoolean("challenge_active", true)
                .putLong("last_touch_at", now)
                .putLong("challenge_deadline", now + WAIT_MS)
                .apply();
    }

    public static long lastTouchAt(Context c) {
        return p(c).getLong("last_touch_at", 0L);
    }

    public static long challengeDeadline(Context c) {
        return p(c).getLong("challenge_deadline", 0L);
    }

    public static void cancelChallenge(Context c) {
        p(c).edit()
                .putBoolean("challenge_active", false)
                .putLong("last_touch_at", 0L)
                .putLong("challenge_deadline", 0L)
                .apply();
    }

    public static void grantTemporaryUnlock(Context c) {
        long until = System.currentTimeMillis() + UNLOCK_MS;
        p(c).edit()
                .putBoolean("challenge_active", false)
                .putLong("last_touch_at", 0L)
                .putLong("challenge_deadline", 0L)
                .putLong("unlock_until", until)
                .apply();
    }

    public static long unlockUntil(Context c) {
        return p(c).getLong("unlock_until", 0L);
    }

    public static boolean isTemporarilyUnlocked(Context c) {
        return System.currentTimeMillis() < unlockUntil(c);
    }

    public static void clearUnlock(Context c) {
        p(c).edit().putLong("unlock_until", 0L).apply();
    }

    public static void clearTransientState(Context c) {
        p(c).edit()
                .putBoolean("challenge_active", false)
                .putLong("last_touch_at", 0L)
                .putLong("challenge_deadline", 0L)
                .putLong("unlock_until", 0L)
                .apply();
    }
}
