package dev.besan.browserbrake;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuntimeMathTest {
    @Test
    public void liveRemainingCountsOnlyForegroundInterval() {
        assertEquals(7_000L, RuntimeMath.liveRemaining(10_000L, 5_000L, 8_000L));
        assertEquals(10_000L, RuntimeMath.liveRemaining(10_000L, 0L, 8_000L));
        assertEquals(0L, RuntimeMath.liveRemaining(2_000L, 5_000L, 8_000L));
    }

    @Test
    public void recoveryIsAnchoredToLastActualUse() {
        assertEquals(15_000L, RuntimeMath.recoveryDeadline(10_000L, 5_000L, 12_000L));
        assertEquals(0L, RuntimeMath.recoveryDeadline(10_000L, 5_000L, 20_000L));
        assertEquals(0L, RuntimeMath.recoveryDeadline(0L, 5_000L, 20_000L));
    }

    @Test
    public void dailyUsageOnlyChargesCurrentBudgetDayAfterReset() {
        assertEquals(2_000L,
                RuntimeMath.usageBelongingToCurrentBudgetDay(8_000L, 12_000L, 10_000L));
        assertEquals(4_000L,
                RuntimeMath.usageBelongingToCurrentBudgetDay(8_000L, 12_000L, 5_000L));
    }
}
