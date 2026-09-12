import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';

import 'catalog_pages.dart';
import 'design_system.dart';

class GuidedSetupModernPage extends StatefulWidget {
  const GuidedSetupModernPage({super.key});

  @override
  State<GuidedSetupModernPage> createState() => _GuidedSetupModernPageState();
}

class _GuidedSetupModernPageState extends State<GuidedSetupModernPage>
    with WidgetsBindingObserver {
  static const _channel = MethodChannel('dev.besan.browserbrake/app');

  final PageController controller = PageController();
  int page = 0;
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
    try {
      final raw = await _channel.invokeMethod<dynamic>('getHealth');
      if (!mounted) return;
      setState(() => health = Map<String, dynamic>.from(raw as Map? ?? const {}));
    } catch (_) {}
  }

  Future<void> _next() => controller.nextPage(
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
      );

  Future<void> _back() => controller.previousPage(
        duration: const Duration(milliseconds: 320),
        curve: Curves.easeOutCubic,
      );

  Future<void> _chooseApps() async {
    final result = await Navigator.of(context).push<List<String>>(
      MaterialPageRoute(builder: (_) => AppPickerPage(initial: packages)),
    );
    if (result != null && mounted) setState(() => packages = result.toSet());
  }

  bool get _requiredReady {
    final accessibility = health['accessibility'] == true;
    final activity = health['activityRecognition'] == true;
    return accessibility && (challenge != 'walk' || activity);
  }

  String get _primaryLabel => switch (page) {
        0 => 'はじめる',
        1 => packages.isEmpty ? 'アプリを選ぶ' : '次へ',
        2 => '次へ',
        _ => 'AppLockoutをはじめる',
      };

  VoidCallback? get _primaryAction {
    if (saving) return null;
    return switch (page) {
      0 => _next,
      1 => packages.isEmpty ? _chooseApps : _next,
      2 => _next,
      _ => _requiredReady ? _finish : null,
    };
  }

  Future<void> _finish() async {
    if (saving || packages.isEmpty || !_requiredReady) return;
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
      'waitMs': challenge == 'wait' ? 30000 : 15000,
      'phoneBreakMs': 60000,
      'walkSteps': challenge == 'walk' ? 100 : 50,
      'readyTimeoutMs': 0,
      'askSessionDuration': true,
      'defaultSessionUsageMs': 600000,
      'sessionWindowMs': 1800000,
      'dailyUsageLimitMs': 3600000,
      'dailySessionLimit': 5,
      'recoveryMs': 300000,
      'escalationMode': 'none',
      'confirmed': true,
    };

    try {
      final raw = await _channel.invokeMethod<dynamic>('saveRule', payload);
      final result = Map<String, dynamic>.from(raw as Map? ?? const {});
      if (!mounted) return;
      if (result['saved'] == true) {
        Navigator.pop(context, true);
        return;
      }
      final validation = (result['validationErrors'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
      final conflicts = (result['conflicts'] as List? ?? const [])
          .map((e) => e.toString())
          .toList();
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
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('確認'),
            ),
          ],
        ),
      );
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Color(0xFFFBFDFF),
              Color(0xFFF0F8FD),
              Color(0xFFF8F5FF),
            ],
          ),
        ),
        child: SafeArea(
          child: Column(
            children: [
              _TopBar(
                page: page,
                onBack: page == 0 ? () => Navigator.pop(context) : _back,
              ),
              Expanded(
                child: PageView(
                  controller: controller,
                  physics: const NeverScrollableScrollPhysics(),
                  onPageChanged: (value) => setState(() => page = value),
                  children: [
                    const _WelcomePage(),
                    _AppsPage(
                      count: packages.length,
                      onChoose: _chooseApps,
                    ),
                    _ChallengePage(
                      value: challenge,
                      onChanged: (value) => setState(() => challenge = value),
                    ),
                    _ReadyPage(
                      challenge: challenge,
                      health: health,
                      onRefresh: _loadHealth,
                    ),
                  ],
                ),
              ),
              _BottomAction(
                label: _primaryLabel,
                loading: saving,
                onPressed: _primaryAction,
                footnote: page == 3 && !_requiredReady
                    ? '必要な設定を完了すると開始できます'
                    : null,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.page, required this.onBack});
  final int page;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(10, 8, 18, 4),
      child: Row(
        children: [
          IconButton(
            tooltip: page == 0 ? '閉じる' : '戻る',
            onPressed: onBack,
            icon: Icon(page == 0 ? Icons.close_rounded : Icons.arrow_back_rounded),
          ),
          const SizedBox(width: 4),
          Text(
            'AppLockout',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w900,
                  letterSpacing: -.3,
                ),
          ),
          const Spacer(),
          if (page > 0)
            Row(
              children: [
                for (var i = 1; i <= 3; i++) ...[
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    width: i == page ? 24 : 8,
                    height: 8,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(99),
                      color: i <= page ? appBlue : const Color(0xFFD8E7F0),
                    ),
                  ),
                  if (i != 3) const SizedBox(width: 6),
                ],
              ],
            ),
        ],
      ),
    );
  }
}

class _BottomAction extends StatelessWidget {
  const _BottomAction({
    required this.label,
    required this.loading,
    required this.onPressed,
    this.footnote,
  });

  final String label;
  final bool loading;
  final VoidCallback? onPressed;
  final String? footnote;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 18),
        decoration: BoxDecoration(
          color: const Color(0xFFF9FCFE).withValues(alpha: .93),
          border: const Border(top: BorderSide(color: Color(0xFFE1ECF2))),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (footnote != null) ...[
              Text(
                footnote!,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: const Color(0xFF6D8191),
                    ),
              ),
              const SizedBox(height: 8),
            ],
            SizedBox(
              width: double.infinity,
              height: 54,
              child: FilledButton(
                onPressed: onPressed,
                child: loading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(label),
              ),
            ),
          ],
        ),
      );
}

class _WelcomePage extends StatelessWidget {
  const _WelcomePage();

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 18, 24, 28),
        child: Column(
          children: [
            const SizedBox(height: 12),
            const _PhoneIllustration(),
            const SizedBox(height: 34),
            Text(
              '開く前に、ひと呼吸。',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                    letterSpacing: -.8,
                    height: 1.18,
                  ),
            ).animate().fadeIn(duration: 360.ms).slideY(begin: .08, end: 0),
            const SizedBox(height: 13),
            Text(
              'つい開いてしまうアプリとの間に、\n小さな「選び直す時間」をつくります。',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color: const Color(0xFF566F82),
                    height: 1.65,
                  ),
            ).animate(delay: 70.ms).fadeIn(duration: 380.ms),
          ],
        ),
      );
}

class _PhoneIllustration extends StatelessWidget {
  const _PhoneIllustration();

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 300,
        child: Stack(
          alignment: Alignment.center,
          children: [
            Container(
              width: 184,
              height: 278,
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFF142330),
                borderRadius: BorderRadius.circular(38),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x24264D68),
                    blurRadius: 36,
                    offset: Offset(0, 20),
                  ),
                ],
              ),
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(29),
                  gradient: const LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Color(0xFFF4FBFF), Color(0xFFE8F4FB)],
                  ),
                ),
                child: Stack(
                  children: [
                    Align(
                      alignment: const Alignment(0, -.91),
                      child: Container(
                        width: 56,
                        height: 7,
                        decoration: BoxDecoration(
                          color: const Color(0xFF18242D),
                          borderRadius: BorderRadius.circular(99),
                        ),
                      ),
                    ),
                    Center(
                      child: Container(
                        margin: const EdgeInsets.symmetric(horizontal: 18),
                        padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(24),
                          border: Border.all(color: const Color(0xFFD9E9F3)),
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 54,
                              height: 54,
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                gradient: LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [Color(0xFF9EDDF4), Color(0xFF4C9BD0)],
                                ),
                              ),
                              child: const Icon(
                                Icons.hourglass_bottom_rounded,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(height: 12),
                            const Text(
                              '30秒だけ待つ',
                              style: TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
                            ),
                            const SizedBox(height: 4),
                            const Text(
                              '今、本当に使う？',
                              style: TextStyle(color: Color(0xFF688194), fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            )
                .animate(onPlay: (controller) => controller.repeat(reverse: true))
                .moveY(begin: 3, end: -5, duration: 2800.ms, curve: Curves.easeInOutCubic),
            const Positioned(
              left: 10,
              top: 46,
              child: _FloatingAppTile(
                icon: Icons.chat_bubble_rounded,
                background: Color(0xFFE7E0FF),
                foreground: Color(0xFF7158B6),
                delayMs: 0,
              ),
            ),
            const Positioned(
              right: 7,
              top: 76,
              child: _FloatingAppTile(
                icon: Icons.play_arrow_rounded,
                background: Color(0xFFFFE1E8),
                foreground: Color(0xFFB9506C),
                delayMs: 350,
              ),
            ),
            const Positioned(
              left: 24,
              bottom: 34,
              child: _FloatingAppTile(
                icon: Icons.public_rounded,
                background: Color(0xFFDDF5F1),
                foreground: Color(0xFF357D70),
                delayMs: 700,
              ),
            ),
            const Positioned(
              right: 20,
              bottom: 28,
              child: _FloatingAppTile(
                icon: Icons.sports_esports_rounded,
                background: Color(0xFFFFEBCF),
                foreground: Color(0xFF9A6A20),
                delayMs: 1050,
              ),
            ),
          ],
        ),
      );
}

class _FloatingAppTile extends StatelessWidget {
  const _FloatingAppTile({
    required this.icon,
    required this.background,
    required this.foreground,
    required this.delayMs,
  });

  final IconData icon;
  final Color background;
  final Color foreground;
  final int delayMs;

  @override
  Widget build(BuildContext context) => Container(
        width: 58,
        height: 58,
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(19),
          border: Border.all(color: Colors.white, width: 2),
          boxShadow: const [
            BoxShadow(color: Color(0x20264D68), blurRadius: 18, offset: Offset(0, 8)),
          ],
        ),
        child: Icon(icon, color: foreground, size: 27),
      )
          .animate(delay: Duration(milliseconds: delayMs))
          .fadeIn(duration: 380.ms)
          .scaleXY(begin: .85, end: 1, curve: Curves.easeOutBack)
          .then()
          .animate(onPlay: (controller) => controller.repeat(reverse: true))
          .moveY(begin: 0, end: -7, duration: 2400.ms, curve: Curves.easeInOutSine);
}

class _AppsPage extends StatelessWidget {
  const _AppsPage({required this.count, required this.onChoose});
  final int count;
  final VoidCallback onChoose;

  @override
  Widget build(BuildContext context) => _StepScroll(
        step: '1',
        title: '止めたいアプリを選ぶ',
        description: 'カテゴリごとの一括選択もできます。最初は、つい開きがちなアプリだけで十分です。',
        child: Column(
          children: [
            Material(
              color: Colors.white.withValues(alpha: .9),
              borderRadius: BorderRadius.circular(28),
              child: InkWell(
                borderRadius: BorderRadius.circular(28),
                onTap: onChoose,
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Row(
                    children: [
                      Container(
                        width: 58,
                        height: 58,
                        decoration: BoxDecoration(
                          color: const Color(0xFFE2F2FC),
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: const Icon(Icons.apps_rounded, color: appBlue),
                      ),
                      const SizedBox(width: 15),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              count == 0 ? 'アプリを選択' : '$count 個を選択中',
                              style: const TextStyle(
                                fontWeight: FontWeight.w900,
                                fontSize: 17,
                              ),
                            ),
                            const SizedBox(height: 4),
                            const Text(
                              'SNS・動画・ゲームなどから選ぶ',
                              style: TextStyle(color: Color(0xFF617B8F)),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.chevron_right_rounded),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 18),
            const _TinyTip(
              icon: Icons.auto_awesome_rounded,
              text: 'ブラウザやSNSを自動で全部対象にはしません。使いたいものだけ選べます。',
            ),
          ],
        ),
      );
}

class _ChallengePage extends StatelessWidget {
  const _ChallengePage({required this.value, required this.onChanged});
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) => _StepScroll(
        step: '2',
        title: '開く前にすること',
        description: '毎回こなせるくらいの、小さなひと手間をひとつ選びます。',
        child: Column(
          children: [
            _ChoiceCard(
              selected: value == 'wait',
              icon: Icons.hourglass_bottom_rounded,
              title: '30秒待つ',
              subtitle: 'その場で少しだけ間を置く',
              accent: const Color(0xFF5A9FCB),
              onTap: () => onChanged('wait'),
            ),
            const SizedBox(height: 12),
            _ChoiceCard(
              selected: value == 'walk',
              icon: Icons.directions_walk_rounded,
              title: '100歩歩く',
              subtitle: '一度身体を動かしてから使う',
              accent: const Color(0xFF5D9B80),
              onTap: () => onChanged('walk'),
            ),
          ],
        ),
      );
}

class _ReadyPage extends StatelessWidget {
  const _ReadyPage({
    required this.challenge,
    required this.health,
    required this.onRefresh,
  });

  static const _channel = MethodChannel('dev.besan.browserbrake/app');
  final String challenge;
  final Map<String, dynamic> health;
  final Future<void> Function() onRefresh;

  Future<void> _call(String method) => _channel.invokeMethod<void>(method);

  @override
  Widget build(BuildContext context) {
    final accessibility = health['accessibility'] == true;
    final activity = health['activityRecognition'] == true;
    final notifications = health['notifications'] == true;

    return _StepScroll(
      step: '3',
      title: '動く準備をする',
      description: '必要なAndroid設定だけ確認します。ここまで終われば使い始められます。',
      child: Column(
        children: [
          _PermissionCard(
            icon: Icons.accessibility_new_rounded,
            title: 'Accessibility',
            text: accessibility ? '準備できています' : '対象アプリの検知に必要です',
            ready: accessibility,
            onTap: accessibility ? null : () => _call('openAccessibilitySettings'),
          ),
          if (challenge == 'walk') ...[
            const SizedBox(height: 10),
            _PermissionCard(
              icon: Icons.directions_walk_rounded,
              title: '身体活動',
              text: activity ? '準備できています' : '歩数の取得に必要です',
              ready: activity,
              onTap: activity
                  ? null
                  : () async {
                      await _call('requestActivityRecognition');
                      await Future<void>.delayed(const Duration(milliseconds: 400));
                      await onRefresh();
                    },
            ),
          ],
          const SizedBox(height: 10),
          _PermissionCard(
            icon: Icons.notifications_none_rounded,
            title: '通知',
            text: notifications ? '許可済み' : '残り時間の確認におすすめ',
            ready: notifications,
            optional: true,
            onTap: notifications ? null : () => _call('openNotificationSettings'),
          ),
          const SizedBox(height: 14),
          TextButton.icon(
            onPressed: onRefresh,
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('状態を再確認'),
          ),
        ],
      ),
    );
  }
}

class _StepScroll extends StatelessWidget {
  const _StepScroll({
    required this.step,
    required this.title,
    required this.description,
    required this.child,
  });

  final String step;
  final String title;
  final String description;
  final Widget child;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 28),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: const Color(0xFFE3F2FB),
                borderRadius: BorderRadius.circular(99),
              ),
              child: Text(
                'STEP $step',
                style: const TextStyle(
                  color: appBlue,
                  fontWeight: FontWeight.w900,
                  fontSize: 11,
                  letterSpacing: .7,
                ),
              ),
            ).animate().fadeIn(duration: 240.ms),
            const SizedBox(height: 13),
            Text(
              title,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                    letterSpacing: -.7,
                  ),
            ).animate().fadeIn(duration: 330.ms).slideY(begin: .08, end: 0),
            const SizedBox(height: 9),
            Text(
              description,
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    height: 1.55,
                    color: const Color(0xFF5B7487),
                  ),
            ).animate(delay: 50.ms).fadeIn(duration: 330.ms),
            const SizedBox(height: 26),
            child
                .animate(delay: 90.ms)
                .fadeIn(duration: 380.ms)
                .slideY(begin: .05, end: 0),
          ],
        ),
      );
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    required this.selected,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.accent,
    required this.onTap,
  });

  final bool selected;
  final IconData icon;
  final String title;
  final String subtitle;
  final Color accent;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        decoration: BoxDecoration(
          color: selected ? Colors.white : Colors.white.withValues(alpha: .74),
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: selected ? accent : const Color(0xFFDDE8EF),
            width: selected ? 2 : 1,
          ),
          boxShadow: selected
              ? const [
                  BoxShadow(
                    color: Color(0x14264D68),
                    blurRadius: 18,
                    offset: Offset(0, 8),
                  ),
                ]
              : null,
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(24),
          child: Padding(
            padding: const EdgeInsets.all(17),
            child: Row(
              children: [
                Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    color: accent.withValues(alpha: .14),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(icon, color: accent),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
                      ),
                      const SizedBox(height: 3),
                      Text(subtitle, style: const TextStyle(color: Color(0xFF61798B))),
                    ],
                  ),
                ),
                AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: selected ? accent : const Color(0xFFF0F4F7),
                  ),
                  child: Icon(
                    selected ? Icons.check_rounded : Icons.circle_outlined,
                    size: 17,
                    color: selected ? Colors.white : const Color(0xFF8AA0AF),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}

class _PermissionCard extends StatelessWidget {
  const _PermissionCard({
    required this.icon,
    required this.title,
    required this.text,
    required this.ready,
    this.optional = false,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String text;
  final bool ready;
  final bool optional;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white.withValues(alpha: .88),
        borderRadius: BorderRadius.circular(22),
        child: ListTile(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
          contentPadding: const EdgeInsets.symmetric(horizontal: 15, vertical: 5),
          onTap: onTap,
          leading: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: ready ? const Color(0xFFDFF3E8) : const Color(0xFFFFF0D9),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(
              icon,
              color: ready ? const Color(0xFF3C8062) : const Color(0xFF956B27),
            ),
          ),
          title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          subtitle: Text(optional && !ready ? '$text（任意）' : text),
          trailing: Icon(
            ready ? Icons.check_circle_rounded : Icons.chevron_right_rounded,
            color: ready ? const Color(0xFF3C8062) : null,
          ),
        ),
      );
}

class _TinyTip extends StatelessWidget {
  const _TinyTip({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: const Color(0xFF7093AA)),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              text,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: const Color(0xFF61798B),
                    height: 1.5,
                  ),
            ),
          ),
        ],
      );
}
