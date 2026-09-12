import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'design_system.dart';

class CatalogBridge {
  static const _channel = MethodChannel('dev.besan.browserbrake/catalog');

  static Future<List<Map<String, dynamic>>> list(String method) async {
    final value = await _channel.invokeMethod<dynamic>(method);
    return (value as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  static Future<Map<String, dynamic>?> map(String method,
      [Map<String, dynamic>? args]) async {
    final value = await _channel.invokeMethod<dynamic>(method, args);
    if (value == null) return null;
    return Map<String, dynamic>.from(value as Map);
  }

  static Future<void> call(String method, [Map<String, dynamic>? args]) =>
      _channel.invokeMethod<void>(method, args);
}

class _CategoryMeta {
  const _CategoryMeta(this.key, this.label, this.icon);
  final String key;
  final String label;
  final IconData icon;
}

const _categories = <_CategoryMeta>[
  _CategoryMeta('social', 'SNS・コミュニケーション', Icons.forum_outlined),
  _CategoryMeta('browser', 'ブラウザ', Icons.public_rounded),
  _CategoryMeta('video', '動画', Icons.ondemand_video_outlined),
  _CategoryMeta('game', 'ゲーム', Icons.sports_esports_outlined),
  _CategoryMeta('audio', '音楽・音声', Icons.headphones_outlined),
  _CategoryMeta('productivity', '仕事・勉強', Icons.work_outline_rounded),
  _CategoryMeta('news', 'ニュース', Icons.newspaper_outlined),
  _CategoryMeta('maps', '地図・移動', Icons.map_outlined),
  _CategoryMeta('image', '写真・画像', Icons.photo_outlined),
  _CategoryMeta('other', 'その他', Icons.apps_rounded),
];

class AppPickerPage extends StatefulWidget {
  const AppPickerPage({super.key, required this.initial});
  final Set<String> initial;

  @override
  State<AppPickerPage> createState() => _AppPickerPageState();
}

class _AppPickerPageState extends State<AppPickerPage> {
  List<Map<String, dynamic>> apps = const [];
  late Set<String> selected;
  String query = '';
  bool loading = true;

  @override
  void initState() {
    super.initState();
    selected = {...widget.initial};
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await CatalogBridge.list('getLaunchableApps');
      if (mounted) {
        setState(() {
          apps = value;
          loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => loading = false);
    }
  }

  void _toggle(String pkg) {
    setState(() {
      if (!selected.add(pkg)) selected.remove(pkg);
    });
  }

  void _toggleCategory(List<Map<String, dynamic>> categoryApps) {
    final packages = categoryApps
        .map((app) => app['package'] as String? ?? '')
        .where((pkg) => pkg.isNotEmpty)
        .toSet();
    final allSelected = packages.isNotEmpty && packages.every(selected.contains);
    setState(() {
      if (allSelected) {
        selected.removeAll(packages);
      } else {
        selected.addAll(packages);
      }
    });
  }

  List<Map<String, dynamic>> _searchResults() {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return const [];
    return apps.where((app) {
      return (app['label'] as String? ?? '').toLowerCase().contains(q) ||
          (app['package'] as String? ?? '').toLowerCase().contains(q);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final results = _searchResults();
    return Scaffold(
      backgroundColor: const Color(0xFFF6FAFC),
      appBar: AppBar(
        backgroundColor: const Color(0xFFF6FAFC),
        surfaceTintColor: Colors.transparent,
        title: const Text('対象アプリ'),
        actions: [
          if (selected.isNotEmpty)
            TextButton(
              onPressed: () => setState(selected.clear),
              child: const Text('全解除'),
            ),
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: FilledButton.tonal(
              onPressed: () => Navigator.pop(context, selected.toList()),
              child: Text('完了  ${selected.length}'),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: SearchBar(
              hintText: 'アプリ名を検索',
              elevation: const WidgetStatePropertyAll(0),
              backgroundColor: const WidgetStatePropertyAll(Colors.white),
              leading: const Icon(Icons.search_rounded),
              trailing: query.isEmpty
                  ? null
                  : [
                      IconButton(
                        tooltip: '検索を消す',
                        onPressed: () => setState(() => query = ''),
                        icon: const Icon(Icons.close_rounded),
                      ),
                    ],
              onChanged: (value) => setState(() => query = value),
            ),
          ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : query.trim().isNotEmpty
                    ? _SearchAppList(
                        apps: results,
                        selected: selected,
                        onToggle: _toggle,
                      )
                    : _GroupedAppList(
                        apps: apps,
                        selected: selected,
                        onToggle: _toggle,
                        onToggleCategory: _toggleCategory,
                      ),
          ),
        ],
      ),
    );
  }
}

class _GroupedAppList extends StatelessWidget {
  const _GroupedAppList({
    required this.apps,
    required this.selected,
    required this.onToggle,
    required this.onToggleCategory,
  });

  final List<Map<String, dynamic>> apps;
  final Set<String> selected;
  final ValueChanged<String> onToggle;
  final ValueChanged<List<Map<String, dynamic>>> onToggleCategory;

  @override
  Widget build(BuildContext context) {
    final sections = <Widget>[];
    for (final meta in _categories) {
      final categoryApps = apps
          .where((app) => (app['category'] as String? ?? 'other') == meta.key)
          .toList();
      if (categoryApps.isEmpty) continue;
      final selectedCount = categoryApps
          .where((app) => selected.contains(app['package']))
          .length;
      final allSelected = selectedCount == categoryApps.length;
      sections.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 5, 16, 4),
          child: Row(
            children: [
              Icon(meta.icon, size: 20, color: const Color(0xFF486A82)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  meta.label,
                  style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15),
                ),
              ),
              Text(
                '$selectedCount / ${categoryApps.length}',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: const Color(0xFF6B8395),
                    ),
              ),
              const SizedBox(width: 4),
              TextButton(
                onPressed: () => onToggleCategory(categoryApps),
                child: Text(allSelected ? '選択解除' : 'すべて選択'),
              ),
            ],
          ),
        ),
      );
      sections.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: SoftSurface(
            padding: const EdgeInsets.symmetric(vertical: 4),
            radius: 20,
            child: Column(
              children: [
                for (var index = 0; index < categoryApps.length; index++) ...[
                  _AppRow(
                    app: categoryApps[index],
                    active: selected.contains(categoryApps[index]['package']),
                    onTap: () => onToggle(categoryApps[index]['package'] as String? ?? ''),
                  ),
                  if (index != categoryApps.length - 1) const Divider(height: 1),
                ],
              ],
            ),
          ),
        ),
      );
    }
    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: sections.isEmpty ? const [_NoAppsState()] : sections,
    );
  }
}

class _SearchAppList extends StatelessWidget {
  const _SearchAppList({
    required this.apps,
    required this.selected,
    required this.onToggle,
  });

  final List<Map<String, dynamic>> apps;
  final Set<String> selected;
  final ValueChanged<String> onToggle;

  @override
  Widget build(BuildContext context) {
    if (apps.isEmpty) return const _NoAppsState();
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
      itemCount: apps.length,
      separatorBuilder: (context, index) => const SizedBox(height: 6),
      itemBuilder: (context, index) {
        final app = apps[index];
        final pkg = app['package'] as String? ?? '';
        return Material(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          child: _AppRow(
            app: app,
            active: selected.contains(pkg),
            onTap: () => onToggle(pkg),
          ),
        );
      },
    );
  }
}

class _AppRow extends StatelessWidget {
  const _AppRow({required this.app, required this.active, required this.onTap});

  final Map<String, dynamic> app;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final pkg = app['package'] as String? ?? '';
    return ListTile(
      onTap: pkg.isEmpty ? null : onTap,
      leading: _AppIcon(encoded: app['icon'] as String?),
      title: Text(
        app['label'] as String? ?? pkg,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
      trailing: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        width: 30,
        height: 30,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: active ? appBlue : const Color(0xFFF0F4F7),
        ),
        child: Icon(
          active ? Icons.check_rounded : Icons.add_rounded,
          size: 18,
          color: active ? Colors.white : const Color(0xFF587184),
        ),
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  const _AppIcon({required this.encoded});
  final String? encoded;

  @override
  Widget build(BuildContext context) {
    Uint8List? bytes;
    if (encoded != null && encoded!.isNotEmpty) {
      try {
        bytes = base64Decode(encoded!);
      } catch (_) {}
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 42,
        height: 42,
        child: bytes == null
            ? const ColoredBox(
                color: Color(0xFFE1EEF8),
                child: Icon(Icons.apps_rounded),
              )
            : Image.memory(bytes, fit: BoxFit.cover, gaplessPlayback: true),
      ),
    );
  }
}

class PlacesPage extends StatefulWidget {
  const PlacesPage({super.key, required this.selected});
  final Set<String> selected;

  @override
  State<PlacesPage> createState() => _PlacesPageState();
}

class _PlacesPageState extends State<PlacesPage> {
  List<Map<String, dynamic>> places = const [];
  late Set<String> selected;

  @override
  void initState() {
    super.initState();
    selected = {...widget.selected};
    _load();
  }

  Future<void> _load() async {
    final value = await CatalogBridge.list('getPlaces');
    if (mounted) setState(() => places = value);
  }

  Future<void> _addCurrent() async {
    final location = await CatalogBridge.map('getCurrentLocation');
    if (!mounted) return;
    if (location == null) {
      await CatalogBridge.call('requestLocationPermission');
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('位置情報を許可したら、もう一度追加してください。')),
      );
      return;
    }
    final ageMs = DateTime.now().millisecondsSinceEpoch -
        ((location['time'] as num?)?.toInt() ?? 0);
    final accuracy = (location['accuracy'] as num?)?.toDouble() ?? 9999;
    if (ageMs > 5 * 60 * 1000 || accuracy > 200) {
      final proceed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('現在地の精度が低いようです'),
          content: Text(ageMs > 5 * 60 * 1000
              ? '取得できた位置情報が古いため、登録位置がずれる可能性があります。屋外などで位置情報を更新してからの登録がおすすめです。'
              : '現在地の誤差が約 ${accuracy.round()}m あります。登録半径を広めにするか、位置情報が安定してから登録してください。'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('やめる')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('このまま続ける')),
          ],
        ),
      );
      if (proceed != true || !mounted) return;
    }
    final name = TextEditingController(text: '新しい場所');
    double radius = 250;
    final added = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => Padding(
          padding: EdgeInsets.fromLTRB(
              20, 4, 20, 24 + MediaQuery.viewInsetsOf(context).bottom),
          child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('現在地を登録',
                    style: Theme.of(context)
                        .textTheme
                        .titleLarge
                        ?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 18),
                TextField(
                    controller: name,
                    decoration: const InputDecoration(labelText: '場所の名前')),
                const SizedBox(height: 20),
                Row(children: [
                  const Text('有効範囲',
                      style: TextStyle(fontWeight: FontWeight.w700)),
                  const Spacer(),
                  Text('${radius.round()} m'),
                ]),
                Slider(
                  value: radius,
                  min: 100,
                  max: 1000,
                  divisions: 9,
                  onChanged: (v) => setDialogState(() => radius = v),
                ),
                const SizedBox(height: 8),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('この場所を追加')),
              ]),
        ),
      ),
    );
    if (added != true) return;
    final place = await CatalogBridge.map('addPlace', {
      'name': name.text.trim(),
      'lat': location['lat'],
      'lon': location['lon'],
      'radiusM': radius,
    });
    if (place != null) selected.add(place['id'] as String);
    await _load();
  }

  Future<void> _deletePlace(Map<String, dynamic> place) async {
    final id = place['id'] as String? ?? '';
    if (id.isEmpty) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('「${place['name'] ?? '場所'}」を削除しますか？'),
        content: const Text('使われていない場所だけ削除できます。'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('キャンセル')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('削除')),
        ],
      ),
    );
    if (ok != true) return;
    final result = await CatalogBridge.map('deletePlace', {'id': id});
    if (!mounted) return;
    if (result?['deleted'] != true) {
      final usedBy = (result?['usedBy'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('この場所は削除できません'),
          content: Text(usedBy.isEmpty
              ? '場所の削除に失敗しました。'
              : '次の制限で使用されています。先に制限側の場所設定を変更してください。\n\n${usedBy.map((e) => '・$e').join('\n')}'),
          actions: [
            FilledButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('確認'))
          ],
        ),
      );
      return;
    }
    selected.remove(id);
    await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF4F9FC),
        appBar: AppBar(
          title: const Text('場所'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, selected.toList()),
                child: const Text('完了')),
          ],
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _addCurrent,
          icon: const Icon(Icons.my_location_rounded),
          label: const Text('現在地を追加'),
        ),
        body: places.isEmpty
            ? const _EmptyPlaceState()
            : ListView.builder(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 100),
                itemCount: places.length,
                itemBuilder: (context, index) {
                  final place = places[index];
                  final id = place['id'] as String? ?? '';
                  final active = selected.contains(id);
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Material(
                      color: active ? const Color(0xFFE1F2FB) : Colors.white,
                      borderRadius: BorderRadius.circular(20),
                      child: CheckboxListTile(
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(20)),
                        value: active,
                        onChanged: (_) => setState(() {
                          if (active) {
                            selected.remove(id);
                          } else {
                            selected.add(id);
                          }
                        }),
                        secondary: IconButton(
                          tooltip: 'この場所を削除',
                          onPressed: () => _deletePlace(place),
                          icon: const Icon(Icons.delete_outline_rounded),
                        ),
                        title: Text(place['name'] as String? ?? '場所',
                            style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: Text(
                            '半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
                      ),
                    ),
                  );
                },
              ),
      );
}

class _EmptyPlaceState extends StatelessWidget {
  const _EmptyPlaceState();

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.location_on_outlined,
                size: 52, color: Color(0xFF137CBD)),
            const SizedBox(height: 14),
            Text('場所はまだありません',
                style: Theme.of(context)
                    .textTheme
                    .titleLarge
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            const Text('自宅や研究室など、制限を有効にしたい場所を現在地から登録できます。',
                textAlign: TextAlign.center),
          ]),
        ),
      );
}

class _NoAppsState extends StatelessWidget {
  const _NoAppsState();

  @override
  Widget build(BuildContext context) => const Center(
        child: Padding(
          padding: EdgeInsets.all(28),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.search_off_rounded,
                size: 44, color: Color(0xFF627D98)),
            SizedBox(height: 10),
            Text('条件に合うアプリがありません',
                style: TextStyle(fontWeight: FontWeight.w700)),
          ]),
        ),
      );
}
