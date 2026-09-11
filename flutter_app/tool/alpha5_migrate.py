from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing patch target: {label}')
    return text.replace(old, new, 1)

# ---------- Flutter main ----------
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace("label: 'Info'", "label: '情報'")
s = s.replace("_pageTitle(context, 'Info', 'AppLockoutについての情報。')", "_pageTitle(context, '情報', 'AppLockoutについての情報。')")

# Rebuild the active tab instead of keeping stale IndexedStack state forever.
old = """        body: SafeArea(child: IndexedStack(index: index, children: pages)),
"""
new = """        body: SafeArea(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 180),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            child: KeyedSubtree(key: ValueKey(index), child: pages[index]),
          ),
        ),
"""
s = replace_once(s, old, new, 'tab refresh')

# Home health state + refresh.
s = replace_once(
    s,
    """  List<Map<String, dynamic>> rules = const [];
  bool loading = true;
""",
    """  List<Map<String, dynamic>> rules = const [];
  Map<String, dynamic> health = const {};
  bool loading = true;
""",
    'home health field',
)
old = """  Future<void> refresh() async {
    try {
      final value = await NativeBridge.list('getRules');
      if (!mounted) return;
      setState(() {
        rules = value;
        loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => loading = false);
    }
  }
"""
new = """  Future<void> refresh() async {
    try {
      final value = await NativeBridge.list('getRules');
      Map<String, dynamic> currentHealth = const {};
      try {
        currentHealth = await NativeBridge.map('getHealth');
      } catch (_) {}
      if (!mounted) return;
      setState(() {
        rules = value;
        health = currentHealth;
        loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => loading = false);
    }
  }
"""
s = replace_once(s, old, new, 'home refresh')
needle = """            const SizedBox(height: 22),
            if (loading)
"""
replacement = """            const SizedBox(height: 18),
            if (!loading && health['accessibility'] != true) ...[
              _EngineWarning(
                onTap: () async {
                  await NativeBridge.call('openAccessibilitySettings');
                },
              ),
              const SizedBox(height: 14),
            ],
            if (loading)
"""
s = replace_once(s, needle, replacement, 'home warning')

# Home disable now respects Settings Protection wait.
old = """              onChanged: (value) async {
                await NativeBridge.call(
                    'setRuleEnabled', {'id': rule['id'], 'enabled': value});
                await onChanged();
              },
"""
new = """              onChanged: (value) async {
                if (!value) {
                  final ok = await showDialog<bool>(
                    context: context,
                    barrierDismissible: false,
                    builder: (_) => const WeakeningConfirmDialog(
                      reasons: ['制限を無効にする'],
                    ),
                  );
                  if (ok != true) return;
                }
                await NativeBridge.call(
                    'setRuleEnabled', {'id': rule['id'], 'enabled': value});
                await onChanged();
              },
"""
s = replace_once(s, old, new, 'protected home toggle')

# Rule save: surface validation and target conflicts before weakening confirmation.
needle = """      final reasons = (result['weakeningReasons'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      if (!mounted) return;
      if (result['saved'] == true) {
"""
replacement = """      final validationErrors = (result['validationErrors'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      final conflicts = (result['conflicts'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      final reasons = (result['weakeningReasons'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      if (!mounted) return;
      if (validationErrors.isNotEmpty) {
        await _showIssueDialog(
          context,
          title: '設定を確認してください',
          message: 'このままでは制限が正しく動きません。',
          items: validationErrors,
        );
        return;
      }
      if (conflicts.isNotEmpty) {
        await _showIssueDialog(
          context,
          title: '対象が重複しています',
          message: '同じ場所で複数の制限が同じアプリに当たると、どの制限を使うか曖昧になります。',
          items: conflicts.map((e) => '「$e」と対象が重複').toList(),
        );
        return;
      }
      if (result['saved'] == true) {
"""
s = replace_once(s, needle, replacement, 'save validation')

# Settings refresh on return from Android settings and show actual health.
s = replace_once(
    s,
    """class _SettingsPageState extends State<SettingsPage> {
  Map<String, dynamic> health = const {};
  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    final value = await NativeBridge.map('getHealth');
    if (mounted) setState(() => health = value);
  }
""",
    """class _SettingsPageState extends State<SettingsPage> with WidgetsBindingObserver {
  Map<String, dynamic> health = const {};
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) refresh();
  }

  Future<void> refresh() async {
    try {
      final value = await NativeBridge.map('getHealth');
      if (mounted) setState(() => health = value);
    } catch (_) {}
  }
""",
    'settings lifecycle',
)
old = """              _settingTile(Icons.battery_saver_outlined, 'バッテリー設定',
                  'Android設定を開く', () async {
                await NativeBridge.call('openBatterySettings');
              }),
"""
new = """              _settingTile(Icons.battery_saver_outlined, 'バッテリー最適化',
                  health['batteryUnrestricted'] == true ? '制限なし' : '最適化中・確認推奨', () async {
                await NativeBridge.call('openBatterySettings');
              }),
              if (health['needsActivityRecognition'] == true) ...[
                const Divider(height: 1),
                _settingTile(Icons.directions_walk_rounded, '身体活動',
                    health['activityRecognition'] == true ? '許可済み' : '歩数チャレンジに必要', () async {
                  await NativeBridge.call('requestActivityRecognition');
                }),
              ],
              if (health['needsLocation'] == true) ...[
                const Divider(height: 1),
                _settingTile(Icons.location_on_outlined, '位置情報',
                    health['location'] == true ? '許可済み' : '場所指定ルールに必要', () async {
                  await NativeBridge.call('openAppSettings');
                }),
              ],
"""
s = replace_once(s, old, new, 'settings health rows')

# SoftSurface should really be used consistently.
old = """class _GlassCard extends StatelessWidget {
  const _GlassCard({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Card(
        color: Colors.white.withValues(alpha: .86),
        child: child,
      );
}
"""
new = """class _GlassCard extends StatelessWidget {
  const _GlassCard({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => SoftSurface(child: child);
}
"""
s = replace_once(s, old, new, 'soft surface')

# Add small shared widgets/dialog helpers before EmptyRules.
marker = 'class _EmptyRules extends StatelessWidget {'
extra = """class _EngineWarning extends StatelessWidget {
  const _EngineWarning({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: const Color(0xFFFFF3D6),
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: onTap,
          child: const Padding(
            padding: EdgeInsets.all(15),
            child: Row(children: [
              Icon(Icons.warning_amber_rounded, color: Color(0xFF8A5A00)),
              SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('制限エンジンが停止しています',
                      style: TextStyle(fontWeight: FontWeight.w800)),
                  SizedBox(height: 2),
                  Text('Accessibilityを有効にすると制限が動作します。'),
                ]),
              ),
              Icon(Icons.chevron_right_rounded),
            ]),
          ),
        ),
      );
}

Future<void> _showIssueDialog(
  BuildContext context, {
  required String title,
  required String message,
  required List<String> items,
}) =>
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(message),
            const SizedBox(height: 12),
            for (final item in items) Padding(
              padding: const EdgeInsets.only(bottom: 5),
              child: Text('• $item'),
            ),
          ],
        ),
        actions: [FilledButton(onPressed: () => Navigator.pop(context), child: const Text('確認'))],
      ),
    );

"""
if 'class _EngineWarning extends StatelessWidget' not in s:
    s = s.replace(marker, extra + marker, 1)

p.write_text(s)

# ---------- Modern onboarding ----------
p = Path('lib/onboarding_modern.dart')
s = p.read_text()
s = replace_once(s, 'State<GuidedSetupModernPage> createState() => _GuidedSetupModernPageState();', 'State<GuidedSetupModernPage> createState() => _GuidedSetupModernPageState();', 'onboarding noop')
s = s.replace('class _GuidedSetupModernPageState extends State<GuidedSetupModernPage> {', 'class _GuidedSetupModernPageState extends State<GuidedSetupModernPage> with WidgetsBindingObserver {')
s = replace_once(
    s,
    """  int page = 0;
  Set<String> packages = {};
  String challenge = 'wait';

  @override
  void dispose() { controller.dispose(); super.dispose(); }
""",
    """  int page = 0;
  Set<String> packages = {};
  String challenge = 'wait';
  Map<String, dynamic> health = const {};
  bool saving = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadHealth();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _loadHealth();
  }

  Future<void> _loadHealth() async {
    const channel = MethodChannel('dev.besan.browserbrake/app');
    try {
      final value = await channel.invokeMethod<dynamic>('getHealth');
      if (mounted) setState(() => health = Map<String, dynamic>.from(value as Map? ?? const {}));
    } catch (_) {}
  }
""",
    'onboarding state',
)
s = s.replace("Text('${page + 1} / 3'", "Text('${page + 1} / 4'")
s = s.replace('List.generate(3, (i)', 'List.generate(4, (i)')
s = s.replace('i == 2 ? 0 : 7', 'i == 3 ? 0 : 7')
s = replace_once(
    s,
    """          _GuideAppsModern(packages: packages, onChanged: (v) => setState(() => packages = v), onNext: next),
          _GuideChallengeModern(value: challenge, onChanged: (v) => setState(() => challenge = v), onDone: _finish),
""",
    """          _GuideAppsModern(packages: packages, onChanged: (v) => setState(() => packages = v), onNext: next),
          _GuideChallengeModern(value: challenge, onChanged: (v) => setState(() => challenge = v), onNext: next),
          _GuideReadyModern(
            challenge: challenge,
            health: health,
            saving: saving,
            onRefresh: _loadHealth,
            onDone: _finish,
          ),
""",
    'onboarding pages',
)
# Robust save feedback.
old = """  Future<void> _finish() async {
    final payload = <String, dynamic>{
"""
new = """  Future<void> _finish() async {
    if (saving) return;
    setState(() => saving = true);
    final payload = <String, dynamic>{
"""
s = replace_once(s, old, new, 'onboarding saving')
old = """    const channel = MethodChannel('dev.besan.browserbrake/app');
    await channel.invokeMethod('saveRule', payload);
    if (mounted) Navigator.pop(context, true);
  }
"""
new = """    const channel = MethodChannel('dev.besan.browserbrake/app');
    try {
      final raw = await channel.invokeMethod<dynamic>('saveRule', payload);
      final result = Map<String, dynamic>.from(raw as Map? ?? const {});
      if (!mounted) return;
      if (result['saved'] == true) {
        Navigator.pop(context, true);
        return;
      }
      final validation = (result['validationErrors'] as List? ?? const []).map((e) => e.toString()).toList();
      final conflicts = (result['conflicts'] as List? ?? const []).map((e) => e.toString()).toList();
      final message = validation.isNotEmpty
          ? validation.join('\n')
          : conflicts.isNotEmpty
              ? '既存の制限「${conflicts.join('、')}」と対象が重複しています。'
              : '保存できませんでした。設定を確認してください。';
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('設定を確認してください'),
          content: Text(message),
          actions: [FilledButton(onPressed: () => Navigator.pop(context), child: const Text('確認'))],
        ),
      );
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }
"""
s = replace_once(s, old, new, 'onboarding save result')
s = s.replace('最初の設定は3ステップだけです。', '最初の設定は4ステップだけです。')
s = s.replace("description: 'スマホ休憩は新規設定から外しました。まずは挙動が明確な2種類に絞ります。',", "description: 'まずは挙動がわかりやすい2種類から選べます。あとから細かく調整できます。',")
s = s.replace('required this.onDone', 'required this.onNext')
s = s.replace('final VoidCallback onDone;', 'final VoidCallback onNext;')
s = s.replace("action: FilledButton.icon(onPressed: onDone, icon: const Icon(Icons.check_rounded), label: const Text('この設定で作る'))", "action: FilledButton.icon(onPressed: onNext, icon: const Icon(Icons.arrow_forward_rounded), label: const Text('次へ'))")

# Add final permission readiness step before GuideLayout.
marker = 'class _GuideLayout extends StatelessWidget {'
ready = r'''class _GuideReadyModern extends StatelessWidget {
  const _GuideReadyModern({
    required this.challenge,
    required this.health,
    required this.saving,
    required this.onRefresh,
    required this.onDone,
  });
  final String challenge;
  final Map<String, dynamic> health;
  final bool saving;
  final Future<void> Function() onRefresh;
  final VoidCallback onDone;

  Future<void> _call(String method) async {
    const channel = MethodChannel('dev.besan.browserbrake/app');
    await channel.invokeMethod<void>(method);
  }

  @override
  Widget build(BuildContext context) {
    final accessibility = health['accessibility'] == true;
    final activity = health['activityRecognition'] == true;
    final notifications = health['notifications'] == true;
    return _GuideLayout(
      eyebrow: 'STEP 3 · READY',
      title: '最後に、動作の準備を\n確認します。',
      description: 'Android側の権限が足りないと、制限を作れても実際には動きません。ここで状態を確認できます。',
      visual: Column(children: [
        _PermissionRow(
          icon: Icons.accessibility_new_rounded,
          title: 'Accessibility',
          subtitle: accessibility ? '準備できています' : '制限の検知に必要です',
          ready: accessibility,
          onTap: accessibility ? null : () => _call('openAccessibilitySettings'),
        ),
        if (challenge == 'walk') ...[
          const SizedBox(height: 10),
          _PermissionRow(
            icon: Icons.directions_walk_rounded,
            title: '身体活動',
            subtitle: activity ? '準備できています' : '歩数の取得に必要です',
            ready: activity,
            onTap: activity ? null : () async {
              await _call('requestActivityRecognition');
              await Future<void>.delayed(const Duration(milliseconds: 350));
              await onRefresh();
            },
          ),
        ],
        const SizedBox(height: 10),
        _PermissionRow(
          icon: Icons.notifications_none_rounded,
          title: '通知',
          subtitle: notifications ? '許可済み' : '進行状況の表示に推奨',
          ready: notifications,
          optional: true,
          onTap: notifications ? null : () => _call('openNotificationSettings'),
        ),
      ]),
      secondary: OutlinedButton.icon(
        onPressed: onRefresh,
        icon: const Icon(Icons.refresh_rounded),
        label: const Text('状態を再確認'),
      ),
      action: FilledButton.icon(
        onPressed: saving ? null : onDone,
        icon: saving
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.check_rounded),
        label: const Text('この設定で作る'),
      ),
    );
  }
}

class _PermissionRow extends StatelessWidget {
  const _PermissionRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.ready,
    this.optional = false,
    this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final bool ready;
  final bool optional;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white.withValues(alpha: .86),
        borderRadius: BorderRadius.circular(20),
        child: ListTile(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          onTap: onTap,
          leading: CircleAvatar(
            backgroundColor: ready ? const Color(0xFFDDF3E6) : const Color(0xFFFFF0D2),
            child: Icon(icon, color: ready ? const Color(0xFF26734D) : const Color(0xFF8A5A00)),
          ),
          title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          subtitle: Text(optional && !ready ? '$subtitle（任意）' : subtitle),
          trailing: Icon(
            ready ? Icons.check_circle_rounded : Icons.chevron_right_rounded,
            color: ready ? const Color(0xFF26734D) : null,
          ),
        ),
      );
}

'''
if 'class _GuideReadyModern extends StatelessWidget' not in s:
    s = s.replace(marker, ready + marker, 1)
p.write_text(s)

# ---------- Catalog pages ----------
p = Path('lib/catalog_pages.dart')
s = p.read_text()
# Remove obsolete duplicate onboarding implementation; modern guide lives in onboarding_modern.dart.
idx = s.find('class GuidedSetupPage extends StatefulWidget')
if idx >= 0:
    s = s[:idx].rstrip() + '\n'

s = replace_once(
    s,
    """  List<Map<String, dynamic>> apps = const [];
  late Set<String> selected;
  String query = '';
""",
    """  List<Map<String, dynamic>> apps = const [];
  late Set<String> selected;
  String query = '';
  String category = 'all';
  bool loading = true;
""",
    'picker state',
)
old = """  Future<void> _load() async {
    final value = await CatalogBridge.list('getLaunchableApps');
    if (mounted) setState(() => apps = value);
  }
"""
new = """  Future<void> _load() async {
    try {
      final value = await CatalogBridge.list('getLaunchableApps');
      if (mounted) setState(() { apps = value; loading = false; });
    } catch (_) {
      if (mounted) setState(() => loading = false);
    }
  }
"""
s = replace_once(s, old, new, 'picker load')
old = """      if (q.isEmpty) return true;
      return (app['label'] as String? ?? '').toLowerCase().contains(q) ||
          (app['package'] as String? ?? '').toLowerCase().contains(q);
"""
new = """      final categoryMatches = category == 'all' || app['category'] == category;
      if (!categoryMatches) return false;
      if (q.isEmpty) return true;
      return (app['label'] as String? ?? '').toLowerCase().contains(q) ||
          (app['package'] as String? ?? '').toLowerCase().contains(q);
"""
s = replace_once(s, old, new, 'picker filter')
needle = """        Expanded(
          child: apps.isEmpty
              ? const Center(child: CircularProgressIndicator())
              : ListView.builder(
"""
replacement = """        SizedBox(
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
"""
s = replace_once(s, needle, replacement, 'picker category UI')

# Warn about stale/inaccurate location before saving a place.
needle = """    final name = TextEditingController(text: '新しい場所');
    double radius = 250;
"""
replacement = """    final ageMs = DateTime.now().millisecondsSinceEpoch - ((location['time'] as num?)?.toInt() ?? 0);
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
"""
s = replace_once(s, needle, replacement, 'location quality')

# Place delete helper.
marker = """  @override
  Widget build(BuildContext context) => Scaffold(
"""
helper = """  Future<void> _deletePlace(Map<String, dynamic> place) async {
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

"""
# Insert only in Places state: locate the marker after _addCurrent block by using last occurrence before body.
pos = s.find(marker, s.find('class _PlacesPageState'))
if pos >= 0 and 'Future<void> _deletePlace' not in s:
    s = s[:pos] + helper + s[pos:]

old = """                        subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
                      ),
"""
new = """                        subtitle: Text('半径 ${((place['radiusM'] as num?)?.toDouble() ?? 0).round()}m'),
                        secondary: PopupMenuButton<String>(
                          tooltip: '場所の操作',
                          onSelected: (value) {
                            if (value == 'delete') _deletePlace(place);
                          },
                          itemBuilder: (context) => const [
                            PopupMenuItem(value: 'delete', child: Text('削除')),
                          ],
                          icon: const Icon(Icons.more_horiz_rounded),
                        ),
                      ),
"""
# CheckboxListTile already has secondary earlier. Replace that earlier secondary with leading-ish icon impossible because duplicate.
# Instead replace the existing secondary CircleAvatar with popup and move location icon into title row is too invasive.
# Keep a trailing delete IconButton by replacing secondary block.
existing_secondary = """                        secondary: const CircleAvatar(
                          backgroundColor: Color(0xFFD9EEF9),
                          child: Icon(Icons.place_outlined),
                        ),
"""
replacement_secondary = """                        secondary: IconButton(
                          tooltip: 'この場所を削除',
                          onPressed: () => _deletePlace(place),
                          icon: const Icon(Icons.delete_outline_rounded),
                        ),
"""
s = replace_once(s, existing_secondary, replacement_secondary, 'place delete button')

# Add picker helper widgets at EOF.
s += r'''

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
'''
p.write_text(s)

# ---------- Native app bridge ----------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = s.replace('import android.accessibilityservice.AccessibilityServiceInfo\n', 'import android.Manifest\nimport android.accessibilityservice.AccessibilityServiceInfo\n')
s = s.replace('import android.content.Intent\n', 'import android.content.Intent\nimport android.content.pm.PackageManager\n')
s = s.replace('import android.os.Build\n', 'import android.os.Build\nimport android.os.PowerManager\n')

s = replace_once(
    s,
    '                    "getRules" -> result.success(ruleMaps(activity))\n',
    '                    "getRules" -> result.success(ruleMaps(activity))\n                    "getRecords" -> result.success(recordMaps(activity))\n                    "getHealth" -> result.success(health(activity))\n',
    'bridge missing methods',
)
s = s.replace('"newRuleTemplate" -> result.success(ruleMap(BrowserRule(browsers = true, challengePhoneBreak = true)))', '"newRuleTemplate" -> result.success(ruleMap(BrowserRule(browsers = true, challengeWait = true, challengePhoneBreak = false)))')

old = """                        val candidate = ruleFromMap(args)
                        val before = RuleRepository.getRule(activity, candidate.id)
                        val reasons = before?.let { RuleRepository.weakeningReasons(it, candidate) }.orEmpty()
                        val confirmed = call.argument<Boolean>("confirmed") == true
                        if (reasons.isNotEmpty() && !confirmed) {
                            result.success(mapOf("saved" to false, "weakeningReasons" to reasons))
                        } else {
                            if (before != null && reasons.isNotEmpty()) {
                                RuleRepository.markCommitmentBreak(activity, candidate.id, "settings_weakened")
                            }
                            RuleRepository.saveRule(activity, candidate)
                            BrowserBlockService.requestRuntimeSync()
                            result.success(mapOf("saved" to true, "weakeningReasons" to reasons))
                        }
"""
new = """                        val candidate = ruleFromMap(args)
                        val validationErrors = validationErrors(candidate)
                        if (validationErrors.isNotEmpty()) {
                            result.success(mapOf("saved" to false, "validationErrors" to validationErrors))
                            return@setMethodCallHandler
                        }
                        val conflicts = RuleRepository.conflicts(activity, candidate)
                        if (conflicts.isNotEmpty()) {
                            result.success(mapOf("saved" to false, "conflicts" to conflicts))
                            return@setMethodCallHandler
                        }
                        val before = RuleRepository.getRule(activity, candidate.id)
                        val reasons = before?.let { RuleRepository.weakeningReasons(it, candidate) }.orEmpty()
                        val confirmed = call.argument<Boolean>("confirmed") == true
                        if (reasons.isNotEmpty() && !confirmed) {
                            result.success(mapOf("saved" to false, "weakeningReasons" to reasons))
                        } else {
                            if (before != null && reasons.isNotEmpty()) {
                                RuleRepository.markCommitmentBreak(activity, candidate.id, "settings_weakened")
                            }
                            RuleRepository.saveRule(activity, candidate)
                            BrowserBlockService.requestRuntimeSync()
                            result.success(mapOf("saved" to true, "weakeningReasons" to reasons))
                        }
"""
s = replace_once(s, old, new, 'bridge save validation')

needle = """                    "openAccessibilitySettings" -> {
"""
addition = """                    "requestActivityRecognition" -> {
                        if (Build.VERSION.SDK_INT >= 29 &&
                            activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 4902)
                        }
                        result.success(null)
                    }
"""
s = replace_once(s, needle, addition + needle, 'activity permission')

# Aggregate all rule histories, not only the first rule.
old_start = s.index('    private fun recordMaps(context: Context): List<Map<String, Any?>> {')
old_end = s.index('\n    private fun health(context: Context): Map<String, Any?> {', old_start)
new_record = """    private fun recordMaps(context: Context): List<Map<String, Any?>> {
        val rules = RuleRepository.getRules(context)
        if (rules.isEmpty()) return emptyList()
        val histories = rules.map { RuleRepository.historyRecords(context, it.id, 30) }
        return (0 until 30).mapNotNull { index ->
            val rows = histories.mapNotNull { it.getOrNull(index) }
            val first = rows.firstOrNull() ?: return@mapNotNull null
            mapOf(
                "dayKey" to first.dayKey,
                "label" to first.label,
                "usageMs" to rows.sumOf { it.usageMs },
                "sessions" to rows.sumOf { it.sessions },
                "hasData" to rows.any { it.hasData },
                "commitmentBroken" to rows.any { it.commitmentBroken }
            )
        }.reversed()
    }
"""
s = s[:old_start] + new_record + s[old_end:]

old_start = s.index('    private fun health(context: Context): Map<String, Any?> {')
old_end = s.index('\n    private fun accessibilityEnabled', old_start)
new_health = """    private fun health(context: Context): Map<String, Any?> {
        val notifications = if (Build.VERSION.SDK_INT >= 24) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.areNotificationsEnabled() == true
        } else true
        val activityRecognition = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val rules = RuleRepository.getRules(context)
        return mapOf(
            "accessibility" to accessibilityEnabled(context),
            "notifications" to notifications,
            "activityRecognition" to activityRecognition,
            "location" to location,
            "batteryUnrestricted" to batteryUnrestricted,
            "needsActivityRecognition" to rules.any { it.enabled && it.challengeWalk },
            "needsLocation" to rules.any { it.enabled && !it.allPlaces }
        )
    }

    private fun validationErrors(rule: BrowserRule): List<String> {
        val errors = mutableListOf<String>()
        if (!rule.browsers && !rule.sns && rule.customPackages.isEmpty()) {
            errors += "対象アプリを1つ以上選んでください"
        }
        if (!rule.allPlaces && rule.placeIds.isEmpty()) {
            errors += "場所を指定する場合は、有効な場所を1つ以上選んでください"
        }
        if (!rule.fullLock && !rule.challengeWait && !rule.challengePhoneBreak && !rule.challengeWalk) {
            errors += "完全ロックでない場合は、解除条件を1つ以上選んでください"
        }
        if (rule.challengeWait && rule.waitMs <= 0L) errors += "待つ時間を設定してください"
        if (rule.challengeWalk && rule.walkSteps <= 0) errors += "必要歩数を設定してください"
        return errors
    }
"""
s = s[:old_start] + new_health + s[old_end:]

# Do not begin a session if the target cannot be launched anymore.
old = """        RuleRuntimeStore.startSession(activity, id, usageMs)
        NotificationController.showSession(activity, id)
        BrowserBlockService.requestRuntimeSync()
        activity.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(it)
        }
        activity.finish()
"""
new = """        val launchIntent = activity.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) {
            RuleRuntimeStore.declineReady(activity, id)
            NotificationController.cancel(activity, id)
            BrowserBlockService.requestRuntimeSync()
            goHome(activity)
            return
        }
        RuleRuntimeStore.startSession(activity, id, usageMs)
        NotificationController.showSession(activity, id)
        BrowserBlockService.requestRuntimeSync()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(launchIntent)
        activity.finish()
"""
s = replace_once(s, old, new, 'session launch validation')
p.write_text(s)

# ---------- Native catalog bridge ----------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterCatalogBridge.kt')
s = p.read_text()
if 'dev.besan.browserbrake.rules.TargetGroupCatalog' not in s:
    s = s.replace('import io.flutter.embedding.android.FlutterActivity\n', 'import dev.besan.browserbrake.rules.TargetGroupCatalog\nimport io.flutter.embedding.android.FlutterActivity\n')
old = """        return pm.queryIntentActivities(intent, 0).asSequence().mapNotNull { info ->
"""
new = """        val browserPackages = TargetApps.browserPackages(context)
        return pm.queryIntentActivities(intent, 0).asSequence().mapNotNull { info ->
"""
s = replace_once(s, old, new, 'catalog browser set')
old = '            mapOf("package" to pkg, "label" to label, "icon" to icon)\n'
new = """            val category = when {
                TargetGroupCatalog.isSnsPackage(pkg) -> "sns"
                pkg in browserPackages -> "browser"
                else -> "other"
            }
            mapOf("package" to pkg, "label" to label, "icon" to icon, "category" to category)
"""
s = replace_once(s, old, new, 'catalog category')
p.write_text(s)

# ---------- Rule repository ----------
p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/RuleRepository.kt')
s = p.read_text()
s = s.replace('BrowserRule(name = name, browsers = false, challengePhoneBreak = true)', 'BrowserRule(name = name, browsers = false, challengeWait = true, challengePhoneBreak = false)')
old = """            .filter { other ->
                val direct = candidate.customPackages.intersect(other.customPackages).isNotEmpty()
                val browserGroup = candidate.browsers && other.browsers
                val snsGroup = candidate.sns && other.sns
                val candidateCustomHitsOtherBrowser =
                    other.browsers && candidate.customPackages.any { it in browserPkgs }
                val otherCustomHitsCandidateBrowser =
                    candidate.browsers && other.customPackages.any { it in browserPkgs }
                val candidateCustomHitsOtherSns =
                    other.sns && candidate.customPackages.any(TargetGroupCatalog::isSnsPackage)
                val otherCustomHitsCandidateSns =
                    candidate.sns && other.customPackages.any(TargetGroupCatalog::isSnsPackage)
                direct || browserGroup || snsGroup ||
                    candidateCustomHitsOtherBrowser || otherCustomHitsCandidateBrowser ||
                    candidateCustomHitsOtherSns || otherCustomHitsCandidateSns
            }
"""
new = """            .filter { other ->
                val direct = candidate.customPackages.intersect(other.customPackages).isNotEmpty()
                val browserGroup = candidate.browsers && other.browsers
                val snsGroup = candidate.sns && other.sns
                val candidateCustomHitsOtherBrowser =
                    other.browsers && candidate.customPackages.any { it in browserPkgs }
                val otherCustomHitsCandidateBrowser =
                    candidate.browsers && other.customPackages.any { it in browserPkgs }
                val candidateCustomHitsOtherSns =
                    other.sns && candidate.customPackages.any(TargetGroupCatalog::isSnsPackage)
                val otherCustomHitsCandidateSns =
                    candidate.sns && other.customPackages.any(TargetGroupCatalog::isSnsPackage)
                val targetOverlap = direct || browserGroup || snsGroup ||
                    candidateCustomHitsOtherBrowser || otherCustomHitsCandidateBrowser ||
                    candidateCustomHitsOtherSns || otherCustomHitsCandidateSns
                val contextOverlap = candidate.allPlaces || other.allPlaces ||
                    candidate.placeIds.intersect(other.placeIds).isNotEmpty()
                targetOverlap && contextOverlap
            }
"""
s = replace_once(s, old, new, 'context aware conflicts')
p.write_text(s)

# ---------- Accessibility runtime ----------
p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()
# Approximate location permission is enough for radius-based rules.
s = s.replace('if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;', 'if (!hasLocationPermission()) return;', 2)
marker = '    private void startPassiveLocationUpdates() {'
helper = """    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

"""
if 'private boolean hasLocationPermission()' not in s:
    s = s.replace(marker, helper + marker, 1)
old = """        BrowserRule runtimeMatchingRule = findActiveRuntimeRuleForPackage(pkg);
        BrowserRule durableMatchingRule = RuleRepository.findMatchingRule(this, pkg);

        updateSessionForeground(type, pkg, runtimeMatchingRule);
"""
new = """        BrowserRule runtimeMatchingRule = findActiveRuntimeRuleForPackage(pkg);
        refreshLastKnownLocation();
        BrowserRule durableMatchingRule = findContextMatchingRuleForPackage(pkg);

        updateSessionForeground(type, pkg, runtimeMatchingRule);
"""
s = replace_once(s, old, new, 'context matching selection')
# Remove the now redundant second refresh call right before context active check.
s = s.replace('        refreshLastKnownLocation();\n        if (!isContextActive(matchingRule)) {', '        if (!isContextActive(matchingRule)) {', 1)
marker = '    private BrowserRule findActiveRuntimeRuleForPackage(String pkg) {'
helper = """    private BrowserRule findContextMatchingRuleForPackage(String pkg) {
        if (pkg == null || pkg.isBlank()) return null;
        for (BrowserRule rule : RuleRepository.getRules(this)) {
            if (RuleRepository.isEffective(rule)
                    && TargetGroupCatalog.packageBelongs(this, rule, pkg)
                    && isRuleContextEligible(rule)) {
                return rule;
            }
        }
        return null;
    }

"""
if 'findContextMatchingRuleForPackage' not in s:
    s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# Version bump.
p = Path('pubspec.yaml')
s = p.read_text().replace('version: 0.6.0-alpha.4+22', 'version: 0.6.0-alpha.5+23')
p.write_text(s)
