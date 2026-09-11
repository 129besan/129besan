package dev.besan.browserbrake

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
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
                    "setRuleEnabled" -> {
                        val id = call.argument<String>("id").orEmpty()
                        val enabled = call.argument<Boolean>("enabled") ?: true
                        RuleRepository.setEnabled(activity, id, enabled)
                        BrowserBlockService.requestRuntimeSync()
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
                        activity.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
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
                        val usageMs = (call.argument<Number>("usageMs")?.toLong() ?: 10L * 60_000L)
                        startSession(activity, id, usageMs)
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
        return mapOf("view" to "home")
    }

    private fun ruleMaps(context: Context): List<Map<String, Any?>> = RuleRepository.getRules(context).map { rule ->
        mapOf(
            "id" to rule.id,
            "name" to rule.name,
            "enabled" to rule.enabled,
            "pausedUntilMs" to rule.pausedUntilMs,
            "state" to RuleRuntimeStore.state(context, rule.id),
            "dailyUsageMs" to RuleRepository.dailyUsageRaw(context, rule.id),
            "dailySessions" to RuleRepository.dailySessionsRaw(context, rule.id)
        )
    }

    private fun recordMaps(context: Context): List<Map<String, Any?>> {
        val first = RuleRepository.getRules(context).firstOrNull() ?: return emptyList()
        return RuleRepository.historyRecords(context, first.id, 30).reversed().map {
            mapOf(
                "dayKey" to it.dayKey,
                "label" to it.label,
                "usageMs" to it.usageMs,
                "sessions" to it.sessions,
                "hasData" to it.hasData,
                "commitmentBroken" to it.commitmentBroken
            )
        }
    }

    private fun health(context: Context): Map<String, Any?> {
        val notifications = if (Build.VERSION.SDK_INT >= 24) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.areNotificationsEnabled() == true
        } else true
        return mapOf("accessibility" to accessibilityEnabled(context), "notifications" to notifications)
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
        RuleRuntimeStore.startSession(activity, id, usageMs)
        NotificationController.showSession(activity, id)
        BrowserBlockService.requestRuntimeSync()
        activity.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(it)
        }
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
