package dev.besan.browserbrake

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

object FlutterBridge {
    private const val CHANNEL = "dev.besan.browserbrake/app"

    fun configure(activity: FlutterActivity, engine: FlutterEngine, view: String) {
        MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getInitialView" -> result.success(initialView(activity, view))
                    "getRules" -> result.success(ruleMaps(activity))
                    "getRecords" -> result.success(recordMaps(activity))
                    "getHealth" -> result.success(health(activity))
                    "getGateStatus" -> result.success(gateStatus(activity))
                    "completeOnboarding" -> {
                        Prefs.p(activity).edit().putBoolean("flutter_onboarding_complete", true).apply()
                        result.success(null)
                    }
                    "getRule" -> {
                        val id = call.argument<String>("id").orEmpty()
                        result.success(RuleRepository.getRule(activity, id)?.let(::ruleMap))
                    }
                    "newRuleTemplate" -> result.success(ruleMap(BrowserRule(browsers = false, challengeWait = true, challengePhoneBreak = false)))
                    "saveRule" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                        val candidate = ruleFromMap(args)
                        val validationErrors = validationErrors(candidate)
                        if (validationErrors.isNotEmpty()) {
                            result.success(mapOf("saved" to false, "validationErrors" to validationErrors))
                            return@setMethodCallHandler
                        }
                        val conflicts = RuleRepository.conflicts(activity, candidate)
                        if (conflicts.isNotEmpty()) {
                            result.success(mapOf("saved" to false, "conflicts" to conflicts))
                            return@setMethodCallHandler
                        }
                        val before = RuleRepository.getRule(activity, candidate.id)
                        val reasons = before?.let { RuleRepository.weakeningReasons(it, candidate) }.orEmpty()
                        val confirmed = call.argument<Boolean>("confirmed") == true
                        if (reasons.isNotEmpty() && !confirmed) {
                            result.success(mapOf("saved" to false, "weakeningReasons" to reasons))
                        } else {
                            if (before != null && reasons.isNotEmpty()) {
                                RuleRepository.markCommitmentBreak(activity, candidate.id, "settings_weakened")
                            }
                            RuleRepository.saveRule(activity, candidate)
                            if (!candidate.enabled) {
                                RuleRuntimeStore.clearRuntime(activity, candidate.id)
                                NotificationController.cancel(activity, candidate.id)
                            }
                            BrowserBlockService.requestRuntimeSync()
                            result.success(mapOf("saved" to true, "weakeningReasons" to reasons))
                        }
                    }
                    "deleteRule" -> {
                        val id = call.argument<String>("id").orEmpty()
                        if (id.isNotBlank()) {
                            RuleRepository.markCommitmentBreak(activity, id, "delete")
                            RuleRepository.deleteRule(activity, id)
                            NotificationController.cancel(activity, id)
                        }
                        BrowserBlockService.requestRuntimeSync()
                        result.success(null)
                    }
                    "pauseRule" -> {
                        val id = call.argument<String>("id").orEmpty()
                        val durationMs = call.argument<Number>("durationMs")?.toLong() ?: 0L
                        val until = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
                        if (id.isNotBlank()) {
                            RuleRepository.pauseRule(activity, id, until)
                            if (durationMs > 0L) NotificationController.cancel(activity, id)
                        }
                        BrowserBlockService.requestRuntimeSync()
                        result.success(null)
                    }
                    "setRuleEnabled" -> {
                        val id = call.argument<String>("id").orEmpty()
                        val enabled = call.argument<Boolean>("enabled") ?: true
                        RuleRepository.setEnabled(activity, id, enabled)
                        if (!enabled) NotificationController.cancel(activity, id)
                        BrowserBlockService.requestRuntimeSync()
                        result.success(null)
                    }
                    "requestActivityRecognition" -> {
                        if (Build.VERSION.SDK_INT >= 29 &&
                            activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 4902)
                        }
                        result.success(null)
                    }
                    "openAccessibilitySettings" -> {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(null)
                    }
                    "openNotificationSettings" -> {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        activity.startActivity(intent)
                        result.success(null)
                    }
                    "openBatterySettings" -> {
                        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        result.success(null)
                    }
                    "openAppSettings" -> {
                        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${activity.packageName}")
                        })
                        result.success(null)
                    }
                    "openUnlock" -> {
                        val id = activity.intent.getStringExtra(BrakeGateActivity.EXTRA_RULE_ID).orEmpty()
                        activity.startActivity(Intent(activity, UnlockGateActivity::class.java).putExtra(UnlockGateActivity.EXTRA_RULE_ID, id))
                        activity.finish()
                        result.success(null)
                    }
                    "declineGate" -> {
                        val id = activity.intent.getStringExtra(BrakeGateActivity.EXTRA_RULE_ID).orEmpty()
                        if (id.isNotBlank() && !activity.intent.getBooleanExtra(BrakeGateActivity.EXTRA_FULL_LOCK, false)) {
                            RuleRuntimeStore.clearRuntime(activity, id)
                            NotificationController.cancel(activity, id)
                            BrowserBlockService.requestRuntimeSync()
                        }
                        goHome(activity)
                        result.success(null)
                    }
                    "startSession" -> {
                        val id = activity.intent.getStringExtra(UnlockGateActivity.EXTRA_RULE_ID).orEmpty()
                        val usageMs = call.argument<Number>("usageMs")?.toLong() ?: 10L * 60_000L
                        startSession(activity, id, usageMs)
                        result.success(null)
                    }
                    "endSession" -> {
                        val id = call.argument<String>("id").orEmpty()
                        if (id.isNotBlank() && RuleRuntimeStore.state(activity, id) == RuleRuntimeStore.STATE_SESSION) {
                            RuleRuntimeStore.finishSession(activity, id)
                            if (RuleRuntimeStore.state(activity, id) == RuleRuntimeStore.STATE_RECOVERY) {
                                NotificationController.showRecovery(activity, id)
                            } else {
                                NotificationController.cancel(activity, id)
                            }
                            BrowserBlockService.requestRuntimeSync()
                        }
                        result.success(null)
                    }
                    "declineReady" -> {
                        val id = activity.intent.getStringExtra(UnlockGateActivity.EXTRA_RULE_ID).orEmpty()
                        if (id.isNotBlank()) {
                            RuleRuntimeStore.declineReady(activity, id)
                            NotificationController.cancel(activity, id)
                            BrowserBlockService.requestRuntimeSync()
                        }
                        activity.finish()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            } catch (t: Throwable) {
                result.error("native_error", t.message, null)
            }
        }
    }

    private fun initialView(context: FlutterActivity, view: String): Map<String, Any?> {
        if (view == "brake") {
            val id = context.intent.getStringExtra(BrakeGateActivity.EXTRA_RULE_ID).orEmpty()
            return mapOf(
                "view" to "brake",
                "ruleId" to id,
                "name" to (context.intent.getStringExtra(BrakeGateActivity.EXTRA_RESTRICTION_NAME)
                    ?: RuleRepository.getRule(context, id)?.name ?: "AppLockout"),
                "fullLock" to context.intent.getBooleanExtra(BrakeGateActivity.EXTRA_FULL_LOCK, false)
            )
        }
        if (view == "unlock") {
            val id = context.intent.getStringExtra(UnlockGateActivity.EXTRA_RULE_ID).orEmpty()
            val rule = RuleRuntimeStore.ruleForRuntime(context, id) ?: RuleRepository.getRule(context, id)
            val remaining = rule?.let { RuleRuntimeStore.dailyUsageRemainingMs(context, it) } ?: -1L
            val over = rule?.let { RuleRuntimeStore.challengeOverLimit(context, id) || RuleRuntimeStore.isOverDailyLimit(context, it) } ?: false
            val overSession = Prefs.p(context).getLong("over_limit_session_ms", 3L * 60_000L)
            val options = when {
                over -> listOf(60_000L, 3L * 60_000L, overSession).map { it.coerceAtMost(overSession) }.filter { it > 0 }.distinct()
                rule?.askSessionDuration == true -> listOf(5L, 10L, 15L).map { it * 60_000L }.map { if (remaining >= 0) it.coerceAtMost(remaining) else it }.filter { it > 0 }.distinct()
                rule != null -> listOf(if (remaining >= 0) rule.defaultSessionUsageMs.coerceAtMost(remaining) else rule.defaultSessionUsageMs).filter { it > 0 }
                else -> listOf(5L * 60_000L)
            }
            return mapOf("view" to "unlock", "ruleId" to id, "name" to (rule?.name ?: "AppLockout"), "options" to options)
        }
        val shouldOnboard = RuleRepository.getRules(context).isEmpty() &&
            !Prefs.p(context).getBoolean("flutter_onboarding_complete", false)
        return mapOf("view" to "home", "shouldOnboard" to shouldOnboard)
    }

    private fun gateStatus(activity: FlutterActivity): Map<String, Any?> {
        val id = activity.intent.getStringExtra(BrakeGateActivity.EXTRA_RULE_ID).orEmpty()
        val rule = RuleRuntimeStore.ruleForRuntime(activity, id) ?: RuleRepository.getRule(activity, id)
        val state = if (id.isBlank()) RuleRuntimeStore.STATE_LOCKED else RuleRuntimeStore.state(activity, id)
        val now = System.currentTimeMillis()
        val waitRemaining = if (id.isBlank()) 0L else
            (RuleRuntimeStore.challengeWaitDeadline(activity, id) - now).coerceAtLeast(0L)
        val phoneDeadline = if (id.isBlank()) 0L else RuleRuntimeStore.challengePhoneDeadline(activity, id)
        val phoneRemaining = if (phoneDeadline > 0L) (phoneDeadline - now).coerceAtLeast(0L) else 0L
        return mapOf(
            "state" to state,
            "challengeWait" to (rule?.challengeWait == true),
            "waitRemainingMs" to waitRemaining,
            "challengeWalk" to (rule?.challengeWalk == true),
            "requiredSteps" to (if (id.isBlank()) 0 else RuleRuntimeStore.challengeRequiredSteps(activity, id)),
            "walkedSteps" to (if (id.isBlank()) 0 else BrowserBlockService.currentWalkedSteps(activity, id)),
            "challengePhoneBreak" to (rule?.challengePhoneBreak == true),
            "phoneRemainingMs" to phoneRemaining,
            "challengeAll" to (rule?.challengeAll != false)
        )
    }

    private fun ruleMaps(context: Context): List<Map<String, Any?>> = RuleRepository.getRules(context).map { rule ->
        ruleMap(rule) + mapOf(
            "state" to RuleRuntimeStore.state(context, rule.id),
            "dailyUsageMs" to RuleRepository.dailyUsageRaw(context, rule.id),
            "dailySessions" to RuleRepository.dailySessionsRaw(context, rule.id)
        )
    }

    private fun ruleMap(rule: BrowserRule): Map<String, Any?> = mapOf(
        "id" to rule.id,
        "name" to rule.name,
        "enabled" to rule.enabled,
        "pausedUntilMs" to rule.pausedUntilMs,
        "fullLock" to rule.fullLock,
        "browsers" to rule.browsers,
        "sns" to rule.sns,
        "customPackages" to rule.customPackages.toList(),
        "allPlaces" to rule.allPlaces,
        "placeIds" to rule.placeIds.toList(),
        "challengeWait" to rule.challengeWait,
        "challengePhoneBreak" to rule.challengePhoneBreak,
        "challengeWalk" to rule.challengeWalk,
        "challengeAll" to rule.challengeAll,
        "waitMs" to rule.waitMs,
        "phoneBreakMs" to rule.phoneBreakMs,
        "walkSteps" to rule.walkSteps,
        "readyTimeoutMs" to rule.readyTimeoutMs,
        "askSessionDuration" to rule.askSessionDuration,
        "defaultSessionUsageMs" to rule.defaultSessionUsageMs,
        "sessionWindowMs" to rule.sessionWindowMs,
        "dailyUsageLimitMs" to rule.dailyUsageLimitMs,
        "dailySessionLimit" to rule.dailySessionLimit,
        "recoveryMs" to rule.recoveryMs,
        "escalationMode" to rule.escalationMode
    )

    private fun ruleFromMap(m: Map<*, *>): BrowserRule {
        fun bool(key: String, fallback: Boolean) = m[key] as? Boolean ?: fallback
        fun long(key: String, fallback: Long) = (m[key] as? Number)?.toLong() ?: fallback
        fun int(key: String, fallback: Int) = (m[key] as? Number)?.toInt() ?: fallback
        fun strings(key: String): Set<String> = (m[key] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.toSet().orEmpty()
        return BrowserRule(
            id = m["id"] as? String ?: BrowserRule().id,
            name = (m["name"] as? String).orEmpty().ifBlank { "制限" },
            enabled = bool("enabled", true),
            pausedUntilMs = long("pausedUntilMs", 0L),
            fullLock = bool("fullLock", false),
            browsers = bool("browsers", false),
            sns = bool("sns", false),
            customPackages = strings("customPackages"),
            allPlaces = bool("allPlaces", true),
            placeIds = strings("placeIds"),
            challengeWait = bool("challengeWait", true),
            challengePhoneBreak = bool("challengePhoneBreak", false),
            challengeWalk = bool("challengeWalk", false),
            challengeAll = bool("challengeAll", true),
            waitMs = long("waitMs", 30_000L),
            phoneBreakMs = long("phoneBreakMs", 3L * 60_000L),
            walkSteps = int("walkSteps", 100),
            readyTimeoutMs = long("readyTimeoutMs", 0L),
            askSessionDuration = bool("askSessionDuration", true),
            defaultSessionUsageMs = long("defaultSessionUsageMs", 10L * 60_000L),
            sessionWindowMs = long("sessionWindowMs", 30L * 60_000L),
            dailyUsageLimitMs = long("dailyUsageLimitMs", 60L * 60_000L),
            dailySessionLimit = int("dailySessionLimit", 5),
            recoveryMs = long("recoveryMs", 5L * 60_000L),
            escalationMode = "none"
        )
    }

    private fun recordMaps(context: Context): List<Map<String, Any?>> {
        val ruleIds = RuleRepository.metricRuleIds(context).toList()
        if (ruleIds.isEmpty()) return emptyList()
        val histories = ruleIds.map { RuleRepository.historyRecords(context, it, 30) }
        return (0 until 30).mapNotNull { index ->
            val rows = histories.mapNotNull { it.getOrNull(index) }
            val first = rows.firstOrNull() ?: return@mapNotNull null
            val usage = rows.sumOf { it.usageMs }
            val sessions = rows.sumOf { it.sessions }
            val commitmentBroken = rows.any { it.commitmentBroken }
            val overLimit = rows.any { it.overLimit }
            val hasActivity = usage > 0L || sessions > 0
            mapOf(
                "dayKey" to first.dayKey,
                "label" to first.label,
                "usageMs" to usage,
                "sessions" to sessions,
                "hasActivity" to hasActivity,
                "hasData" to (hasActivity || commitmentBroken || overLimit),
                "commitmentBroken" to commitmentBroken,
                "overLimit" to overLimit
            )
        }.reversed()
    }

    private fun health(context: Context): Map<String, Any?> {
        val notifications = if (Build.VERSION.SDK_INT >= 24) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.areNotificationsEnabled() == true
        } else true
        val activityRecognition = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationReady = location && backgroundLocation
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val rules = RuleRepository.getRules(context)
        return mapOf(
            "accessibility" to accessibilityEnabled(context),
            "notifications" to notifications,
            "activityRecognition" to activityRecognition,
            "location" to location,
            "backgroundLocation" to backgroundLocation,
            "locationReady" to locationReady,
            "batteryUnrestricted" to batteryUnrestricted,
            "needsActivityRecognition" to rules.any { it.enabled && it.challengeWalk },
            "needsLocation" to rules.any { it.enabled && !it.allPlaces }
        )
    }

    private fun validationErrors(rule: BrowserRule): List<String> {
        val errors = mutableListOf<String>()
        if (!rule.browsers && !rule.sns && rule.customPackages.isEmpty()) {
            errors += "対象アプリを1つ以上選んでください"
        }
        if (!rule.allPlaces && rule.placeIds.isEmpty()) {
            errors += "場所を指定する場合は、有効な場所を1つ以上選んでください"
        }
        if (!rule.fullLock && !rule.challengeWait && !rule.challengePhoneBreak && !rule.challengeWalk) {
            errors += "完全ロックでない場合は、解除条件を1つ以上選んでください"
        }
        if (rule.challengeWait && rule.waitMs <= 0L) errors += "待つ時間を設定してください"
        if (rule.challengeWalk && rule.walkSteps <= 0) errors += "必要歩数を設定してください"
        return errors
    }

    private fun accessibilityEnabled(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return@runCatching false
        val mine = ComponentName(context, BrowserBlockService::class.java)
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo?.serviceInfo ?: return@any false
            val component = ComponentName(service.packageName, service.name)
            TextUtils.equals(component.flattenToString(), mine.flattenToString()) ||
                TextUtils.equals(component.flattenToShortString(), mine.flattenToShortString())
        }
    }.getOrDefault(false)

    private fun startSession(activity: FlutterActivity, id: String, usageMs: Long) {
        if (id.isBlank()) return
        val pkg = RuleRuntimeStore.pendingTarget(activity, id)
        if (pkg.isBlank()) return
        val launchIntent = activity.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) {
            RuleRuntimeStore.declineReady(activity, id)
            NotificationController.cancel(activity, id)
            BrowserBlockService.requestRuntimeSync()
            goHome(activity)
            return
        }
        RuleRuntimeStore.startSession(activity, id, usageMs)
        NotificationController.showSession(activity, id)
        BrowserBlockService.requestRuntimeSync()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(launchIntent)
        activity.finish()
    }

    private fun goHome(activity: FlutterActivity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        activity.finish()
    }
}
