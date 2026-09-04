package dev.besan.browserbrake

import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleStrengthTest {
    @Test
    fun shorteningPhoneBreakIsWeakening() {
        val before = BrowserRule(
            browsers = false,
            customPackages = setOf("example.app"),
            challengePhoneBreak = true,
            phoneBreakMs = 3 * 60_000L
        )
        val after = before.copy(phoneBreakMs = 60_000L)

        assertTrue(
            RuleRepository.weakeningReasons(before, after)
                .contains("スマホ休憩を短くする")
        )
    }

    @Test
    fun tighteningDailyLimitIsNotWeakening() {
        val before = BrowserRule(dailyUsageLimitMs = 60 * 60_000L)
        val after = before.copy(dailyUsageLimitMs = 30 * 60_000L)

        assertTrue(RuleRepository.weakeningReasons(before, after).isEmpty())
    }

    @Test
    fun removingTargetAndDisablingChallengeAreProtected() {
        val before = BrowserRule(
            browsers = false,
            customPackages = setOf("example.one", "example.two"),
            challengeWait = true,
            challengePhoneBreak = true,
            challengeAll = true
        )
        val after = before.copy(
            customPackages = setOf("example.one"),
            challengePhoneBreak = false
        )

        val reasons = RuleRepository.weakeningReasons(before, after)
        assertTrue(reasons.contains("対象アプリを減らす"))
        assertTrue(reasons.contains("スマホ休憩を外す"))
    }
}
