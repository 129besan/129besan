from pathlib import Path

p = Path('flutter_app/lib/main.dart')
s = p.read_text()
if "import 'catalog_pages.dart';" not in s:
    s = s.replace("import 'package:flutter/services.dart';\n", "import 'package:flutter/services.dart';\n\nimport 'catalog_pages.dart';\n")

old = """                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('SNS'),
                  subtitle: const Text('主要なSNSアプリをまとめて対象にします'),
                  value: b('sns'),
                  onChanged: (v) => setValue('sns', v),
                ),
              ]),
            ),
            if (!fullLock) ...[
"""
new = """                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('SNS'),
                  subtitle: const Text('主要なSNSアプリをまとめて対象にします'),
                  value: b('sns'),
                  onChanged: (v) => setValue('sns', v),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.apps_rounded),
                  title: const Text('個別にアプリを選ぶ'),
                  subtitle: Text('${(draft['customPackages'] as List? ?? const []).length}個選択中'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () async {
                    final current = (draft['customPackages'] as List? ?? const [])
                        .map((e) => e.toString()).toSet();
                    final result = await Navigator.of(context).push<List<String>>(
                      MaterialPageRoute(builder: (_) => AppPickerPage(initial: current)),
                    );
                    if (result != null) setValue('customPackages', result);
                  },
                ),
              ]),
            ),
            const SizedBox(height: 12),
            _EditorCard(
              title: '場所',
              child: Column(children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('場所を指定しない'),
                  subtitle: const Text('どこにいてもこの制限を使います'),
                  value: b('allPlaces', true),
                  onChanged: (v) => setValue('allPlaces', v),
                ),
                if (!b('allPlaces', true))
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.place_outlined),
                    title: const Text('有効な場所を選ぶ'),
                    subtitle: Text('${(draft['placeIds'] as List? ?? const []).length}か所選択中'),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () async {
                      final current = (draft['placeIds'] as List? ?? const [])
                          .map((e) => e.toString()).toSet();
                      final result = await Navigator.of(context).push<List<String>>(
                        MaterialPageRoute(builder: (_) => PlacesPage(selected: current)),
                      );
                      if (result != null) setValue('placeIds', result);
                    },
                  ),
              ]),
            ),
            if (!fullLock) ...[
"""
if old not in s:
    raise SystemExit('target app integration needle not found')
s = s.replace(old, new, 1)

settings_needle = """          const SizedBox(height: 14),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.fact_check_outlined),
"""
settings_new = """          const SizedBox(height: 14),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.auto_awesome_outlined),
              title: const Text('ガイド形式で新しい制限を作る',
                  style: TextStyle(fontWeight: FontWeight.w700)),
              subtitle: const Text('対象アプリと解除条件だけ先に決めます'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () async {
                await Navigator.of(context).push<bool>(
                  MaterialPageRoute(builder: (_) => const GuidedSetupPage()),
                );
              },
            ),
          ),
          const SizedBox(height: 14),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.fact_check_outlined),
"""
if settings_needle not in s:
    raise SystemExit('settings integration needle not found')
s = s.replace(settings_needle, settings_new, 1)
p.write_text(s)

c = Path('flutter_app/lib/catalog_pages.dart')
cs = c.read_text()
cs = cs.replace("""                    secondary: const CircleAvatar(child: Icon(Icons.place_outlined)),
                    title: Text(place['name'] as String? ?? '場所'),
                    subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
                    secondary: const Icon(Icons.place_outlined),
""", """                    secondary: const CircleAvatar(child: Icon(Icons.place_outlined)),
                    title: Text(place['name'] as String? ?? '場所'),
                    subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
""")
cs = cs.replace("""    if (location == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('位置情報を取得できません。位置情報権限を確認してください。')),
      );
      return;
    }
""", """    if (location == null) {
      await CatalogBridge.call('requestLocationPermission');
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('位置情報を許可したら、もう一度「現在地を追加」を押してください。')),
      );
      return;
    }
""")
c.write_text(cs)
