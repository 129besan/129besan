import 'dart:convert';
import 'dart:typed_data';

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

  @override
  void initState() {
    super.initState();
    selected = {...widget.initial};
    _load();
  }

  Future<void> _load() async {
    final value = await CatalogBridge.list('getLaunchableApps');
    if (mounted) setState(() => apps = value);
  }

  @override
  Widget build(BuildContext context) {
    final filtered = apps.where((app) {
      final q = query.trim().toLowerCase();
      if (q.isEmpty) return true;
      return (app['label'] as String? ?? '').toLowerCase().contains(q) ||
          (app['package'] as String? ?? '').toLowerCase().contains(q);
    }).toList();
    return Scaffold(
      appBar: AppBar(
        title: Text('対象アプリ ${selected.length}'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, selected.toList()),
            child: const Text('完了'),
          ),
        ],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
          child: SearchBar(
            hintText: 'アプリ名を検索',
            leading: const Icon(Icons.search_rounded),
            onChanged: (value) => setState(() => query = value),
          ),
        ),
        Expanded(
          child: apps.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : ListView.builder(
                  itemCount: filtered.length,
                  itemBuilder: (context, index) {
                    final app = filtered[index];
                    final pkg = app['package'] as String? ?? '';
                    final active = selected.contains(pkg);
                    return CheckboxListTile(
                      value: active,
                      onChanged: (_) => setState(() {
                        if (active) {
                          selected.remove(pkg);
                        } else {
                          selected.add(pkg);
                        }
                      }),
                      secondary: _AppIcon(encoded: app['icon'] as String?),
                      title: Text(app['label'] as String? ?? pkg),
                      subtitle: Text(pkg, maxLines: 1, overflow: TextOverflow.ellipsis),
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
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('位置情報を取得できません。位置情報権限を確認してください。')),
      );
      return;
    }
    final name = TextEditingController(text: '新しい場所');
    double radius = 250;
    final added = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('現在地を場所として追加'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: name, decoration: const InputDecoration(labelText: '名前')),
            const SizedBox(height: 14),
            Row(children: [
              const Text('半径'),
              Expanded(
                child: Slider(
                  value: radius,
                  min: 100,
                  max: 1000,
                  divisions: 9,
                  label: '${radius.round()}m',
                  onChanged: (v) => setDialogState(() => radius = v),
                ),
              ),
              Text('${radius.round()}m'),
            ]),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('キャンセル')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('追加')),
          ],
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

  @override
  Widget build(BuildContext context) => Scaffold(
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
            ? const Center(child: Padding(
                padding: EdgeInsets.all(32),
                child: Text('登録済みの場所はありません。\n右下から現在地を追加できます。', textAlign: TextAlign.center),
              ))
            : ListView.separated(
                padding: const EdgeInsets.only(bottom: 100),
                itemCount: places.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final place = places[index];
                  final id = place['id'] as String? ?? '';
                  final active = selected.contains(id);
                  return CheckboxListTile(
                    value: active,
                    onChanged: (_) => setState(() {
                      if (active) selected.remove(id); else selected.add(id);
                    }),
                    secondary: const CircleAvatar(child: Icon(Icons.place_outlined)),
                    title: Text(place['name'] as String? ?? '場所'),
                    subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
                    secondary: const Icon(Icons.place_outlined),
                  );
                },
              ),
      );

class GuidedSetupPage extends StatefulWidget {
  const GuidedSetupPage({super.key});

  @override
  State<GuidedSetupPage> createState() => _GuidedSetupPageState();
}

class _GuidedSetupPageState extends State<GuidedSetupPage> {
  int step = 0;
  Set<String> packages = {};
  String challenge = 'phone';

  @override
  Widget build(BuildContext context) {
    final pages = [
      _GuideIntro(onNext: () => setState(() => step = 1)),
      _GuideApps(packages: packages, onChanged: (v) => setState(() => packages = v), onNext: () => setState(() => step = 2)),
      _GuideMethod(value: challenge, onChanged: (v) => setState(() => challenge = v), onDone: _finish),
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('ガイド形式で作成')),
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 320),
        transitionBuilder: (child, animation) => FadeTransition(opacity: animation, child: SlideTransition(position: Tween(begin: const Offset(.05, 0), end: Offset.zero).animate(animation), child: child)),
        child: KeyedSubtree(key: ValueKey(step), child: pages[step]),
      ),
    );
  }

  Future<void> _finish() async {
    final payload = <String, dynamic>{
      'name': '新しい制限',
      'enabled': true,
      'browsers': false,
      'sns': false,
      'customPackages': packages.toList(),
      'allPlaces': true,
      'placeIds': <String>[],
      'fullLock': false,
      'challengeWait': challenge == 'wait',
      'challengePhoneBreak': challenge == 'phone',
      'challengeWalk': challenge == 'walk',
      'challengeAll': true,
      'waitMs': 15000,
      'phoneBreakMs': 60000,
      'walkSteps': 50,
      'readyTimeoutMs': 0,
      'askSessionDuration': true,
      'defaultSessionUsageMs': 600000,
      'sessionWindowMs': 1800000,
      'dailyUsageLimitMs': 3600000,
      'dailySessionLimit': 5,
      'recoveryMs': 300000,
      'escalationMode': 'standard',
      'confirmed': true,
    };
    const channel = MethodChannel('dev.besan.browserbrake/app');
    await channel.invokeMethod('saveRule', payload);
    if (mounted) Navigator.pop(context, true);
  }
}

class _GuideIntro extends StatelessWidget {
  const _GuideIntro({required this.onNext});
  final VoidCallback onNext;
  @override
  Widget build(BuildContext context) => _GuideFrame(
    icon: Icons.touch_app_outlined,
    title: '反射的に開く前に、1回だけ選び直す',
    text: 'AppLockoutは完全に使えなくすることより、開く前に短い間をつくることを重視します。',
    child: FilledButton(onPressed: onNext, child: const Text('はじめる')),
  );
}

class _GuideApps extends StatelessWidget {
  const _GuideApps({required this.packages, required this.onChanged, required this.onNext});
  final Set<String> packages;
  final ValueChanged<Set<String>> onChanged;
  final VoidCallback onNext;
  @override
  Widget build(BuildContext context) => _GuideFrame(
    icon: Icons.apps_rounded,
    title: 'まず対象を選びます',
    text: '最初は1〜3個くらいに絞ると挙動を確認しやすいです。',
    child: Column(children: [
      OutlinedButton.icon(
        onPressed: () async {
          final result = await Navigator.push<List<String>>(context, MaterialPageRoute(builder: (_) => AppPickerPage(initial: packages)));
          if (result != null) onChanged(result.toSet());
        },
        icon: const Icon(Icons.apps_rounded),
        label: Text(packages.isEmpty ? 'アプリを選ぶ' : '${packages.length}個選択中'),
      ),
      const SizedBox(height: 12),
      FilledButton(onPressed: packages.isEmpty ? null : onNext, child: const Text('次へ')),
    ]),
  );
}

class _GuideMethod extends StatelessWidget {
  const _GuideMethod({required this.value, required this.onChanged, required this.onDone});
  final String value;
  final ValueChanged<String> onChanged;
  final VoidCallback onDone;
  @override
  Widget build(BuildContext context) => _GuideFrame(
    icon: Icons.self_improvement_rounded,
    title: '最初の解除条件',
    text: '後から細かく調整できます。まずは試しやすいものを1つ選びます。',
    child: Column(children: [
      RadioListTile(value: 'phone', groupValue: value, onChanged: (v) => onChanged(v!), title: const Text('スマホ休憩 1分')),
      RadioListTile(value: 'wait', groupValue: value, onChanged: (v) => onChanged(v!), title: const Text('待つ 15秒')),
      RadioListTile(value: 'walk', groupValue: value, onChanged: (v) => onChanged(v!), title: const Text('歩く 50歩')),
      const SizedBox(height: 8),
      FilledButton(onPressed: onDone, child: const Text('制限を作る')),
    ]),
  );
}

class _GuideFrame extends StatelessWidget {
  const _GuideFrame({required this.icon, required this.title, required this.text, required this.child});
  final IconData icon;
  final String title;
  final String text;
  final Widget child;
  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(28),
      child: Column(children: [
        CircleAvatar(radius: 42, child: Icon(icon, size: 38)),
        const SizedBox(height: 24),
        Text(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        Text(text, textAlign: TextAlign.center),
        const SizedBox(height: 28),
        child,
      ]),
    ),
  );
}
