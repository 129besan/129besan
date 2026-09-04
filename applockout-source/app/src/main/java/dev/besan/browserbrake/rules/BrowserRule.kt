package dev.besan.browserbrake.rules

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BrowserRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "新しい制限",
    val enabled: Boolean = true,
    val pausedUntilMs: Long = 0L,
    val fullLock: Boolean = false,
    val browsers: Boolean = true,
    val sns: Boolean = false,
    val customPackages: Set<String> = emptySet(),
    val allPlaces: Boolean = true,
    val placeIds: Set<String> = emptySet(),
    val challengeWait: Boolean = false,
    val challengePhoneBreak: Boolean = true,
    val challengeWalk: Boolean = false,
    val challengeAll: Boolean = true,
    val waitMs: Long = 30_000L,
    val phoneBreakMs: Long = 3 * 60_000L,
    val walkSteps: Int = 100,
    val readyTimeoutMs: Long = 0L,
    val askSessionDuration: Boolean = true,
    val defaultSessionUsageMs: Long = 10 * 60_000L,
    val sessionWindowMs: Long = 30 * 60_000L,
    val dailyUsageLimitMs: Long = 60 * 60_000L,
    val dailySessionLimit: Int = 5,
    val recoveryMs: Long = 5 * 60_000L,
    val escalationMode: String = "standard"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("pausedUntilMs", pausedUntilMs)
        put("fullLock", fullLock)
        put("browsers", browsers)
        put("sns", sns)
        put("customPackages", customPackages.toJsonArray())
        put("allPlaces", allPlaces)
        put("placeIds", placeIds.toJsonArray())
        put("challengeWait", challengeWait)
        put("challengePhoneBreak", challengePhoneBreak)
        put("challengeWalk", challengeWalk)
        put("challengeAll", challengeAll)
        put("waitMs", waitMs)
        put("phoneBreakMs", phoneBreakMs)
        put("walkSteps", walkSteps)
        put("readyTimeoutMs", readyTimeoutMs)
        put("askSessionDuration", askSessionDuration)
        put("defaultSessionUsageMs", defaultSessionUsageMs)
        put("sessionWindowMs", sessionWindowMs)
        put("dailyUsageLimitMs", dailyUsageLimitMs)
        put("dailySessionLimit", dailySessionLimit)
        put("recoveryMs", recoveryMs)
        put("escalationMode", escalationMode)
    }

    companion object {
        fun fromJson(o: JSONObject): BrowserRule = BrowserRule(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "制限"),
            enabled = o.optBoolean("enabled", true),
            pausedUntilMs = o.optLong("pausedUntilMs", 0L),
            fullLock = o.optBoolean("fullLock", false),
            browsers = o.optBoolean("browsers", true),
            sns = o.optBoolean("sns", false),
            customPackages = o.optJSONArray("customPackages").toStringSet(),
            allPlaces = o.optBoolean("allPlaces", true),
            placeIds = o.optJSONArray("placeIds").toStringSet(),
            challengeWait = o.optBoolean("challengeWait", false),
            challengePhoneBreak = o.optBoolean("challengePhoneBreak", true),
            challengeWalk = o.optBoolean("challengeWalk", false),
            challengeAll = o.optBoolean("challengeAll", true),
            waitMs = o.optLong("waitMs", 30_000L),
            phoneBreakMs = o.optLong("phoneBreakMs", 3 * 60_000L),
            walkSteps = o.optInt("walkSteps", 100),
            readyTimeoutMs = o.optLong("readyTimeoutMs", 0L),
            askSessionDuration = o.optBoolean("askSessionDuration", true),
            defaultSessionUsageMs = o.optLong("defaultSessionUsageMs", 10 * 60_000L),
            sessionWindowMs = o.optLong("sessionWindowMs", 30 * 60_000L),
            dailyUsageLimitMs = o.optLong("dailyUsageLimitMs", 60 * 60_000L),
            dailySessionLimit = o.optInt("dailySessionLimit", 5),
            recoveryMs = o.optLong("recoveryMs", 5 * 60_000L),
            escalationMode = o.optString("escalationMode", "standard")
        )
    }
}

private fun Set<String>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach(array::put) }

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
