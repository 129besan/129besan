from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Runtime: make "none" really mean no escalation, and make dailyUsageLimitMs=0
# match the editor's "unlimited" semantics.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/runtime/RuleRuntimeStore.kt')
s = p.read_text()
s = replace_once(
    s,
    '''    private fun escalationMultiplier(mode: String, level: Int): Double {\n        if (level <= 0 || mode == "off") return 1.0\n''',
    '''    private fun escalationDisabled(mode: String): Boolean = mode == "off" || mode == "none"\n\n    private fun escalationMultiplier(mode: String, level: Int): Double {\n        if (level <= 0 || escalationDisabled(mode)) return 1.0\n''',
    'escalation multiplier',
)
s = replace_once(
    s,
    '''    private fun escalationDecayMs(mode: String): Long = when (mode) {\n        "strong" -> 3L * 60L * 60_000L\n        "off" -> 0L\n        else -> 90L * 60_000L\n    }\n''',
    '''    private fun escalationDecayMs(mode: String): Long = when {\n        escalationDisabled(mode) -> 0L\n        mode == "strong" -> 3L * 60L * 60_000L\n        else -> 90L * 60_000L\n    }\n''',
    'escalation decay',
)
s = replace_once(
    s,
    '''        val currentLevel = effectiveEscalationLevel(context, ruleId, rule, now)\n        val nextLevel = min(4, currentLevel + 1)\n''',
    '''        val currentLevel = effectiveEscalationLevel(context, ruleId, rule, now)\n        val nextLevel = if (escalationDisabled(rule.escalationMode)) 0 else min(4, currentLevel + 1)\n''',
    'next escalation level',
)
s = replace_once(
    s,
    '''    fun effectiveEscalationLevel(context: Context, ruleId: String, rule: BrowserRule, now: Long): Int {\n        val level = Prefs.p(context).getInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), 0)\n''',
    '''    fun effectiveEscalationLevel(context: Context, ruleId: String, rule: BrowserRule, now: Long): Int {\n        if (escalationDisabled(rule.escalationMode)) return 0\n        val level = Prefs.p(context).getInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), 0)\n''',
    'effective escalation level',
)
s = replace_once(
    s,
    '''        val timeOver = rule.dailyUsageLimitMs >= 0L && usage >= rule.dailyUsageLimitMs\n''',
    '''        val timeOver = rule.dailyUsageLimitMs > 0L && usage >= rule.dailyUsageLimitMs\n''',
    'daily time limit',
)
s = replace_once(
    s,
    '''        if (rule.dailyUsageLimitMs < 0L) return -1L\n''',
    '''        if (rule.dailyUsageLimitMs <= 0L) return -1L\n''',
    'daily remaining unlimited',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Service: notification early-end needs a reliable HOME action when Accessibility
# is alive, replacing the removed overlay's old lock-now behavior.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()
needle = '''    public static void requestRuntimeSync() {\n        BrowserBlockService service = activeService.get();\n        if (service != null) {\n            service.handler.post(service::syncAllRuntimes);\n        }\n    }\n\n'''
addition = needle + '''    public static void requestReturnHomeAndSync() {\n        BrowserBlockService service = activeService.get();\n        if (service != null) {\n            service.handler.post(() -> {\n                service.performGlobalAction(GLOBAL_ACTION_HOME);\n                service.syncAllRuntimes();\n            });\n        }\n    }\n\n'''
s = replace_once(s, needle, addition, 'return home helper')
p.write_text(s)

# -----------------------------------------------------------------------------
# Notification action: ending a session is an actual exit, not just a state flip.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/NotificationActionReceiver.java')
s = p.read_text()
s = replace_once(
    s,
    '''            BrowserBlockService.requestRuntimeSync();\n            return;\n        }\n\n        if (ACTION_DECLINE_READY.equals(intent.getAction())) {\n''',
    '''            BrowserBlockService.requestReturnHomeAndSync();\n            return;\n        }\n\n        if (ACTION_DECLINE_READY.equals(intent.getAction())) {\n''',
    'notification session exit',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Flutter bridge: clear rule notifications immediately on operations that kill a
# runtime, instead of waiting for a service sync that may no longer see the id.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = replace_once(
    s,
    '''                            RuleRepository.saveRule(activity, candidate)\n                            BrowserBlockService.requestRuntimeSync()\n''',
    '''                            RuleRepository.saveRule(activity, candidate)\n                            if (!candidate.enabled) {\n                                RuleRuntimeStore.clearRuntime(activity, candidate.id)\n                                NotificationController.cancel(activity, candidate.id)\n                            }\n                            BrowserBlockService.requestRuntimeSync()\n''',
    'save disabled cleanup',
)
s = replace_once(
    s,
    '''                            RuleRepository.deleteRule(activity, id)\n                        }\n                        BrowserBlockService.requestRuntimeSync()\n''',
    '''                            RuleRepository.deleteRule(activity, id)\n                            NotificationController.cancel(activity, id)\n                        }\n                        BrowserBlockService.requestRuntimeSync()\n''',
    'delete notification cleanup',
)
s = replace_once(
    s,
    '''                        if (id.isNotBlank()) RuleRepository.pauseRule(activity, id, until)\n                        BrowserBlockService.requestRuntimeSync()\n''',
    '''                        if (id.isNotBlank()) {\n                            RuleRepository.pauseRule(activity, id, until)\n                            if (durationMs > 0L) NotificationController.cancel(activity, id)\n                        }\n                        BrowserBlockService.requestRuntimeSync()\n''',
    'pause notification cleanup',
)
s = replace_once(
    s,
    '''                        RuleRepository.setEnabled(activity, id, enabled)\n                        BrowserBlockService.requestRuntimeSync()\n''',
    '''                        RuleRepository.setEnabled(activity, id, enabled)\n                        if (!enabled) NotificationController.cancel(activity, id)\n                        BrowserBlockService.requestRuntimeSync()\n''',
    'enable notification cleanup',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# UI copy: describe exactly what the records mean rather than implying a full-day
# adherence metric that isn't actually measured.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace("Expanded(child: _MetricCard(label: '継続', value: '$success', caption: '設定を保てた日'))", "Expanded(child: _MetricCard(label: '変更なし', value: '$success', caption: '利用記録のある日'))")
s = s.replace("Text('青は設定を保てた日、赤は一時停止・無効化などを行った日です。'", "Text('青は利用記録があり設定を弱めなかった日、赤は一時停止・無効化などを行った日です。'")
s = s.replace("const Text('次の変更は今日のストリークに影響します。'),", "const Text('次の変更は、今日の記録に「制限を弱めた変更」として残ります。'),")
p.write_text(s)
