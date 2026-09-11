from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()
if "import 'design_system.dart';" not in s:
    s = s.replace("import 'catalog_pages.dart';", "import 'catalog_pages.dart';\nimport 'design_system.dart';\nimport 'onboarding_modern.dart';")

if "package:flutter_localizations/flutter_localizations.dart" not in s:
    s = s.replace("import 'package:flutter/services.dart';", "import 'package:flutter/services.dart';\nimport 'package:flutter_localizations/flutter_localizations.dart';")

if 'const seed = Color(0xFF1769AA);' in s:
    start = s.index('    const seed = Color(0xFF1769AA);')
    end = s.index('      home: _view == null', start)
    s = s[:start] + "    return MaterialApp(\n      debugShowCheckedModeBanner: false,\n      title: 'AppLockout',\n      theme: buildAppTheme(),\n" + s[end:]

locale_needle = "      theme: buildAppTheme(),\n"
if "locale: const Locale('ja', 'JP')" not in s:
    s = s.replace(locale_needle, locale_needle + "      locale: const Locale('ja', 'JP'),\n      supportedLocales: const [Locale('ja', 'JP')],\n      localizationsDelegates: GlobalMaterialLocalizations.delegates,\n", 1)

needle = "    draft = Map<String, dynamic>.from(widget.initial);\n"
if "if (widget.isNew) draft['challengePhoneBreak'] = false;" not in s:
    s = s.replace(needle, needle + "    if (widget.isNew) draft['challengePhoneBreak'] = false;\n", 1)

old = """                  SwitchListTile(\n                    contentPadding: EdgeInsets.zero,\n                    title: const Text('スマホ休憩'),\n                    value: b('challengePhoneBreak', true),\n                    onChanged: (v) => setValue('challengePhoneBreak', v),\n                  ),\n                  if (b('challengePhoneBreak'))\n                    _ChoiceRow(\n                      label: '休憩時間',\n                      value: n('phoneBreakMs', 180000),\n                      options: const {60000: '1分', 180000: '3分', 300000: '5分', 600000: '10分'},\n                      onChanged: (v) => setValue('phoneBreakMs', v),\n                    ),\n"""
new = """                  if (b('challengePhoneBreak'))\n                    ListTile(\n                      contentPadding: EdgeInsets.zero,\n                      leading: const Icon(Icons.history_rounded),\n                      title: const Text('旧「スマホ休憩」'),\n                      subtitle: const Text('既存ルールとの互換用です。新規では使用しません。'),\n                      trailing: TextButton(\n                        onPressed: () => setValue('challengePhoneBreak', false),\n                        child: const Text('外す'),\n                      ),\n                    ),\n"""
s = s.replace(old, new)
s = s.replace("[b('challengeWait'), b('challengePhoneBreak'), b('challengeWalk')]", "[b('challengeWait'), b('challengeWalk')]")
s = s.replace('MaterialPageRoute(builder: (_) => const GuidedSetupPage())', 'MaterialPageRoute(builder: (_) => const GuidedSetupModernPage())')
s = s.replace('4ステップで、対象と開く前の摩擦を決めます', '3ステップで、対象と開く前の摩擦を決めます')

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

heatmap_use_needle = """        const SizedBox(height: 12),\n        _GlassCard(\n          child: Padding(\n            padding: const EdgeInsets.all(18),\n            child: records.isEmpty\n"""
if '_AchievementGrid(records: records)' not in s:
    heatmap_use = """        const SizedBox(height: 12),\n        _GlassCard(\n          child: Padding(\n            padding: const EdgeInsets.all(18),\n            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [\n              Text('直近30日', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),\n              const SizedBox(height: 4),\n              Text('青は守れた日、赤は設定を弱めた日です。', style: Theme.of(context).textTheme.bodySmall?.copyWith(color: const Color(0xFF5C768A))),\n              const SizedBox(height: 14),\n              _AchievementGrid(records: records),\n            ]),\n          ),\n        ),\n        const SizedBox(height: 12),\n        _GlassCard(\n          child: Padding(\n            padding: const EdgeInsets.all(18),\n            child: records.isEmpty\n"""
    s = s.replace(heatmap_use_needle, heatmap_use, 1)

if 'class _AchievementGrid extends StatelessWidget' not in s:
    marker = 'class SettingsPage extends StatefulWidget {'
    grid_widget = """class _AchievementGrid extends StatelessWidget {\n  const _AchievementGrid({required this.records});\n  final List<Map<String, dynamic>> records;\n\n  @override\n  Widget build(BuildContext context) {\n    final days = records.take(30).toList().reversed.toList();\n    return GridView.builder(\n      shrinkWrap: true,\n      physics: const NeverScrollableScrollPhysics(),\n      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(\n        crossAxisCount: 10, crossAxisSpacing: 6, mainAxisSpacing: 6),\n      itemCount: 30,\n      itemBuilder: (context, index) {\n        final offset = 30 - days.length;\n        final record = index >= offset ? days[index - offset] : null;\n        final hasData = record?['hasData'] == true;\n        final broken = record?['commitmentBroken'] == true;\n        final color = !hasData\n            ? const Color(0xFFE8F0F5)\n            : broken\n                ? const Color(0xFFF5C9C9)\n                : const Color(0xFF70B9E5);\n        final label = record?['label'] as String? ?? '記録なし';\n        return Tooltip(\n          message: label,\n          child: AnimatedContainer(\n            duration: const Duration(milliseconds: 180),\n            decoration: BoxDecoration(\n              color: color,\n              borderRadius: BorderRadius.circular(7),\n              border: Border.all(color: Colors.white.withValues(alpha: .8)),\n            ),\n          ),\n        );\n      },\n    );\n  }\n}\n\n"""
    s = s.replace(marker, grid_widget + marker, 1)

p.write_text(s)

catalog = Path('lib/catalog_pages.dart')
c = catalog.read_text()
if "import 'design_system.dart';" not in c:
    c = c.replace("import 'package:flutter/services.dart';", "import 'package:flutter/services.dart';\n\nimport 'design_system.dart';")
c = c.replace("    return Scaffold(\n      appBar: AppBar(", "    return Scaffold(\n      backgroundColor: const Color(0xFFF4FAFE),\n      appBar: AppBar(\n        backgroundColor: Colors.transparent,\n        surfaceTintColor: Colors.transparent,", 1)
c = c.replace("  Widget build(BuildContext context) => Scaffold(\n        appBar: AppBar(", "  Widget build(BuildContext context) => Scaffold(\n        backgroundColor: const Color(0xFFF4FAFE),\n        appBar: AppBar(\n          backgroundColor: Colors.transparent,\n          surfaceTintColor: Colors.transparent,", 1)
catalog.write_text(c)

pub = Path('pubspec.yaml')
t = pub.read_text().replace('version: 0.6.0-alpha.3+21', 'version: 0.6.0-alpha.4+22')
if 'flutter_localizations:' not in t:
    t = t.replace("  flutter:\n    sdk: flutter\n", "  flutter:\n    sdk: flutter\n  flutter_localizations:\n    sdk: flutter\n", 1)
pub.write_text(t)
