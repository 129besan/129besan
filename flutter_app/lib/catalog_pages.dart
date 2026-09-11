import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
  String category = 'all';
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
      if (mounted) setState(() { apps = value; loading = false; });
    } catch (_) {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final filtered = apps.where((app) {
      final q = query.trim().toLowerCase();
      final categoryMatches = category == 'all' || app['category'] == category;
      if (!categoryMatches) return false;
      if (q.isEmpty) return true;
      return (app['label'] as String? ?? '').toLowerCase().contains(q) ||
          (app['package'] as String? ?? '').toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      backgroundColor: const Color(0xFFF4F9FC),
      appBar: AppBar(
        title: const Text('対象アプリ'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: FilledButton.tonal(
              onPressed: () => Navigator.pop(context, selected.toList()),
              child: Text('完了  ${selected.length}'),
            ),
          ),
        ],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: SearchBar(
            hintText: 'アプリ名を検索',
            elevation: const WidgetStatePropertyAll(0),
            backgroundColor: const WidgetStatePropertyAll(Colors.white),
            leading: const Icon(Icons.search_rounded),
            onChanged: (value) => setState(() => query = value),
          ),
        ),
        SizedBox(
          height: 42,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              _CategoryChip(label: 'すべて', value: 'all', current: category, onChanged: (v) => setState(() => category = v)),
              _CategoryChip(label: 'SNS', value: 'sns', current: category, onChanged: (v) => setState(() => category = v)),
              _CategoryChip(label: 'ブラウザ', value: 'browser', current: category, onChanged: (v) => setState(() => category = v)),
              _CategoryChip(label: 'その他', value: 'other', current: category, onChanged: (v) => setState(() => category = v)),
            ],
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: loading
              ? const Center(child: CircularProgressIndicator())
              : filtered.isEmpty
                  ? const _NoAppsState()
                  : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
                  itemCount: filtered.length,
                  itemBuilder: (context, index) {
                    final app = filtered[index];
                    final pkg = app['package'] as String? ?? '';
                    final active = selected.contains(pkg);
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Material(
                        color: active ? const Color(0xFFE1F2FB) : Colors.white,
                        borderRadius: BorderRadius.circular(20),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(20),
                          onTap: () => setState(() {
                            if (active) {
                              selected.remove(pkg);
                            } else {
                              selected.add(pkg);
                            }
                          }),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                            child: Row(children: [
                              _AppIcon(encoded: app['icon'] as String?),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(app['label'] as String? ?? pkg,
                                        style: const TextStyle(fontWeight: FontWeight.w700)),
                                    const SizedBox(height: 2),
                                    Text(pkg,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                              color: const Color(0xFF627D98),
                                            )),
                                  ],
                                ),
                              ),
                              AnimatedContainer(
                                duration: const Duration(milliseconds: 180),
                                width: 30,
                                height: 30,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: active ? const Color(0xFF137CBD) : const Color(0xFFF0F4F8),
                                ),
                                child: Icon(
                                  active ? Icons.check_rounded : Icons.add_rounded,
                                  size: 18,
                                  color: active ? Colors.white : const Color(0xFF486581),
                                ),
                              ),
                            ]),
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ]),
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
      borderRadius: BorderRadius.circular(13),
      child: SizedBox(
        width: 46,
        height: 46,
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
    final ageMs = DateTime.now().millisecondsSinceEpoch - ((location['time'] as num?)?.toInt() ?? 0);
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
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('やめる')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('このまま続ける')),
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
          padding: EdgeInsets.fromLTRB(20, 4, 20, 24 + MediaQuery.viewInsetsOf(context).bottom),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('現在地を登録', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 18),
            TextField(controller: name, decoration: const InputDecoration(labelText: '場所の名前')),
            const SizedBox(height: 20),
            Row(children: [
              const Text('有効範囲', style: TextStyle(fontWeight: FontWeight.w700)),
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
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('この場所を追加')),
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
        content: const Text('この場所を使っている制限では、次に設定を開いたとき場所を選び直してください。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('キャンセル')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('削除')),
        ],
      ),
    );
    if (ok != true) return;
    await CatalogBridge.call('deletePlace', {'id': id});
    selected.remove(id);
    await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF4F9FC),
        appBar: AppBar(
          title: const Text('場所'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, selected.toList()), child: const Text('完了')),
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
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
                        subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
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
            const Icon(Icons.location_on_outlined, size: 52, color: Color(0xFF137CBD)),
            const SizedBox(height: 14),
            Text('場所はまだありません', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            const Text('自宅や研究室など、制限を有効にしたい場所を現在地から登録できます。', textAlign: TextAlign.center),
          ]),
        ),
      );
}


class _CategoryChip extends StatelessWidget {
  const _CategoryChip({required this.label, required this.value, required this.current, required this.onChanged});
  final String label;
  final String value;
  final String current;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(right: 8),
        child: FilterChip(
          selected: current == value,
          label: Text(label),
          onSelected: (_) => onChanged(value),
          showCheckmark: false,
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
            Icon(Icons.search_off_rounded, size: 44, color: Color(0xFF627D98)),
            SizedBox(height: 10),
            Text('条件に合うアプリがありません', style: TextStyle(fontWeight: FontWeight.w700)),
          ]),
        ),
      );
}
