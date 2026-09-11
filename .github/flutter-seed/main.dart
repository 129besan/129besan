import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const AppLockoutApp());

class NativeBridge {
  static const _channel = MethodChannel('dev.besan.browserbrake/app');

  static Future<Map<String, dynamic>> map(String method, [Map<String, dynamic>? args]) async {
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
    const seed = Color(0xFF1769AA);
    final scheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.light,
      surface: const Color(0xFFF7FAFD),
    );
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AppLockout',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: Colors.transparent,
        cardTheme: const CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(24)),
          ),
        ),
        listTileTheme: const ListTileThemeData(
          contentPadding: EdgeInsets.symmetric(horizontal: 18, vertical: 4),
        ),
        navigationBarTheme: NavigationBarThemeData(
          height: 72,
          backgroundColor: scheme.surface.withValues(alpha: .96),
          indicatorColor: scheme.secondaryContainer,
        ),
      ),
      home: _view == null
          ? const AppBackground(child: Center(child: CircularProgressIndicator()))
          : switch (_view) {
              'brake' => BrakeView(initial: _initial),
              'unlock' => UnlockView(initial: _initial),
              _ => const HomeShell(),
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
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int index = 0;

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
        body: SafeArea(child: IndexedStack(index: index, children: pages)),
        bottomNavigationBar: NavigationBar(
          selectedIndex: index,
          onDestinationSelected: (value) => setState(() => index = value),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'ホーム'),
            NavigationDestination(icon: Icon(Icons.insights_outlined), selectedIcon: Icon(Icons.insights), label: '記録'),
            NavigationDestination(icon: Icon(Icons.tune_outlined), selectedIcon: Icon(Icons.tune), label: '設定'),
            NavigationDestination(icon: Icon(Icons.info_outline), selectedIcon: Icon(Icons.info), label: 'Info'),
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
  bool loading = true;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
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

  @override
  Widget build(BuildContext context) => RefreshIndicator(
        onRefresh: refresh,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
          children: [
            Text('AppLockout', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, color: const Color(0xFF08345D))),
            const SizedBox(height: 4),
            Text('必要なときだけ、意識して使う。', style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: const Color(0xFF174C70))),
            const SizedBox(height: 22),
            if (loading)
              const Center(child: Padding(padding: EdgeInsets.all(32), child: CircularProgressIndicator()))
            else if (rules.isEmpty)
              const _GlassCard(child: _EmptyRules())
            else ...[
              Text('制限', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700, color: const Color(0xFF0D3A61))),
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
    return _GlassCard(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 14, 12, 14),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(color: Theme.of(context).colorScheme.secondaryContainer, shape: BoxShape.circle),
              child: Icon(active ? Icons.hourglass_bottom_rounded : Icons.shield_outlined),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(rule['name'] as String? ?? '制限', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                const SizedBox(height: 2),
                Text(active ? _stateLabel(state) : (enabled ? '有効' : '無効'), style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
              ]),
            ),
            Switch(
              value: enabled,
              onChanged: (value) async {
                await NativeBridge.call('setRuleEnabled', {'id': rule['id'], 'enabled': value});
                await onChanged();
              },
            ),
          ],
        ),
      ),
    );
  }

  String _stateLabel(String value) => switch (value) {
        'CHALLENGING' => '解除条件を進行中',
        'READY' => '利用時間を選択できます',
        'SESSION' => '利用中',
        'RECOVERY' => '休憩中',
        _ => value,
      };
}

class RecordsPage extends StatefulWidget {
  const RecordsPage({super.key});
  @override
  State<RecordsPage> createState() => _RecordsPageState();
}

class _RecordsPageState extends State<RecordsPage> {
  List<Map<String, dynamic>> records = const [];
  @override
  void initState() {
    super.initState();
    NativeBridge.list('getRecords').then((value) {
      if (mounted) setState(() => records = value);
    }).catchError((_) {});
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
        children: [
          _pageTitle(context, '記録', '使わなかった時間ではなく、意図して選べた日を中心に。'),
          const SizedBox(height: 18),
          _GlassCard(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: records.isEmpty
                  ? const Text('記録はまだありません。')
                  : Column(
                      children: records.take(14).map((r) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(r['commitmentBroken'] == true ? Icons.close_rounded : Icons.check_circle_outline),
                        title: Text(r['label'] as String? ?? ''),
                        trailing: Text('${r['sessions'] ?? 0} 回'),
                      )).toList(),
                    ),
            ),
          ),
        ],
      );
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
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

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 28),
        children: [
          _pageTitle(context, '設定', '権限、動作チェック、Android側の設定をまとめています。'),
          const SizedBox(height: 18),
          _GlassCard(
            child: Column(children: [
              _settingTile(Icons.accessibility_new_rounded, 'Accessibility', health['accessibility'] == true ? '有効' : '要設定', () async {
                await NativeBridge.call('openAccessibilitySettings');
              }),
              const Divider(height: 1),
              _settingTile(Icons.notifications_outlined, '通知', health['notifications'] == true ? '許可済み' : '確認', () async {
                await NativeBridge.call('openNotificationSettings');
              }),
              const Divider(height: 1),
              _settingTile(Icons.battery_saver_outlined, 'バッテリー設定', 'Android設定を開く', () async {
                await NativeBridge.call('openBatterySettings');
              }),
            ]),
          ),
          const SizedBox(height: 14),
          _GlassCard(
            child: ListTile(
              leading: const Icon(Icons.fact_check_outlined),
              title: const Text('動作チェック', style: TextStyle(fontWeight: FontWeight.w700)),
              subtitle: const Text('制限エンジンと権限状態を再確認します'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: refresh,
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
          _pageTitle(context, 'Info', 'AppLockoutについての情報。'),
          const SizedBox(height: 18),
          const _GlassCard(child: Column(children: [
            _InfoTile(icon: Icons.psychology_alt_outlined, title: 'AppLockoutについて', text: '反射的なアプリ起動の前に、短い選び直しの時間をつくるためのアプリです。'),
            Divider(height: 1),
            _InfoTile(icon: Icons.lock_outline_rounded, title: 'プライバシー', text: '制限設定や利用記録は端末内に保存します。Accessibilityは前面アプリの検知に使います。'),
            Divider(height: 1),
            _InfoTile(icon: Icons.code_rounded, title: 'Flutter移行版', text: 'UIはFlutter Material 3、Androidの制限エンジンはネイティブ実装です。'),
          ])),
        ],
      );
}

class BrakeView extends StatelessWidget {
  const BrakeView({super.key, required this.initial});
  final Map<String, dynamic> initial;

  @override
  Widget build(BuildContext context) {
    final fullLock = initial['fullLock'] == true;
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Icon(fullLock ? Icons.lock_rounded : Icons.hourglass_bottom_rounded, size: 64, color: const Color(0xFF08345D)),
                const SizedBox(height: 24),
                Text(initial['name'] as String? ?? 'AppLockout', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, color: const Color(0xFF08345D))),
                const SizedBox(height: 10),
                Text(fullLock ? 'この制限は完全ロックです。' : '解除条件を満たしてから、本当に今使うか選び直します。', textAlign: TextAlign.center),
                const SizedBox(height: 28),
                if (!fullLock)
                  FilledButton.icon(onPressed: () => NativeBridge.call('openUnlock'), icon: const Icon(Icons.arrow_forward_rounded), label: const Text('利用時間を選ぶ')),
                const SizedBox(height: 10),
                OutlinedButton(onPressed: () => NativeBridge.call('declineGate'), child: Text(fullLock ? 'ホームへ戻る' : '今回はやめる')),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class UnlockView extends StatelessWidget {
  const UnlockView({super.key, required this.initial});
  final Map<String, dynamic> initial;

  @override
  Widget build(BuildContext context) {
    final options = (initial['options'] as List? ?? const [300000, 600000, 900000]).cast<num>();
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const Spacer(),
              Text('今回は何分使いますか？', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, color: const Color(0xFF08345D))),
              const SizedBox(height: 8),
              Text(initial['name'] as String? ?? '', textAlign: TextAlign.center),
              const SizedBox(height: 28),
              for (final ms in options) ...[
                FilledButton(
                  onPressed: () => NativeBridge.call('startSession', {'usageMs': ms.toInt()}),
                  child: Padding(padding: const EdgeInsets.symmetric(vertical: 5), child: Text('${(ms / 60000).round()} 分')),
                ),
                const SizedBox(height: 10),
              ],
              TextButton(onPressed: () => NativeBridge.call('declineReady'), child: const Text('今回はやめる')),
              const Spacer(),
            ]),
          ),
        ),
      ),
    );
  }
}

class _GlassCard extends StatelessWidget {
  const _GlassCard({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Card(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: .91),
        child: child,
      );
}

class _EmptyRules extends StatelessWidget {
  const _EmptyRules();
  @override
  Widget build(BuildContext context) => const Padding(
        padding: EdgeInsets.all(22),
        child: Column(children: [
          Icon(Icons.shield_outlined, size: 42),
          SizedBox(height: 12),
          Text('制限がありません', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 17)),
          SizedBox(height: 4),
          Text('既存のAndroid版から更新した場合は設定がそのまま引き継がれます。', textAlign: TextAlign.center),
        ]),
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

Widget _pageTitle(BuildContext context, String title, String subtitle) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800, color: const Color(0xFF08345D))),
        const SizedBox(height: 4),
        Text(subtitle, style: const TextStyle(color: Color(0xFF174C70))),
      ],
    );

Widget _settingTile(IconData icon, String title, String subtitle, VoidCallback onTap) => ListTile(
      leading: Icon(icon),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
