from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# BrowserBlockService: remove the in-app session accessibility overlay and noisy
# diagnostics. Session state remains durable and visible through notifications.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()

for imp in [
    'import android.graphics.Color;\n',
    'import android.graphics.PixelFormat;\n',
    'import android.graphics.Typeface;\n',
    'import android.graphics.drawable.GradientDrawable;\n',
    'import android.view.Gravity;\n',
    'import android.view.View;\n',
    'import android.view.WindowManager;\n',
    'import android.widget.LinearLayout;\n',
    'import android.widget.TextView;\n',
]:
    s = s.replace(imp, '')

s = s.replace('''    private WindowManager windowManager;\n    private View sessionOverlay;\n    private TextView sessionOverlayText;\n\n''', '')
s = s.replace('''    private long lastDiagnosticAt = 0L;\n    private static final long INTERACTION_THROTTLE_MS = 200L;\n    private static final long DIAGNOSTIC_THROTTLE_MS = 500L;\n\n''', '''    private static final long INTERACTION_THROTTLE_MS = 200L;\n\n''')
s = s.replace('    private final Runnable overlayUpdater = this::updateSessionOverlayText;\n', '')
s = s.replace('        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);\n', '')
s = s.replace('        Toast.makeText(this, "AppLockout が有効になりました", Toast.LENGTH_SHORT).show();\n', '')

# Refresh location before paused-rule context matching on foreground changes.
s = replace_once(
    s,
    '''        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isTransientOverlayPackage(pkg)) {\n            lastForegroundPackage = pkg;\n            updatePausedForeground(pkg);\n            updatePhoneBreakForeground(pkg);\n        }\n''',
    '''        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isTransientOverlayPackage(pkg)) {\n            lastForegroundPackage = pkg;\n            refreshLastKnownLocation();\n            updatePausedForeground(pkg);\n            updatePhoneBreakForeground(pkg);\n        }\n''',
    'foreground location refresh',
)

# Persistent debug writes on accessibility events are not part of product behavior.
s = s.replace('''        recordRuntimeDiagnostic(type, pkg,\n                runtimeMatchingRule != null ? runtimeMatchingRule : durableMatchingRule);\n\n''', '')
start = s.find('    private void recordRuntimeDiagnostic(int eventType, String pkg, BrowserRule matchingRule) {')
if start >= 0:
    end = s.find('    public static int currentWalkedSteps', start)
    if end < 0:
        raise SystemExit('missing end of diagnostic method')
    s = s[:start] + s[end:]

# Remove the entire accessibility overlay implementation.
start = s.find('    private int dp(int value) {')
if start < 0:
    raise SystemExit('missing overlay block start')
end = s.find('    @Override\n    public void onLocationChanged', start)
if end < 0:
    raise SystemExit('missing overlay block end')
s = s[:start] + s[end:]

# Remove every now-obsolete overlay call/callback.
s = re.sub(r'^\s*hideSessionOverlay\(\);\n', '', s, flags=re.MULTILINE)
s = re.sub(r'^\s*showSessionOverlay\([^\n;]+\);\n', '', s, flags=re.MULTILINE)
s = s.replace('        handler.removeCallbacks(overlayUpdater);\n', '')

# Collapse the overlay-only branch in runtime reconciliation.
old = '''        if (!currentForegroundRuleId.isEmpty()\n                && !RuleRuntimeStore.STATE_SESSION.equals(\n                        RuleRuntimeStore.state(this, currentForegroundRuleId))) {\n            currentForegroundRuleId = "";\n        } else if (!currentForegroundRuleId.isEmpty()) {\n        } else {\n        }\n'''
if old in s:
    s = s.replace(old, '''        if (!currentForegroundRuleId.isEmpty()\n                && !RuleRuntimeStore.STATE_SESSION.equals(\n                        RuleRuntimeStore.state(this, currentForegroundRuleId))) {\n            currentForegroundRuleId = "";\n        }\n''', 1)

p.write_text(s)

# -----------------------------------------------------------------------------
# FlutterBridge: safer defaults and correct Android battery settings destination.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = s.replace(
    '"newRuleTemplate" -> result.success(ruleMap(BrowserRule(browsers = true, challengeWait = true, challengePhoneBreak = false)))',
    '"newRuleTemplate" -> result.success(ruleMap(BrowserRule(browsers = false, challengeWait = true, challengePhoneBreak = false)))',
)
s = s.replace(
    'activity.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))',
    'activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# App catalog: always downscale launcher icons before sending them over the
# MethodChannel. Large adaptive/bitmap icons otherwise make the picker janky.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterCatalogBridge.kt')
s = p.read_text()
s = s.replace('import android.graphics.drawable.BitmapDrawable\n', '')
old_start = s.find('    private fun drawablePngBase64(drawable: Drawable): String {')
if old_start < 0:
    raise SystemExit('missing drawablePngBase64')
old_end = s.find('\n    }\n}', old_start)
if old_end < 0:
    raise SystemExit('missing drawablePngBase64 end')
old_end += len('\n    }')
new_fun = '''    private fun drawablePngBase64(drawable: Drawable): String {\n        val size = 72\n        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)\n        val canvas = Canvas(bitmap)\n        drawable.setBounds(0, 0, size, size)\n        drawable.draw(canvas)\n        val out = ByteArrayOutputStream()\n        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)\n        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)\n    }'''
s = s[:old_start] + new_fun + s[old_end:]
p.write_text(s)

# -----------------------------------------------------------------------------
# Notifications: the notification replaces the removed overlay as the one place
# for session state + an explicit early-end action.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/NotificationController.java')
s = p.read_text().replace('                        "ロック",\n', '                        "利用を終了",\n', 1)
p.write_text(s)

# -----------------------------------------------------------------------------
# Main Flutter UI: reduce navigation/controls, make pause deliberate, and fix
# onboarding cancellation semantics.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()

old = '''    await Navigator.of(context).push<bool>(\n      MaterialPageRoute(builder: (_) => const GuidedSetupModernPage(), fullscreenDialog: true),\n    );\n    try {\n      await NativeBridge.call('completeOnboarding');\n    } catch (_) {}\n    if (mounted) setState(() => generation++);\n'''
new = '''    final completed = await Navigator.of(context).push<bool>(\n      MaterialPageRoute(builder: (_) => const GuidedSetupModernPage(), fullscreenDialog: true),\n    );\n    if (completed == true) {\n      try {\n        await NativeBridge.call('completeOnboarding');\n      } catch (_) {}\n      if (mounted) setState(() => generation++);\n    }\n'''
s = replace_once(s, old, new, 'onboarding completion')

# Home only needs three primary destinations; static information moves to Settings.
s = replace_once(
    s,
    '''    final pages = <Widget>[\n      const HomePage(),\n      const RecordsPage(),\n      const SettingsPage(),\n      const InfoPage(),\n    ];\n''',
    '''    final pages = <Widget>[\n      const HomePage(),\n      const RecordsPage(),\n      const SettingsPage(),\n    ];\n''',
    'primary pages',
)
s = replace_once(
    s,
    '''            NavigationDestination(\n                icon: Icon(Icons.tune_outlined),\n                selectedIcon: Icon(Icons.tune),\n                label: '設定'),\n            NavigationDestination(\n                icon: Icon(Icons.info_outline),\n                selectedIcon: Icon(Icons.info),\n                label: '情報'),\n''',
    '''            NavigationDestination(\n                icon: Icon(Icons.tune_outlined),\n                selectedIcon: Icon(Icons.tune),\n                label: '設定'),\n''',
    'navigation info destination',
)

# Rule cards become read-only summaries; weakening is only done from the editor.
s = replace_once(
    s,
    '''    final usage = (rule['dailyUsageMs'] as num?)?.toInt() ?? 0;\n    return _GlassCard(\n''',
    '''    final usage = (rule['dailyUsageMs'] as num?)?.toInt() ?? 0;\n    final sessions = (rule['dailySessions'] as num?)?.toInt() ?? 0;\n    final status = !enabled\n        ? '無効'\n        : paused\n            ? '一時停止中  •  今日 ${_minutes(usage)}分'\n            : active\n                ? _stateLabel(state)\n                : '有効  •  今日 ${_minutes(usage)}分・$sessions回';\n    return _GlassCard(\n''',
    'rule status values',
)
old_status = '''                Text(\n                  paused\n                      ? '一時停止中'\n                      : active\n                          ? _stateLabel(state)\n                          : '${enabled ? '有効' : '無効'}  •  今日 ${_minutes(usage)}分',\n                  style: TextStyle(\n                      color: Theme.of(context).colorScheme.onSurfaceVariant,\n                      fontSize: 13),\n                ),\n'''
s = replace_once(
    s,
    old_status,
    '''                Text(\n                  status,\n                  style: TextStyle(\n                      color: Theme.of(context).colorScheme.onSurfaceVariant,\n                      fontSize: 13),\n                ),\n''',
    'rule status text',
)
old_switch = '''            Switch(\n              value: enabled,\n              onChanged: (value) async {\n                if (!value) {\n                  final ok = await showDialog<bool>(\n                    context: context,\n                    barrierDismissible: false,\n                    builder: (_) => const WeakeningConfirmDialog(\n                      reasons: ['制限を無効にする'],\n                    ),\n                  );\n                  if (ok != true) return;\n                }\n                await NativeBridge.call(\n                    'setRuleEnabled', {'id': rule['id'], 'enabled': value});\n                await onChanged();\n              },\n            ),\n            const Icon(Icons.chevron_right_rounded, size: 20),\n'''
s = replace_once(
    s,
    old_switch,
    '''            const Icon(Icons.chevron_right_rounded, size: 22),\n''',
    'home enable switch',
)

# Temporary pause is a deliberate bypass, so restore a small delay instead of
# immediately disabling enforcement from one accidental tap.
old_pause = '''  Future<void> pause(int minutes) async {\n    await NativeBridge.call('pauseRule', {\n      'id': draft['id'],\n      'durationMs': minutes * 60 * 1000,\n    });\n    if (mounted) Navigator.pop(context, true);\n  }\n'''
new_pause = '''  Future<void> pause(int minutes) async {\n    if (minutes > 0) {\n      final seconds = minutes <= 15 ? 8 : 15;\n      final ok = await showDialog<bool>(\n        context: context,\n        barrierDismissible: false,\n        builder: (_) => PauseConfirmDialog(minutes: minutes, seconds: seconds),\n      );\n      if (ok != true) return;\n    }\n    await NativeBridge.call('pauseRule', {\n      'id': draft['id'],\n      'durationMs': minutes * 60 * 1000,\n    });\n    if (mounted) Navigator.pop(context, true);\n  }\n'''
s = replace_once(s, old_pause, new_pause, 'pause confirmation')

# Records wording should describe what the metric really means.
s = s.replace("Expanded(child: _MetricCard(label: '30日', value: '$success', caption: '守れた日'))", "Expanded(child: _MetricCard(label: '継続', value: '$success', caption: '設定を保てた日'))")
s = s.replace("Expanded(child: _MetricCard(label: '記録', value: '${records.where((e) => e['hasData'] == true).length}', caption: 'データのある日'))", "Expanded(child: _MetricCard(label: '利用記録', value: '${records.where((e) => e['hasData'] == true).length}', caption: '記録がある日'))")
s = s.replace("Text('青は守れた日、赤は設定を弱めた日です。'", "Text('青は設定を保てた日、赤は一時停止・無効化などを行った日です。'")

# Fold the low-frequency Info tab into Settings.
info_start = s.find('class InfoPage extends StatelessWidget {')
info_end = s.find('class BrakeView extends StatefulWidget {', info_start)
if info_start < 0 or info_end < 0:
    raise SystemExit('missing InfoPage block')
s = s[:info_start] + s[info_end:]

settings_anchor = '''          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.settings_applications_outlined),\n              title: const Text('Androidアプリ設定', style: TextStyle(fontWeight: FontWeight.w700)),\n              subtitle: const Text('権限・バッテリー・アプリ情報をOS側で確認'),\n              trailing: const Icon(Icons.open_in_new_rounded),\n              onTap: () => NativeBridge.call('openAppSettings'),\n            ),\n          ),\n'''
settings_extra = settings_anchor + '''          const SizedBox(height: 18),\n          const SectionLabel('AppLockoutについて'),\n          const _GlassCard(\n            child: Column(children: [\n              _InfoTile(\n                icon: Icons.psychology_alt_outlined,\n                title: '考え方',\n                text: '反射的なアプリ起動の前に、短い選び直しの時間をつくります。'),\n              Divider(height: 1),\n              _InfoTile(\n                icon: Icons.lock_outline_rounded,\n                title: 'プライバシー',\n                text: '制限設定や利用記録は端末内に保存します。Accessibilityは前面アプリの検知に使います。'),\n              Divider(height: 1),\n              _InfoTile(\n                icon: Icons.code_rounded,\n                title: '実装',\n                text: 'UIはFlutter Material 3、Androidの制限エンジンはネイティブ実装です。'),\n            ]),\n          ),\n'''
s = replace_once(s, settings_anchor, settings_extra, 'settings about section')

# Insert pause countdown dialog before RecordsPage.
marker = 'class RecordsPage extends StatefulWidget {'
pause_dialog = r'''class PauseConfirmDialog extends StatefulWidget {
  const PauseConfirmDialog({super.key, required this.minutes, required this.seconds});
  final int minutes;
  final int seconds;

  @override
  State<PauseConfirmDialog> createState() => _PauseConfirmDialogState();
}

class _PauseConfirmDialogState extends State<PauseConfirmDialog> {
  late int remaining = widget.seconds;
  Timer? timer;

  @override
  void initState() {
    super.initState();
    timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (remaining <= 1) {
        timer?.cancel();
        setState(() => remaining = 0);
      } else {
        setState(() => remaining--);
      }
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text('${widget.minutes}分、一時停止しますか？'),
        content: Text(
          remaining == 0
              ? '一時停止中の対象アプリ利用も記録には加算されます。'
              : '誤操作を防ぐため、あと $remaining 秒で一時停止できます。',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('戻る')),
          FilledButton(
            onPressed: remaining == 0 ? () => Navigator.pop(context, true) : null,
            child: const Text('一時停止する'),
          ),
        ],
      );
}

'''
if marker not in s:
    raise SystemExit('missing RecordsPage marker')
s = s.replace(marker, pause_dialog + marker, 1)

p.write_text(s)

# -----------------------------------------------------------------------------
# Guided setup: do not let the user finish a rule that cannot actually enforce.
# Notifications remain optional.
# -----------------------------------------------------------------------------
p = Path('lib/onboarding_modern.dart')
s = p.read_text()
s = replace_once(
    s,
    '''    final accessibility = health['accessibility'] == true;\n    final activity = health['activityRecognition'] == true;\n    final notifications = health['notifications'] == true;\n''',
    '''    final accessibility = health['accessibility'] == true;\n    final activity = health['activityRecognition'] == true;\n    final notifications = health['notifications'] == true;\n    final requiredReady = accessibility && (challenge != 'walk' || activity);\n''',
    'guided required readiness',
)
s = replace_once(
    s,
    '''      action: FilledButton.icon(\n        onPressed: saving ? null : onDone,\n''',
    '''      action: FilledButton.icon(\n        onPressed: saving || !requiredReady ? null : onDone,\n''',
    'guided final action',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Version alpha6.
# -----------------------------------------------------------------------------
p = Path('pubspec.yaml')
s = p.read_text()
s = re.sub(r'^version: .*$', 'version: 0.6.0-alpha.6+24', s, flags=re.MULTILINE)
p.write_text(s)
