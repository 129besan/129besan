import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'catalog_pages.dart';
import 'design_system.dart';
import 'onboarding_modern.dart';

void main() => runApp(const AppLockoutApp());

class NativeBridge {
  static const _channel = MethodChannel('dev.besan.browserbrake/app');

  static Future<Map<String, dynamic>> map(String method,
      [Map<String, dynamic>? args]) async {
    final value = await _channel.invokeMethod<dynamic>(method, args);
    return Map<String, dynamic>.from(value as Map? ?? const {});
  }

  static Future<List<Map<String, dynamic>>> list(String method) async {
    final value = await _channel.invokeMethod<dynamic>(method);
    return (value as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  static Future<void> call(String method, [Map<String, dynamic>? args]) =>
      _channel.invokeMethod<void>(method, args);
}

class AppLockoutApp extends StatefulWidget {
  const AppLockoutApp({super.key});
  @override
  State<AppLockoutApp> createState() => _AppLockoutAppState();
}

class _AppLockoutAppState extends State<AppLockoutApp> {
  String? _view;
  Map<String, dynamic> _initial = const {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final data = await NativeBridge.map('getInitialView');
      if (!mounted) return;
      setState(() {
        _view = data['view'] as String? ?? 'home';
        _initial = data;
      });
    } on PlatformException {
      if (!mounted) return;
      setState(() => _view = 'home');
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AppLockout',
      theme: buildAppTheme(),
      locale: const Locale('ja', 'JP'),
      supportedLocales: const [Locale('ja', 'JP')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      home: _view == null
          ? const AppBackground(
              child: Center(child: CircularProgressIndicator()))
          : switch (_view) {
              'brake' => BrakeView(initial: _initial),
              'unlock' => UnlockView(initial: _initial),
              _ => HomeShell(showOnboarding: _initial['shouldOnboard'] == true),
            },
    );
  }
}

class AppBackground extends StatelessWidget {
  const AppBackground({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            stops: [0, .17, .52, 1],
            colors: [
              Color(0xFFBDEBFA),
              Color(0xFF79C7EA),
              Color(0xFF3D86C4),
              Color(0xFF174C8A),
            ],
          ),
        ),
        child: child,
      );
}

class HomeShell extends StatefulWidget {
  const HomeShell({super.key, this.showOnboarding = false});
  final bool showOnboarding;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int index = 0;
  int generation = 0;
  bool onboardingPresented = false;

  @override
  void initState() {
    super.initState();
    if (widget.showOnboarding) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _showOnboarding());
    }
  }

  Future<void> _showOnboarding() async {
    if (!mounted || onboardingPresented) return;
    onboardingPresented = true;
    final completed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const GuidedSetupModernPage(), fullscreenDialog: true),
    );
    if (completed == true) {
      try {
        await NativeBridge.call('completeOnboarding');
      } catch (_) {}
      if (mounted) setState(() => generation++);
    }
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      const HomePage(),
      const RecordsPage(),
      const SettingsPage(),
      const InfoPage(),
    ];
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 180),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            child: KeyedSubtree(
              key: ValueKey('$index-$generation'),
              child: pages[index],
            ),
          ),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: index,
          onDestinationSelected: (value) => setState(() => index = value),
          destinations: const [
            NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home),
                label: 'ホーム'),
            NavigationDestination(
                icon: Icon(Icons.insights_outlined),
                selectedIcon: Icon(Icons.insights),
                label: '記録'),
            NavigationDestination(
                icon: Icon(Icons.tune_outlined),
                selectedIcon: Icon(Icons.tune),
                label: '設定'),
            NavigationDestination(
                icon: Icon(Icons.info_outline_rounded),
                selectedIcon: Icon(Icons.info_rounded),
                label: '情報'),
          ],
        ),
      ),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<Map<String, dynamic>> rules = const [];
  Map<String, dynamic> health = const {};
  bool loading = true;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
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

  Future<void> createRule() async {
    final draft = await NativeBridge.map('newRuleTemplate');
    if (!mounted) return;
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => RuleEditorPage(initial: draft, isNew: true)),
    );
    if (changed == true) refresh();
  }

  @override
  Widget build(BuildContext context) => RefreshIndicator(
        onRefresh: refresh,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
          children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('AppLockout',
                      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                          color: const Color(0xFF08345D))),
                  const SizedBox(height: 4),
                  Text('必要なときだけ、意識して使う。',
                      style: Theme.of(context)
                          .textTheme
                          .bodyLarge
                          ?.copyWith(color: const Color(0xFF174C70))),
                ]),
              ),
              IconButton.filledTonal(
                tooltip: '制限を追加',
                onPressed: createRule,
                icon: const Icon(Icons.add_rounded),
              ),
            ]),
            const SizedBox(height: 18),
            if (!loading && health['accessibility'] != true) ...[
              _EngineWarning(
                onTap: () async {
                  await NativeBridge.call('openAccessibilitySettings');
                },
              ),
              const SizedBox(height: 14),
            ],
            if (loading)
              const Center(
                  child: Padding(
                      padding: EdgeInsets.all(32),
                      child: CircularProgressIndicator()))
            else if (rules.isEmpty)
              _GlassCard(child: _EmptyRules(onCreate: createRule))
            else ...[
              Text('制限',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: const Color(0xFF0D3A61))),
              const SizedBox(height: 10),
              for (final rule in rules) ...[
                RuleCard(rule: rule, onChanged: refresh),
                const SizedBox(height: 10),
              ],
            ],
          ],
        ),
      );
}

class RuleCard extends StatelessWidget {
  const RuleCard({super.key, required this.rule, required this.onChanged});
  final Map<String, dynamic> rule;
  final Future<void> Function() onChanged;

  @override
  Widget build(BuildContext context) {
    final enabled = rule['enabled'] == true;
    final state = rule['state'] as String? ?? 'LOCKED';
    final active = state != 'LOCKED';
    final pausedUntil = (rule['pausedUntilMs'] as num?)?.toInt() ?? 0;
    final paused = pausedUntil > DateTime.now().millisecondsSinceEpoch;
    final usage = (rule['dailyUsageMs'] as num?)?.toInt() ?? 0;
    final sessions = (rule['dailySessions'] as num?)?.toInt() ?? 0;
    final status = !enabled
        ? '無効'
        : paused
            ? '一時停止中  •  今日 ${_minutes(usage)}分'
            : active
                ? _stateLabel(state)
                : '有効  •  今日 ${_minutes(usage)}分・$sessions回';
    return _GlassCard(
      child: InkWell(
        borderRadius: BorderRadius.circular(26),
        onTap: () async {
          final changed = await Navigator.of(context).push<bool>(
            MaterialPageRoute(
                builder: (_) => RuleEditorPage(initial: rule, isNew: false)),
          );
          if (changed == true) await onChanged();
        },
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 13, 10, 13),
          child: Row(children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.secondaryContainer,
                  shape: BoxShape.circle),
              child: Icon(active
                  ? Icons.hourglass_bottom_rounded
                  : Icons.shield_outlined),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(rule['name'] as String? ?? '制限',
                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                const SizedBox(height: 2),
                Text(
                  status,
                  style: TextStyle(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                      fontSize: 13),
                ),
              ]),
            ),
            if (state == 'SESSION')
              IconButton(
                tooltip: '今回の利用を終了',
                onPressed: () async {
                  await NativeBridge.call('endSession', {'id': rule['id']});
                  await onChanged();
                },
                icon: const Icon(Icons.stop_circle_outlined),
              ),
            const Icon(Icons.chevron_right_rounded, size: 22),
          ]),
        ),
      ),
    );
  }
}

class RuleEditorPage extends StatefulWidget {
  const RuleEditorPage({super.key, required this.initial, required this.isNew});
  final Map<String, dynamic> initial;
  final bool isNew;

  @override
  State<RuleEditorPage> createState() => _RuleEditorPageState();
}

class _RuleEditorPageState extends State<RuleEditorPage> {
  late Map<String, dynamic> draft;
  late TextEditingController nameController;
  bool saving = false;

  @override
  void initState() {
    super.initState();
    draft = Map<String, dynamic>.from(widget.initial);
    if (widget.isNew) {
      draft['challengePhoneBreak'] = false;
      draft['browsers'] = false;
      draft['sns'] = false;
    }
    nameController = TextEditingController(text: draft['name'] as String? ?? '');
  }

  @override
  void dispose() {
    nameController.dispose();
    super.dispose();
  }

  bool b(String key, [bool fallback = false]) => draft[key] as bool? ?? fallback;
  int n(String key, [int fallback = 0]) => (draft[key] as num?)?.toInt() ?? fallback;
  void setValue(String key, Object? value) => setState(() => draft[key] = value);

  Future<void> save({bool confirmed = false}) async {
    if (saving) return;
    draft['name'] = nameController.text.trim().isEmpty
        ? '制限'
        : nameController.text.trim();
    setState(() => saving = true);
    try {
      final payload = Map<String, dynamic>.from(draft)..['confirmed'] = confirmed;
      final result = await NativeBridge.map('saveRule', payload);
      final validationErrors = (result['validationErrors'] as List? ?? const [])
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
        Navigator.pop(context, true);
        return;
      }
      if (reasons.isNotEmpty) {
        final ok = await showDialog<bool>(
          context: context,
          barrierDismissible: false,
          builder: (_) => WeakeningConfirmDialog(reasons: reasons),
        );
        if (ok == true && mounted) {
          setState(() => saving = false);
          await save(confirmed: true);
          return;
        }
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> deleteRule() async {
    final ok = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const WeakeningConfirmDialog(
        reasons: ['制限そのものを削除する'],
      ),
    );
    if (ok != true) return;
    await NativeBridge.call('deleteRule', {'id': draft['id']});
    if (mounted) Navigator.pop(context, true);
  }

  Future<void> pause(int minutes) async {
    if (minutes > 0) {
      final seconds = minutes <= 15 ? 8 : 15;
      final ok = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (_) => PauseConfirmDialog(minutes: minutes, seconds: seconds),
      );
      if (ok != true) return;
    }
    await NativeBridge.call('pauseRule', {
      'id': draft['id'],
      'durationMs': minutes * 60 * 1000,
    });
    if (mounted) Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) {
    final fullLock = b('fullLock');
    final paused = n('pausedUntilMs') > DateTime.now().millisecondsSinceEpoch;
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          title: Text(widget.isNew ? '新しい制限' : '制限を編集'),
          actions: [
            TextButton(
              onPressed: saving ? null : save,
              child: saving
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('保存'),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 36),
          children: [
            _EditorCard(
              title: '基本',
              child: Column(children: [
                TextField(
                  controller: nameController,
                  decoration: const InputDecoration(labelText: '制限名'),
                ),
                const SizedBox(height: 10),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('有効'),
                  subtitle: const Text('この制限を動作させます'),
                  value: b('enabled', true),
                  onChanged: (v) => setValue('enabled', v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('完全ロック'),
                  subtitle: const Text('解除条件や利用時間を使わず、対象アプリを開かない'),
                  value: fullLock,
                  onChanged: (v) => setValue('fullLock', v),
                ),
              ]),
            ),
            const SizedBox(height: 12),
            _EditorCard(
              title: '対象アプリ',
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.apps_rounded),
                title: const Text('アプリを選ぶ'),
                subtitle: Text(
                  b('browsers') || b('sns')
                      ? '旧ブラウザ / SNS一括設定を使用中。開くと個別選択へ移行します。'
                      : '${(draft['customPackages'] as List? ?? const []).length}個選択中',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () async {
                  final current = (draft['customPackages'] as List? ?? const [])
                      .map((e) => e.toString())
                      .toSet();
                  if (b('browsers') || b('sns')) {
                    try {
                      final apps = await CatalogBridge.list('getLaunchableApps');
                      for (final app in apps) {
                        final category = app['category'] as String? ?? '';
                        final pkg = app['package'] as String? ?? '';
                        if (pkg.isEmpty) continue;
                        if (b('browsers') && category == 'browser') current.add(pkg);
                        if (b('sns') && category == 'social') current.add(pkg);
                      }
                    } catch (_) {}
                  }
                  if (!context.mounted) return;
                  final result = await Navigator.of(context).push<List<String>>(
                    MaterialPageRoute(builder: (_) => AppPickerPage(initial: current)),
                  );
                  if (result != null && mounted) {
                    setState(() {
                      draft['customPackages'] = result;
                      draft['browsers'] = false;
                      draft['sns'] = false;
                    });
                  }
                },
              ),
            ),
            const SizedBox(height: 12),
            _EditorCard(
              title: '場所',
              child: Column(children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('どこでも有効'),
                  subtitle: const Text('オフにすると、選んだ場所だけで有効になります'),
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
              const SizedBox(height: 12),
              _EditorCard(
                title: '解除条件',
                child: Column(children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('待つ'),
                    value: b('challengeWait'),
                    onChanged: (v) => setValue('challengeWait', v),
                  ),
                  if (b('challengeWait'))
                    _ChoiceRow(
                      label: '待つ時間',
                      value: n('waitMs', 30000),
                      options: const {15000: '15秒', 30000: '30秒', 60000: '1分', 120000: '2分'},
                      onChanged: (v) => setValue('waitMs', v),
                    ),
                  if (b('challengePhoneBreak')) ...[
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('スマホ休憩（旧方式）'),
                      subtitle: const Text('新規設定では使いません。オフにすると一覧から消えます。'),
                      value: true,
                      onChanged: (v) => setValue('challengePhoneBreak', v),
                    ),
                    _ChoiceRow(
                      label: '休憩時間',
                      value: n('phoneBreakMs', 180000),
                      options: const {60000: '1分', 180000: '3分', 300000: '5分', 600000: '10分'},
                      onChanged: (v) => setValue('phoneBreakMs', v),
                    ),
                  ],
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('歩く'),
                    value: b('challengeWalk'),
                    onChanged: (v) => setValue('challengeWalk', v),
                  ),
                  if (b('challengeWalk'))
                    _ChoiceRow(
                      label: '必要歩数',
                      value: n('walkSteps', 100),
                      options: const {50: '50歩', 100: '100歩', 200: '200歩', 500: '500歩'},
                      onChanged: (v) => setValue('walkSteps', v),
                    ),
                  if ([b('challengeWait'), b('challengeWalk'), b('challengePhoneBreak')]
                          .where((e) => e)
                          .length >=
                      2)
                    SegmentedButton<bool>(
                      segments: const [
                        ButtonSegment(value: true, label: Text('すべて満たす')),
                        ButtonSegment(value: false, label: Text('どれか1つ')),
                      ],
                      selected: {b('challengeAll', true)},
                      onSelectionChanged: (v) => setValue('challengeAll', v.first),
                    ),
                ]),
              ),
              const SizedBox(height: 12),
              _EditorCard(
                title: '利用セッション',
                child: Column(children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('利用時間を毎回選ぶ'),
                    subtitle: const Text('解除後に5分・10分・15分から選びます'),
                    value: b('askSessionDuration', true),
                    onChanged: (v) => setValue('askSessionDuration', v),
                  ),
                  if (!b('askSessionDuration', true))
                    _ChoiceRow(
                      label: '1回の利用時間',
                      value: n('defaultSessionUsageMs', 600000),
                      options: const {300000: '5分', 600000: '10分', 900000: '15分', 1200000: '20分'},
                      onChanged: (v) => setValue('defaultSessionUsageMs', v),
                    ),
                  _ChoiceRow(
                    label: '1日の通常利用時間',
                    value: n('dailyUsageLimitMs', 3600000),
                    options: const {1800000: '30分', 3600000: '60分', 7200000: '120分', 0: '上限なし'},
                    onChanged: (v) => setValue('dailyUsageLimitMs', v),
                  ),
                  _ChoiceRow(
                    label: '1日の通常利用回数',
                    value: n('dailySessionLimit', 5),
                    options: const {3: '3回', 5: '5回', 10: '10回', -1: '上限なし'},
                    onChanged: (v) => setValue('dailySessionLimit', v),
                  ),
                  _ChoiceRow(
                    label: '利用後の休憩',
                    value: n('recoveryMs', 300000),
                    options: const {0: 'なし', 60000: '1分', 300000: '5分', 600000: '10分'},
                    onChanged: (v) => setValue('recoveryMs', v),
                  ),
                ]),
              ),
            ],
            if (!widget.isNew) ...[
              const SizedBox(height: 12),
              _EditorCard(
                title: '一時停止',
                child: Wrap(spacing: 8, runSpacing: 8, children: [
                  OutlinedButton(onPressed: () => pause(15), child: const Text('15分')),
                  OutlinedButton(onPressed: () => pause(60), child: const Text('60分')),
                  if (paused)
                    OutlinedButton(onPressed: () => pause(0), child: const Text('再開')),
                ]),
              ),
              const SizedBox(height: 16),
              TextButton.icon(
                onPressed: deleteRule,
                icon: const Icon(Icons.delete_outline),
                label: const Text('この制限を削除'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class WeakeningConfirmDialog extends StatefulWidget {
  const WeakeningConfirmDialog({super.key, required this.reasons});
  final List<String> reasons;

  @override
  State<WeakeningConfirmDialog> createState() => _WeakeningConfirmDialogState();
}

class _WeakeningConfirmDialogState extends State<WeakeningConfirmDialog> {
  int remaining = 30;
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
        title: const Text('制限を弱める変更です'),
        content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('次の変更は、今日の記録に「制限を弱めた変更」として残ります。'),
          const SizedBox(height: 10),
          for (final reason in widget.reasons) Text('• $reason'),
          const SizedBox(height: 14),
          Text(remaining == 0 ? '変更できます。' : '変更まで $remaining 秒'),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('戻る')),
          FilledButton(
            onPressed: remaining == 0 ? () => Navigator.pop(context, true) : null,
            child: const Text('変更する'),
          ),
        ],
      );
}

class PauseConfirmDialog extends StatefulWidget {
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

class RecordsPage extends StatefulWidget {
  const RecordsPage({super.key});

  @override
  State<RecordsPage> createState() => _RecordsPageState();
}

class _RecordsPageState extends State<RecordsPage> {
  List<Map<String, dynamic>> records = const [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    try {
      final value = await NativeBridge.list('getRecords');
      if (mounted) {
        setState(() {
          records = value;
          loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final today = records.isEmpty ? const <String, dynamic>{} : records.first;
    final week = records.take(7).toList();
    final todayUsage = (today['usageMs'] as num?)?.toInt() ?? 0;
    final todaySessions = (today['sessions'] as num?)?.toInt() ?? 0;
    final weekUsage = week.fold<int>(
      0,
      (sum, record) => sum + ((record['usageMs'] as num?)?.toInt() ?? 0),
    );
    final recent = records.where(_recordHasMeaningfulData).take(14).toList();

    return RefreshIndicator(
      onRefresh: refresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
        children: [
          _pageTitle(context, '記録', '対象アプリを実際に使った時間と回数。'),
          const SizedBox(height: 18),
          if (loading)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: CircularProgressIndicator(),
              ),
            )
          else ...[
            Row(
              children: [
                Expanded(
                  child: _MetricCard(
                    label: '今日',
                    value: _usageLabel(todayUsage),
                    caption: '$todaySessions回利用',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _MetricCard(
                    label: '直近7日',
                    value: _usageLabel(weekUsage),
                    caption: '対象アプリの合計',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _GlassCard(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 18, 18, 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '7日間の利用',
                      style: Theme.of(context)
                          .textTheme
                          .titleMedium
                          ?.copyWith(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '高さは利用時間。赤い棒は通常利用の上限に達した日、橙の点は設定変更です。',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF61798B),
                          ),
                    ),
                    const SizedBox(height: 18),
                    _UsageWeekChart(records: week),
                    const SizedBox(height: 8),
                    Text(
                      '1日は午前4時に切り替わります',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF718797),
                          ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            _GlassCard(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '最近の記録',
                      style: Theme.of(context)
                          .textTheme
                          .titleMedium
                          ?.copyWith(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '利用があった日と、制限を一時停止・弱化した日だけ表示します。',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF61798B),
                          ),
                    ),
                    const SizedBox(height: 10),
                    if (recent.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 18),
                        child: Center(child: Text('まだ記録はありません。')),
                      )
                    else
                      for (var index = 0; index < recent.length; index++) ...[
                        _RecordRow(record: recent[index]),
                        if (index != recent.length - 1) const Divider(height: 1),
                      ],
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

bool _recordHasMeaningfulData(Map<String, dynamic> record) {
  final usage = (record['usageMs'] as num?)?.toInt() ?? 0;
  final sessions = (record['sessions'] as num?)?.toInt() ?? 0;
  return usage > 0 || sessions > 0 || record['commitmentBroken'] == true;
}

String _usageLabel(int milliseconds) {
  if (milliseconds <= 0) return '0分';
  final minutes = (milliseconds / 60000).ceil();
  if (minutes < 60) return '$minutes分';
  final hours = minutes ~/ 60;
  final remainder = minutes % 60;
  return remainder == 0 ? '$hours時間' : '$hours時間$remainder分';
}

class _UsageWeekChart extends StatelessWidget {
  const _UsageWeekChart({required this.records});
  final List<Map<String, dynamic>> records;

  @override
  Widget build(BuildContext context) {
    final days = records.reversed.toList();
    final maxUsage = days.fold<int>(
      1,
      (value, record) {
        final usage = (record['usageMs'] as num?)?.toInt() ?? 0;
        return usage > value ? usage : value;
      },
    );

    if (days.isEmpty) {
      return const SizedBox(height: 120, child: Center(child: Text('記録はまだありません。')));
    }

    return SizedBox(
      height: 152,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (final record in days)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 3),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Text(
                      _shortUsage((record['usageMs'] as num?)?.toInt() ?? 0),
                      maxLines: 1,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: const Color(0xFF61798B),
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 5),
                    Expanded(
                      child: Align(
                        alignment: Alignment.bottomCenter,
                        child: FractionallySizedBox(
                          heightFactor: (((record['usageMs'] as num?)?.toInt() ?? 0) / maxUsage)
                              .clamp(0.035, 1.0),
                          child: Container(
                            width: 22,
                            decoration: BoxDecoration(
                              color: record['overLimit'] == true
                                  ? const Color(0xFFE79A9A)
                                  : const Color(0xFF5FA8D3),
                              borderRadius: BorderRadius.circular(7),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      record['label'] as String? ?? '',
                      maxLines: 1,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: const Color(0xFF536D80),
                          ),
                    ),
                    if (record['commitmentBroken'] == true)
                      const Padding(
                        padding: EdgeInsets.only(top: 3),
                        child: SizedBox(
                          width: 5,
                          height: 5,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: Color(0xFFF0A84B),
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      )
                    else
                      const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  String _shortUsage(int milliseconds) {
    if (milliseconds <= 0) return '0';
    final minutes = (milliseconds / 60000).ceil();
    if (minutes < 60) return '$minutes';
    final hours = minutes / 60;
    return hours >= 10 ? '${hours.round()}h' : '${hours.toStringAsFixed(1)}h';
  }
}

class _RecordRow extends StatelessWidget {
  const _RecordRow({required this.record});
  final Map<String, dynamic> record;

  @override
  Widget build(BuildContext context) {
    final usage = (record['usageMs'] as num?)?.toInt() ?? 0;
    final sessions = (record['sessions'] as num?)?.toInt() ?? 0;
    final changed = record['commitmentBroken'] == true;
    final overLimit = record['overLimit'] == true;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: const Color(0xFFE8F3FA),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.schedule_rounded, size: 20, color: appBlue),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  record['label'] as String? ?? '',
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 2),
                Text(
                  '${_usageLabel(usage)}・$sessions回',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: const Color(0xFF61798B),
                      ),
                ),
              ],
            ),
          ),
          if (overLimit || changed)
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                if (overLimit)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFE7E7),
                      borderRadius: BorderRadius.circular(99),
                    ),
                    child: const Text(
                      '通常上限',
                      style: TextStyle(
                        color: Color(0xFF984848),
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                if (overLimit && changed) const SizedBox(height: 4),
                if (changed)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: const Color(0xFFFFF0D8),
                      borderRadius: BorderRadius.circular(99),
                    ),
                    child: const Text(
                      '設定変更',
                      style: TextStyle(
                        color: Color(0xFF805D17),
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> with WidgetsBindingObserver {
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

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
        children: [
          _pageTitle(context, '設定', '権限とAndroid側の動作設定。'),
          const SizedBox(height: 18),
          const SectionLabel('動作に必要な設定'),
          _GlassCard(
            child: Column(children: [
              _settingTile(Icons.accessibility_new_rounded, 'Accessibility',
                  health['accessibility'] == true ? '有効' : '要設定', () async {
                await NativeBridge.call('openAccessibilitySettings');
              }),
              const Divider(height: 1),
              _settingTile(Icons.notifications_outlined, '通知',
                  health['notifications'] == true ? '許可済み' : '確認', () async {
                await NativeBridge.call('openNotificationSettings');
              }),
              const Divider(height: 1),
              _settingTile(Icons.battery_saver_outlined, 'バッテリー最適化',
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
                    health['locationReady'] == true ? '常に許可されています' : '「常に許可」が必要です', () async {
                  await NativeBridge.call('openAppSettings');
                }),
              ],
            ]),
          ),
          const SizedBox(height: 14),
          const SectionLabel('制限'),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.auto_awesome_outlined),
              title: const Text('ガイド形式で新しい制限を作る',
                  style: TextStyle(fontWeight: FontWeight.w700)),
              subtitle: const Text('3ステップで、対象アプリと開く前のひと手間を決めます'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () async {
                await Navigator.of(context).push<bool>(
                  MaterialPageRoute(builder: (_) => const GuidedSetupModernPage()),
                );
              },
            ),
          ),
          const SizedBox(height: 10),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.settings_applications_outlined),
              title: const Text('Androidアプリ設定', style: TextStyle(fontWeight: FontWeight.w700)),
              subtitle: const Text('権限・バッテリー・アプリ情報をOS側で確認'),
              trailing: const Icon(Icons.open_in_new_rounded),
              onTap: () => NativeBridge.call('openAppSettings'),
            ),
          ),
        ],
      );
}

class InfoPage extends StatelessWidget {
  const InfoPage({super.key});

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
        children: [
          _pageTitle(context, '情報', 'AppLockoutが何をして、何をしないアプリなのか。'),
          const SizedBox(height: 18),
          const _GlassCard(
            child: Column(children: [
              _InfoTile(
                icon: Icons.psychology_alt_outlined,
                title: '考え方',
                text: 'アプリを禁止するより、反射的に開く直前へ短い間を入れて「今使う」を選び直せるようにします。'),
              Divider(height: 1),
              _InfoTile(
                icon: Icons.timelapse_rounded,
                title: '利用時間の数え方',
                text: '利用セッションでは、対象アプリが実際に前面にある時間を中心に消費します。通常利用の上限に達した後も、必要なときは強い解除条件を経て短時間だけ追加利用できます。'),
              Divider(height: 1),
              _InfoTile(
                icon: Icons.place_outlined,
                title: '場所による制限',
                text: '場所を指定したルールだけ位置情報を使います。場所を指定しないルールは位置情報なしで動作します。'),
            ]),
          ),
          const SizedBox(height: 12),
          const _GlassCard(
            child: Column(children: [
              _InfoTile(
                icon: Icons.lock_outline_rounded,
                title: 'データとプライバシー',
                text: '制限設定や利用記録は端末内に保存します。AppLockout独自のサーバーへ送信しません。'),
              Divider(height: 1),
              _InfoTile(
                icon: Icons.accessibility_new_rounded,
                title: 'Accessibility',
                text: '対象アプリが前面に来たことを検知して制限を開始するために使います。入力した文章の収集を目的にはしていません。'),
            ]),
          ),
          const SizedBox(height: 12),
          const _GlassCard(
            child: _InfoTile(
              icon: Icons.info_outline_rounded,
              title: 'Android上の制約',
              text: '強制停止や一部メーカー独自の省電力機能でAccessibilityが停止すると、制限も動作できません。ホームの警告や設定画面から状態を確認できます。',
            ),
          ),
          const SizedBox(height: 12),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.description_outlined),
              title: const Text('オープンソースライセンス', style: TextStyle(fontWeight: FontWeight.w700)),
              subtitle: const Text('利用しているOSSとライセンスを確認'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => showLicensePage(
                context: context,
                applicationName: 'AppLockout',
              ),
            ),
          ),
        ],
      );
}

class BrakeView extends StatefulWidget {
  const BrakeView({super.key, required this.initial});
  final Map<String, dynamic> initial;

  @override
  State<BrakeView> createState() => _BrakeViewState();
}

class _BrakeViewState extends State<BrakeView> {
  Timer? timer;
  Map<String, dynamic> status = const {};
  bool openingUnlock = false;

  bool get fullLock => widget.initial['fullLock'] == true;

  @override
  void initState() {
    super.initState();
    if (!fullLock) {
      _refresh();
      timer = Timer.periodic(const Duration(milliseconds: 450), (_) => _refresh());
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    if (!mounted || openingUnlock || fullLock) return;
    try {
      final next = await NativeBridge.map('getGateStatus');
      if (!mounted) return;
      setState(() => status = next);
      if (next['state'] == 'READY' && !openingUnlock) {
        openingUnlock = true;
        timer?.cancel();
        await NativeBridge.call('openUnlock');
      }
    } catch (_) {}
  }

  String _progressText() {
    if (status.isEmpty) return '解除条件を確認しています…';
    final pieces = <String>[];
    if (status['challengeWait'] == true) {
      final remaining = (status['waitRemainingMs'] as num?)?.toInt() ?? 0;
      pieces.add(remaining > 0 ? 'あと ${(remaining / 1000).ceil()} 秒' : '待機 完了');
    }
    if (status['challengeWalk'] == true) {
      final walked = (status['walkedSteps'] as num?)?.toInt() ?? 0;
      final required = (status['requiredSteps'] as num?)?.toInt() ?? 0;
      pieces.add('$walked / $required 歩');
    }
    if (status['challengePhoneBreak'] == true) {
      final remaining = (status['phoneRemainingMs'] as num?)?.toInt() ?? 0;
      pieces.add(remaining > 0
          ? '旧スマホ休憩 あと ${(remaining / 1000).ceil()} 秒'
          : 'スマホから離れると休憩開始');
    }
    return pieces.isEmpty ? 'まもなく準備できます' : pieces.join('  •  ');
  }

  IconData _challengeIcon() {
    if (status['challengeWalk'] == true) return Icons.directions_walk_rounded;
    if (status['challengeWait'] == true) return Icons.hourglass_top_rounded;
    return Icons.self_improvement_rounded;
  }

  @override
  Widget build(BuildContext context) {
    final all = status['challengeAll'] != false;
    final count = [
      status['challengeWait'] == true,
      status['challengeWalk'] == true,
      status['challengePhoneBreak'] == true,
    ].where((e) => e).length;
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Spacer(),
                _InterventionOrb(
                  icon: fullLock ? Icons.lock_rounded : _challengeIcon(),
                  locked: fullLock,
                ),
                const SizedBox(height: 30),
                Text(
                  widget.initial['name'] as String? ?? 'AppLockout',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w900,
                        color: const Color(0xFF08345D),
                      ),
                ),
                const SizedBox(height: 10),
                Text(
                  fullLock
                      ? 'この制限は完全ロックです。'
                      : count >= 2
                          ? (all ? '設定した条件をすべて満たすと利用時間を選べます。' : 'どれか1つの条件を満たすと利用時間を選べます。')
                          : '少しだけ間を置いてから、本当に今使うか選び直します。',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Color(0xFF345A75), height: 1.5),
                ),
                if (!fullLock) ...[
                  const SizedBox(height: 22),
                  SoftSurface(
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 15),
                    child: Row(children: [
                      Icon(_challengeIcon(), color: appBlue),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          _progressText(),
                          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                        ),
                      ),
                      const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    ]),
                  ),
                ],
                const SizedBox(height: 18),
                OutlinedButton(
                  onPressed: () => NativeBridge.call('declineGate'),
                  child: Text(fullLock ? 'ホームへ戻る' : '今回はやめる'),
                ),
                const Spacer(),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _InterventionOrb extends StatefulWidget {
  const _InterventionOrb({required this.icon, required this.locked});
  final IconData icon;
  final bool locked;

  @override
  State<_InterventionOrb> createState() => _InterventionOrbState();
}

class _InterventionOrbState extends State<_InterventionOrb>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2600),
  )..repeat(reverse: true);

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Center(
        child: AnimatedBuilder(
          animation: controller,
          builder: (context, child) {
            final t = Curves.easeInOutCubic.transform(controller.value);
            return Transform.scale(scale: .96 + .055 * t, child: child);
          },
          child: Container(
            width: 172,
            height: 172,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                center: const Alignment(-.38, -.42),
                radius: .92,
                colors: widget.locked
                    ? const [Color(0xFFF0F7FF), Color(0xFF76A7D8), Color(0xFF164C83)]
                    : const [Color(0xFFE4FAFF), Color(0xFF72C7ED), Color(0xFF1769AA)],
                stops: const [0, .48, 1],
              ),
              border: Border.all(color: Colors.white.withValues(alpha: .9), width: 3),
              boxShadow: const [
                BoxShadow(color: Color(0x3D0C4C79), blurRadius: 32, offset: Offset(0, 14)),
                BoxShadow(color: Color(0x66FFFFFF), blurRadius: 12, spreadRadius: -2),
              ],
            ),
            child: Icon(widget.icon, size: 58, color: const Color(0xFF08345D)),
          ),
        ),
      );
}

class UnlockView extends StatelessWidget {
  const UnlockView({super.key, required this.initial});
  final Map<String, dynamic> initial;

  @override
  Widget build(BuildContext context) {
    final options =
        (initial['options'] as List? ?? const [300000, 600000, 900000]).cast<num>();
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Spacer(),
              Text('今回は何分使いますか？',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                      color: const Color(0xFF08345D))),
              const SizedBox(height: 8),
              Text(initial['name'] as String? ?? '', textAlign: TextAlign.center),
              const SizedBox(height: 28),
              for (final ms in options) ...[
                FilledButton(
                  onPressed: () =>
                      NativeBridge.call('startSession', {'usageMs': ms.toInt()}),
                  child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 5),
                      child: Text('${(ms / 60000).round()} 分')),
                ),
                const SizedBox(height: 10),
              ],
              TextButton(
                  onPressed: () => NativeBridge.call('declineReady'),
                  child: const Text('今回はやめる')),
              const Spacer(),
            ]),
          ),
        ),
      ),
    );
  }
}

class _EditorCard extends StatelessWidget {
  const _EditorCard({required this.title, required this.child});
  final String title;
  final Widget child;
  @override
  Widget build(BuildContext context) => _GlassCard(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title,
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 10),
            child,
          ]),
        ),
      );
}

class _ChoiceRow<T> extends StatelessWidget {
  const _ChoiceRow(
      {required this.label,
      required this.value,
      required this.options,
      required this.onChanged});
  final String label;
  final T value;
  final Map<T, String> options;
  final ValueChanged<T> onChanged;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(children: [
          Expanded(child: Text(label)),
          DropdownButton<T>(
            value: options.containsKey(value) ? value : options.keys.first,
            items: options.entries
                .map((e) => DropdownMenuItem<T>(value: e.key, child: Text(e.value)))
                .toList(),
            onChanged: (v) {
              if (v != null) onChanged(v);
            },
          ),
        ]),
      );
}

class _GlassCard extends StatelessWidget {
  const _GlassCard({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => SoftSurface(child: child);
}

class _EngineWarning extends StatelessWidget {
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

class _EmptyRules extends StatelessWidget {
  const _EmptyRules({required this.onCreate});
  final VoidCallback onCreate;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(22),
        child: Column(children: [
          const Icon(Icons.shield_outlined, size: 42),
          const SizedBox(height: 12),
          const Text('制限がありません',
              style: TextStyle(fontWeight: FontWeight.w700, fontSize: 17)),
          const SizedBox(height: 4),
          const Text('まず1つ、対象と解除条件を決めます。', textAlign: TextAlign.center),
          const SizedBox(height: 14),
          FilledButton.icon(
              onPressed: onCreate,
              icon: const Icon(Icons.add_rounded),
              label: const Text('制限を作る')),
        ]),
      );
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.label, required this.value, required this.caption});
  final String label;
  final String value;
  final String caption;
  @override
  Widget build(BuildContext context) => _GlassCard(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: 4),
            Text(value, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
            Text(caption, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ]),
        ),
      );
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({required this.icon, required this.title, required this.text});
  final IconData icon;
  final String title;
  final String text;
  @override
  Widget build(BuildContext context) => ListTile(
        leading: Icon(icon),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text(text),
      );
}

String _stateLabel(String value) => switch (value) {
      'CHALLENGING' => '解除条件を進行中',
      'READY' => '利用時間を選択できます',
      'SESSION' => '利用中',
      'RECOVERY' => '休憩中',
      _ => value,
    };

int _minutes(int ms) => (ms / 60000).round();

Widget _pageTitle(BuildContext context, String title, String subtitle) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800, color: const Color(0xFF08345D))),
        const SizedBox(height: 4),
        Text(subtitle, style: const TextStyle(color: Color(0xFF486581))),
      ],
    );

Widget _settingTile(
        IconData icon, String title, String subtitle, VoidCallback onTap) =>
    ListTile(
      leading: Icon(icon),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
