package dev.besan.browserbrake;

public final class RuntimeMath {
    private RuntimeMath() {}

    public static long liveRemaining(long storedRemainingMs, long foregroundSinceMs, long nowMs) {
        long remaining = Math.max(0L, storedRemainingMs);
        if (foregroundSinceMs > 0L && nowMs > foregroundSinceMs) {
            remaining -= (nowMs - foregroundSinceMs);
        }
        return Math.max(0L, remaining);
    }

    public static long consumedForeground(long storedRemainingMs, long foregroundSinceMs, long nowMs) {
        if (foregroundSinceMs <= 0L || nowMs <= foregroundSinceMs) return 0L;
        long elapsed = nowMs - foregroundSinceMs;
        return Math.min(Math.max(0L, storedRemainingMs), elapsed);
    }

    public static long recoveryDeadline(long lastUseEndMs, long recoveryDurationMs, long nowMs) {
        if (lastUseEndMs <= 0L || recoveryDurationMs <= 0L) return 0L;
        long deadline = lastUseEndMs + recoveryDurationMs;
        return deadline > nowMs ? deadline : 0L;
    }

    public static long observedCheckpointEnd(long foregroundSinceMs, long checkpointMs, long nowMs) {
        if (foregroundSinceMs <= 0L) return 0L;
        long bounded = Math.min(Math.max(checkpointMs, foregroundSinceMs), nowMs);
        return Math.max(foregroundSinceMs, bounded);
    }

    public static long usageBelongingToCurrentBudgetDay(long foregroundSinceMs, long nowMs, long budgetDayStartMs) {
        if (foregroundSinceMs <= 0L || nowMs <= foregroundSinceMs) return 0L;
        long chargeFrom = Math.max(foregroundSinceMs, budgetDayStartMs);
        return Math.max(0L, nowMs - chargeFrom);
    }
}
