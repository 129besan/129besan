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
        Expanded(
          child: apps.isEmpty
              ? const Center(child: CircularProgressIndicator())
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
                        secondary: const CircleAvatar(
                          backgroundColor: Color(0xFFD9EEF9),
                          child: Icon(Icons.place_outlined),
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

class GuidedSetupPage extends StatefulWidget {
  const GuidedSetupPage({super.key});
  @override
  State<GuidedSetupPage> createState() => _GuidedSetupPageState();
}

class _GuidedSetupPageState extends State<GuidedSetupPage> {
  final PageController _controller = PageController();
  int step = 0;
  Set<String> packages = {};
  String challenge = 'wait';
  bool saving = false;

  static const _pageCount = 4;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _next() async {
    if (step >= _pageCount - 1) {
      await _finish();
      return;
    }
    await _controller.nextPage(
      duration: const Duration(milliseconds: 420),
      curve: Curves.easeOutCubic,
    );
  }

  Future<void> _finish() async {
    if (saving) return;
    setState(() => saving = true);
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
      'challengePhoneBreak': false,
      'challengeWalk': challenge == 'walk',
      'challengeAll': true,
      'waitMs': challenge == 'wait' ? 15000 : 30000,
      'phoneBreakMs': 0,
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

  @override
  Widget build(BuildContext context) {
    final canContinue = step != 1 || packages.isNotEmpty;
    return Scaffold(
      backgroundColor: const Color(0xFFEDF7FC),
      body: SafeArea(
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 6, 12, 4),
            child: Row(children: [
              IconButton(
                onPressed: step == 0
                    ? () => Navigator.pop(context)
                    : () => _controller.previousPage(
                          duration: const Duration(milliseconds: 360),
                          curve: Curves.easeOutCubic,
                        ),
                icon: Icon(step == 0 ? Icons.close_rounded : Icons.arrow_back_rounded),
              ),
              const Spacer(),
              _ProgressDots(current: step, count: _pageCount),
              const Spacer(),
              const SizedBox(width: 48),
            ]),
          ),
          Expanded(
            child: PageView(
              controller: _controller,
              physics: const NeverScrollableScrollPhysics(),
              onPageChanged: (value) => setState(() => step = value),
              children: [
                const _GuideWelcome(),
                _GuideApps(
                  packages: packages,
                  onChanged: (value) => setState(() => packages = value),
                ),
                _GuideFriction(
                  value: challenge,
                  onChanged: (value) => setState(() => challenge = value),
                ),
                _GuideReview(packageCount: packages.length, challenge: challenge),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
            child: SizedBox(
              width: double.infinity,
              height: 56,
              child: FilledButton(
                onPressed: canContinue && !saving ? _next : null,
                child: saving
                    ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(step == _pageCount - 1 ? 'この設定ではじめる' : '続ける'),
              ),
            ),
          ),
        ]),
      ),
    );
  }
}

class _ProgressDots extends StatelessWidget {
  const _ProgressDots({required this.current, required this.count});
  final int current;
  final int count;
  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: List.generate(count, (index) {
          final active = index == current;
          final passed = index < current;
          return AnimatedContainer(
            duration: const Duration(milliseconds: 240),
            margin: const EdgeInsets.symmetric(horizontal: 3),
            height: 7,
            width: active ? 24 : 7,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(99),
              color: active || passed ? const Color(0xFF137CBD) : const Color(0xFFBCD4E3),
            ),
          );
        }),
      );
}

class _GuideWelcome extends StatelessWidget {
  const _GuideWelcome();
  @override
  Widget build(BuildContext context) => const _GuideCanvas(
        eyebrow: 'APPLOCKOUT',
        title: '無意識に開く前に、\n一度だけ選び直す。',
        text: '厳しく禁止するより、開くまでの流れに小さな摩擦をつくります。最初は1つだけ設定して試してみましょう。',
        visual: _OrbitVisual(),
      );
}

class _GuideApps extends StatelessWidget {
  const _GuideApps({required this.packages, required this.onChanged});
  final Set<String> packages;
  final ValueChanged<Set<String>> onChanged;

  @override
  Widget build(BuildContext context) => _GuideCanvas(
        eyebrow: '01  TARGET',
        title: 'つい開いてしまう\nアプリを選ぶ。',
        text: '最初は1〜3個がおすすめです。あとからいつでも変更できます。',
        visual: _SelectionVisual(count: packages.length),
        bottom: OutlinedButton.icon(
          onPressed: () async {
            final result = await Navigator.push<List<String>>(
              context,
              MaterialPageRoute(builder: (_) => AppPickerPage(initial: packages)),
            );
            if (result != null) onChanged(result.toSet());
          },
          icon: const Icon(Icons.apps_rounded),
          label: Text(packages.isEmpty ? 'アプリを選ぶ' : '${packages.length}個選択中・変更する'),
        ),
      );
}

class _GuideFriction extends StatelessWidget {
  const _GuideFriction({required this.value, required this.onChanged});
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) => _GuideCanvas(
        eyebrow: '02  FRICTION',
        title: '開く前に、何を\n挟みますか？',
        text: 'スマホ休憩は新規設定から外しました。まずはシンプルで挙動が読みやすい2種類に絞ります。',
        visual: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _ChallengeCard(
              selected: value == 'wait',
              icon: Icons.hourglass_top_rounded,
              title: '15秒 待つ',
              subtitle: '衝動が過ぎるまで、短く間を置く',
              onTap: () => onChanged('wait'),
            ),
            const SizedBox(height: 10),
            _ChallengeCard(
              selected: value == 'walk',
              icon: Icons.directions_walk_rounded,
              title: '50歩 歩く',
              subtitle: '身体を動かして、いったん画面から離れる',
              onTap: () => onChanged('walk'),
            ),
          ],
        ),
      );
}

class _GuideReview extends StatelessWidget {
  const _GuideReview({required this.packageCount, required this.challenge});
  final int packageCount;
  final String challenge;
  @override
  Widget build(BuildContext context) => _GuideCanvas(
        eyebrow: 'READY',
        title: 'これで準備できました。',
        text: '使ってみて合わなければ、強さや利用時間はあとから調整できます。',
        visual: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: .9),
            borderRadius: BorderRadius.circular(28),
          ),
          child: Column(children: [
            _ReviewRow(icon: Icons.apps_rounded, label: '対象', value: '$packageCount個のアプリ'),
            const Divider(height: 28),
            _ReviewRow(
              icon: challenge == 'walk' ? Icons.directions_walk_rounded : Icons.hourglass_top_rounded,
              label: '開く前',
              value: challenge == 'walk' ? '50歩 歩く' : '15秒 待つ',
            ),
            const Divider(height: 28),
            const _ReviewRow(icon: Icons.timer_outlined, label: '利用時間', value: '開くたびに選択'),
          ]),
        ),
      );
}

class _GuideCanvas extends StatelessWidget {
  const _GuideCanvas({
    required this.eyebrow,
    required this.title,
    required this.text,
    required this.visual,
    this.bottom,
  });
  final String eyebrow;
  final String title;
  final String text;
  final Widget visual;
  final Widget? bottom;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 14, 24, 20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(eyebrow,
              style: const TextStyle(
                letterSpacing: 1.4,
                fontWeight: FontWeight.w800,
                fontSize: 12,
                color: Color(0xFF137CBD),
              )),
          const SizedBox(height: 12),
          Text(title,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    height: 1.25,
                    fontWeight: FontWeight.w800,
                    color: const Color(0xFF102A43),
                  )),
          const SizedBox(height: 12),
          Text(text,
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    height: 1.6,
                    color: const Color(0xFF486581),
                  )),
          const SizedBox(height: 28),
          Center(child: visual),
          if (bottom != null) ...[
            const SizedBox(height: 24),
            SizedBox(width: double.infinity, child: bottom!),
          ],
        ]),
      );
}

class _OrbitVisual extends StatelessWidget {
  const _OrbitVisual();
  @override
  Widget build(BuildContext context) => SizedBox(
        height: 260,
        child: Stack(alignment: Alignment.center, children: [
          Container(
            width: 230,
            height: 230,
            decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFFD7F1FB)),
          ),
          Container(
            width: 164,
            height: 164,
            decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFF9ED9F2)),
          ),
          Container(
            width: 106,
            height: 106,
            decoration: const BoxDecoration(shape: BoxShape.circle, color: Color(0xFF137CBD)),
            child: const Icon(Icons.touch_app_rounded, size: 46, color: Colors.white),
          ),
          const Positioned(left: 26, top: 34, child: _MiniBubble(icon: Icons.chat_bubble_outline_rounded)),
          const Positioned(right: 22, bottom: 38, child: _MiniBubble(icon: Icons.public_rounded)),
        ]),
      );
}

class _MiniBubble extends StatelessWidget {
  const _MiniBubble({required this.icon});
  final IconData icon;
  @override
  Widget build(BuildContext context) => Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          boxShadow: const [BoxShadow(blurRadius: 18, color: Color(0x220B5A86), offset: Offset(0, 8))],
        ),
        child: Icon(icon, color: const Color(0xFF137CBD)),
      );
}

class _SelectionVisual extends StatelessWidget {
  const _SelectionVisual({required this.count});
  final int count;
  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: .86),
          borderRadius: BorderRadius.circular(28),
        ),
        child: Row(children: [
          const SizedBox(
            width: 58,
            height: 58,
            child: DecoratedBox(
              decoration: BoxDecoration(shape: BoxShape.circle, color: Color(0xFFD7F1FB)),
              child: Icon(Icons.apps_rounded, color: Color(0xFF137CBD)),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(count == 0 ? 'まだ選択していません' : '$count個のアプリを選択中',
                  style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
              const SizedBox(height: 4),
              const Text('SNS・ブラウザ・動画など、対象は自由に選べます。', style: TextStyle(color: Color(0xFF627D98))),
            ]),
          ),
        ]),
      );
}

class _ChallengeCard extends StatelessWidget {
  const _ChallengeCard({
    required this.selected,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final bool selected;
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: selected ? const Color(0xFFDDF2FC) : Colors.white.withValues(alpha: .88),
        borderRadius: BorderRadius.circular(22),
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              CircleAvatar(
                radius: 24,
                backgroundColor: selected ? const Color(0xFF137CBD) : const Color(0xFFE8F1F7),
                child: Icon(icon, color: selected ? Colors.white : const Color(0xFF486581)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  const SizedBox(height: 3),
                  Text(subtitle, style: const TextStyle(color: Color(0xFF627D98))),
                ]),
              ),
              Icon(selected ? Icons.check_circle_rounded : Icons.circle_outlined,
                  color: selected ? const Color(0xFF137CBD) : const Color(0xFF9FB3C8)),
            ]),
          ),
        ),
      );
}

class _ReviewRow extends StatelessWidget {
  const _ReviewRow({required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Row(children: [
        CircleAvatar(
          backgroundColor: const Color(0xFFE1F2FB),
          child: Icon(icon, color: const Color(0xFF137CBD)),
        ),
        const SizedBox(width: 12),
        Expanded(child: Text(label, style: const TextStyle(color: Color(0xFF627D98)))),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w800)),
      ]);
}
