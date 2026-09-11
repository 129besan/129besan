from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Flutter UI consistency and settings-protection correctness.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()

# Confirmed weakening save must not be blocked by the outer saving=true guard.
s = replace_once(
    s,
    '''        if (ok == true && mounted) await save(confirmed: true);\n''',
    '''        if (ok == true && mounted) {\n          setState(() => saving = false);\n          await save(confirmed: true);\n          return;\n        }\n''',
    'confirmed weakening save',
)

# Positive wording is easier to understand than an inverted switch.
s = replace_once(
    s,
    '''                  title: const Text('場所を指定しない'),\n                  subtitle: const Text('どこにいてもこの制限を使います'),\n''',
    '''                  title: const Text('どこでも有効'),\n                  subtitle: const Text('オフにすると、選んだ場所だけで有効になります'),\n''',
    'all places wording',
)

# When duration is chosen every time, the default duration is not used and should
# not be presented as an editable setting.
s = replace_once(
    s,
    '''                    title: const Text('利用前に時間を選ぶ'),\n                    value: b('askSessionDuration', true),\n                    onChanged: (v) => setValue('askSessionDuration', v),\n                  ),\n                  _ChoiceRow(\n                    label: '1回の標準利用時間',\n                    value: n('defaultSessionUsageMs', 600000),\n                    options: const {300000: '5分', 600000: '10分', 900000: '15分', 1200000: '20分'},\n                    onChanged: (v) => setValue('defaultSessionUsageMs', v),\n                  ),\n''',
    '''                    title: const Text('利用時間を毎回選ぶ'),\n                    subtitle: const Text('解除後に5分・10分・15分から選びます'),\n                    value: b('askSessionDuration', true),\n                    onChanged: (v) => setValue('askSessionDuration', v),\n                  ),\n                  if (!b('askSessionDuration', true))\n                    _ChoiceRow(\n                      label: '1回の利用時間',\n                      value: n('defaultSessionUsageMs', 600000),\n                      options: const {300000: '5分', 600000: '10分', 900000: '15分', 1200000: '20分'},\n                      onChanged: (v) => setValue('defaultSessionUsageMs', v),\n                    ),\n''',
    'session duration UI',
)
s = s.replace("options: const {'none': 'なし', 'standard': 'Standard', 'strong': 'Strong'}", "options: const {'none': 'なし', 'standard': '標準', 'strong': '強め'}")

# Add an unobtrusive way to explicitly end a live session now that the overlay is gone.
s = replace_once(
    s,
    '''            const Icon(Icons.chevron_right_rounded, size: 22),\n''',
    '''            if (state == 'SESSION')\n              IconButton(\n                tooltip: '今回の利用を終了',\n                onPressed: () async {\n                  await NativeBridge.call('endSession', {'id': rule['id']});\n                  await onChanged();\n                },\n                icon: const Icon(Icons.stop_circle_outlined),\n              ),\n            const Icon(Icons.chevron_right_rounded, size: 22),\n''',
    'home end-session action',
)

# Settings auto-refreshes on resume; a separate silent "動作チェック" tile adds
# little value and looks like a diagnostic control.
old_check = '''          const SizedBox(height: 14),\n          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.fact_check_outlined),\n              title: const Text('動作チェック',\n                  style: TextStyle(fontWeight: FontWeight.w700)),\n              subtitle: const Text('制限エンジンと権限状態を再確認します'),\n              trailing: const Icon(Icons.refresh_rounded),\n              onTap: refresh,\n            ),\n          ),\n'''
s = replace_once(s, old_check, '', 'remove silent health check')

# Keep user-facing information useful rather than exposing implementation trivia.
s = s.replace(
    "text: '制限設定や利用記録は端末内に保存します。Accessibilityは前面アプリの検知に使います。'),",
    "text: '制限設定や利用記録はアプリ内に保存し、AppLockout独自のサーバーへ送信しません。Accessibilityは前面アプリの検知に使います。'),",
)
old_impl = '''              Divider(height: 1),\n              _InfoTile(\n                icon: Icons.code_rounded,\n                title: '実装',\n                text: 'UIはFlutter Material 3、Androidの制限エンジンはネイティブ実装です。'),\n'''
s = replace_once(s, old_impl, '', 'remove implementation info')
p.write_text(s)

# -----------------------------------------------------------------------------
# Native bridge: provide a non-overlay end-session action for Home.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
needle = '''                    "declineReady" -> {\n                        val id = activity.intent.getStringExtra(UnlockGateActivity.EXTRA_RULE_ID).orEmpty()\n                        if (id.isNotBlank()) {\n                            RuleRuntimeStore.declineReady(activity, id)\n                            NotificationController.cancel(activity, id)\n                            BrowserBlockService.requestRuntimeSync()\n                        }\n                        activity.finish()\n                        result.success(null)\n                    }\n'''
addition = '''                    "endSession" -> {\n                        val id = call.argument<String>("id").orEmpty()\n                        if (id.isNotBlank() && RuleRuntimeStore.state(activity, id) == RuleRuntimeStore.STATE_SESSION) {\n                            RuleRuntimeStore.finishSession(activity, id)\n                            if (RuleRuntimeStore.state(activity, id) == RuleRuntimeStore.STATE_RECOVERY) {\n                                NotificationController.showRecovery(activity, id)\n                            } else {\n                                NotificationController.cancel(activity, id)\n                            }\n                            BrowserBlockService.requestRuntimeSync()\n                        }\n                        result.success(null)\n                    }\n'''
s = replace_once(s, needle, addition + needle, 'end session bridge')
p.write_text(s)

# -----------------------------------------------------------------------------
# Notifications: avoid repeated heads-up refreshes for READY / Full Lock.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/NotificationController.java')
s = p.read_text()
s = s.replace('.setOnlyAlertOnce(false)\n                .addAction(new Notification.Action.Builder(', '.setOnlyAlertOnce(true)\n                .addAction(new Notification.Action.Builder(', 1)
s = s.replace('.setOngoing(false)\n                .setOnlyAlertOnce(false);', '.setOngoing(false)\n                .setOnlyAlertOnce(true);', 1)
p.write_text(s)
