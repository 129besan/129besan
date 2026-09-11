from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()
if "import 'design_system.dart';" not in s:
    s = s.replace("import 'catalog_pages.dart';", "import 'catalog_pages.dart';\nimport 'design_system.dart';\nimport 'onboarding_modern.dart';")

if 'const seed = Color(0xFF1769AA);' in s:
    start = s.index('    const seed = Color(0xFF1769AA);')
    end = s.index('      home: _view == null', start)
    s = s[:start] + "    return MaterialApp(\n      debugShowCheckedModeBanner: false,\n      title: 'AppLockout',\n      theme: buildAppTheme(),\n" + s[end:]

needle = "    draft = Map<String, dynamic>.from(widget.initial);\n"
if "if (widget.isNew) draft['challengePhoneBreak'] = false;" not in s:
    s = s.replace(needle, needle + "    if (widget.isNew) draft['challengePhoneBreak'] = false;\n", 1)

old = """                  SwitchListTile(\n                    contentPadding: EdgeInsets.zero,\n                    title: const Text('スマホ休憩'),\n                    value: b('challengePhoneBreak', true),\n                    onChanged: (v) => setValue('challengePhoneBreak', v),\n                  ),\n                  if (b('challengePhoneBreak'))\n                    _ChoiceRow(\n                      label: '休憩時間',\n                      value: n('phoneBreakMs', 180000),\n                      options: const {60000: '1分', 180000: '3分', 300000: '5分', 600000: '10分'},\n                      onChanged: (v) => setValue('phoneBreakMs', v),\n                    ),\n"""
new = """                  if (b('challengePhoneBreak'))\n                    ListTile(\n                      contentPadding: EdgeInsets.zero,\n                      leading: const Icon(Icons.history_rounded),\n                      title: const Text('旧「スマホ休憩」'),\n                      subtitle: const Text('既存ルールとの互換用です。新規では使用しません。'),\n                      trailing: TextButton(\n                        onPressed: () => setValue('challengePhoneBreak', false),\n                        child: const Text('外す'),\n                      ),\n                    ),\n"""
s = s.replace(old, new)
s = s.replace("[b('challengeWait'), b('challengePhoneBreak'), b('challengeWalk')]", "[b('challengeWait'), b('challengeWalk')]")
s = s.replace('MaterialPageRoute(builder: (_) => const GuidedSetupPage())', 'MaterialPageRoute(builder: (_) => const GuidedSetupModernPage())')

old_card = """class _GlassCard extends StatelessWidget {\n  const _GlassCard({required this.child});\n  final Widget child;\n  @override\n  Widget build(BuildContext context) => Card(\n        color: Theme.of(context).colorScheme.surface.withValues(alpha: .91),\n        child: child,\n      );\n}\n"""
new_card = """class _GlassCard extends StatelessWidget {\n  const _GlassCard({required this.child});\n  final Widget child;\n  @override\n  Widget build(BuildContext context) => SoftSurface(child: child);\n}\n"""
s = s.replace(old_card, new_card)

s = s.replace("          const SizedBox(height: 18),\n          _GlassCard(\n            child: Column(children: [\n              _settingTile(Icons.accessibility_new_rounded", "          const SizedBox(height: 18),\n          const SectionLabel('動作に必要な設定'),\n          _GlassCard(\n            child: Column(children: [\n              _settingTile(Icons.accessibility_new_rounded", 1)
s = s.replace("          const SizedBox(height: 14),\n          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.auto_awesome_outlined)", "          const SizedBox(height: 14),\n          const SectionLabel('制限'),\n          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.auto_awesome_outlined)", 1)

check = """          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.fact_check_outlined),\n              title: const Text('動作チェック',\n                  style: TextStyle(fontWeight: FontWeight.w700)),\n              subtitle: const Text('制限エンジンと権限状態を再確認します'),\n              trailing: const Icon(Icons.refresh_rounded),\n              onTap: refresh,\n            ),\n          ),\n"""
if "title: const Text('Androidアプリ設定'" not in s:
    s = s.replace(check, check + """          const SizedBox(height: 10),\n          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.settings_applications_outlined),\n              title: const Text('Androidアプリ設定', style: TextStyle(fontWeight: FontWeight.w700)),\n              subtitle: const Text('権限・バッテリー・アプリ情報をOS側で確認'),\n              trailing: const Icon(Icons.open_in_new_rounded),\n              onTap: () => NativeBridge.call('openAppSettings'),\n            ),\n          ),\n""", 1)

info_action = """          const SizedBox(height: 12),\n          _GlassCard(\n            child: ListTile(\n              leading: const Icon(Icons.settings_applications_outlined),\n              title: const Text('Androidアプリ情報'),\n              trailing: const Icon(Icons.open_in_new_rounded),\n              onTap: () => NativeBridge.call('openAppSettings'),\n            ),\n          ),\n"""
s = s.replace(info_action, '', 1)
p.write_text(s)

pub = Path('pubspec.yaml')
t = pub.read_text().replace('version: 0.6.0-alpha.3+21', 'version: 0.6.0-alpha.4+22')
pub.write_text(t)
