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
    const channel = MethodChannel('dev.besan.browserbrake/app');
    try {
      final raw = await channel.invokeMethod<dynamic>('getHealth');
      if (!mounted) return;
      setState(() => health = Map<String, dynamic>.from(raw as Map? ?? const {}));
    } catch (_) {}
  }

  Future<void> _goTo(int next) async {
    await controller.animateToPage(
      next,
      duration: const Duration(milliseconds: 360),
      curve: Curves.easeOutCubic,
    );
  }

  Future<void> _chooseApps() async {
    final result = await Navigator.of(context).push<List<String>>(
      MaterialPageRoute(builder: (_) => AppPickerPage(initial: packages)),
    );
    if (result != null && mounted) setState(() => packages = result.toSet());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FBFD),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 8, 16, 4),
              child: Row(
                children: [
                  IconButton(
                    tooltip: page == 0 ? '閉じる' : '戻る',
                    onPressed: () {
                      if (page == 0) {
                        Navigator.pop(context);
                      } else {
                        _goTo(page - 1);
                      }
                    },
                    icon: Icon(page == 0 ? Icons.close_rounded : Icons.arrow_back_rounded),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    'セットアップ',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                          color: appInk,
                        ),
                  ),
                  const Spacer(),
                  Text(
                    '${page + 1} / 3',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: const Color(0xFF57748B),
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(22, 4, 22, 10),
              child: Row(
                children: List.generate(3, (index) {
                  return Expanded(
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 260),
                      curve: Curves.easeOutCubic,
                      height: 4,
                      margin: EdgeInsets.only(right: index == 2 ? 0 : 8),
                      decoration: BoxDecoration(
                        color: index <= page ? appBlue : const Color(0xFFDCE8F0),
                        borderRadius: BorderRadius.circular(99),
                      ),
                    ),
                  );
                }),
              ),
            ),
            Expanded(
              child: PageView(
                controller: controller,
                physics: const NeverScrollableScrollPhysics(),
                onPageChanged: (value) => setState(() => page = value),
                children: [
                  _TargetStep(
                    selectedCount: packages.length,
                    onChooseApps: _chooseApps,
                    onNext: packages.isEmpty ? _chooseApps : () => _goTo(1),
                  ),
                  _PauseStep(
                    value: challenge,
                    onChanged: (value) => setState(() => challenge = value),
                    onNext: () => _goTo(2),
                  ),
                  _ReadyStep(
                    challenge: challenge,
                    health: health,
                    saving: saving,
                    onRefresh: _loadHealth,
                    onDone: _finish,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
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
      'waitMs': 30000,
      'phoneBreakMs': 60000,
      'walkSteps': 100,
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
    const channel = MethodChannel('dev.besan.browserbrake/app');
    try {
      final raw = await channel.invokeMethod<dynamic>('saveRule', payload);
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
}

class _TargetStep extends StatelessWidget {
  const _TargetStep({
    required this.selectedCount,
    required this.onChooseApps,
    required this.onNext,
  });

  final int selectedCount;
  final VoidCallback onChooseApps;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return _OnboardingStep(
      title: 'どのアプリに、\nひと呼吸おく？',
      description: 'つい反射的に開いてしまうアプリを選びます。最初は少なめでも大丈夫です。',
      hero: _AppsHero(selectedCount: selectedCount),
      body: SoftSurface(
        padding: const EdgeInsets.all(16),
        child: InkWell(
          borderRadius: BorderRadius.circular(22),
          onTap: onChooseApps,
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: const Color(0xFFE5F3FB),
                  borderRadius: BorderRadius.circular(15),
                ),
                child: const Icon(Icons.apps_rounded, color: appBlue),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      selectedCount == 0 ? '対象アプリを選ぶ' : '$selectedCount個のアプリを選択中',
                      style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'カテゴリごとにまとめて選べます',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF617D91),
                          ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded),
            ],
          ),
        ),
      ),
      primaryLabel: selectedCount == 0 ? 'アプリを選ぶ' : '次へ',
      primaryIcon: selectedCount == 0 ? Icons.grid_view_rounded : Icons.arrow_forward_rounded,
      onPrimary: onNext,
      secondaryLabel: selectedCount == 0 ? null : '選び直す',
      onSecondary: selectedCount == 0 ? null : onChooseApps,
    );
  }
}

class _PauseStep extends StatelessWidget {
  const _PauseStep({
    required this.value,
    required this.onChanged,
    required this.onNext,
  });

  final String value;
  final ValueChanged<String> onChanged;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return _OnboardingStep(
      title: '開く前に、\n何をひとつ挟む？',
      description: '厳しくするより、毎回ちゃんと使えることを優先します。あとから変更できます。',
      hero: _PauseHero(walking: value == 'walk'),
      body: Column(
        children: [
          _ChoiceCard(
            selected: value == 'wait',
            icon: Icons.hourglass_top_rounded,
            title: '30秒だけ待つ',
            description: 'いちばん単純で、場所を選ばず使えます。',
            onTap: () => onChanged('wait'),
          ),
          const SizedBox(height: 10),
          _ChoiceCard(
            selected: value == 'walk',
            icon: Icons.directions_walk_rounded,
            title: '100歩あるく',
            description: '一度身体を動かしてから開きます。',
            onTap: () => onChanged('walk'),
          ),
        ],
      ),
      primaryLabel: '次へ',
      primaryIcon: Icons.arrow_forward_rounded,
      onPrimary: onNext,
    );
  }
}

class _ReadyStep extends StatelessWidget {
  const _ReadyStep({
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
    final requiredReady = accessibility && (challenge != 'walk' || activity);

    return _OnboardingStep(
      title: 'あと少しで、\n使いはじめられます。',
      description: 'Androidの検知に必要な設定だけ確認します。通知はあとからでも構いません。',
      hero: _ReadyHero(ready: requiredReady),
      body: Column(
        children: [
          _PermissionTile(
            icon: Icons.accessibility_new_rounded,
            title: 'Accessibility',
            subtitle: accessibility ? '準備できています' : '対象アプリの検知に必要です',
            ready: accessibility,
            onTap: accessibility ? null : () => _call('openAccessibilitySettings'),
          ),
          if (challenge == 'walk') ...[
            const SizedBox(height: 10),
            _PermissionTile(
              icon: Icons.directions_walk_rounded,
              title: '身体活動',
              subtitle: activity ? '準備できています' : '歩数の取得に必要です',
              ready: activity,
              onTap: activity
                  ? null
                  : () async {
                      await _call('requestActivityRecognition');
                      await Future<void>.delayed(const Duration(milliseconds: 350));
                      await onRefresh();
                    },
            ),
          ],
          const SizedBox(height: 10),
          _PermissionTile(
            icon: Icons.notifications_none_rounded,
            title: '通知',
            subtitle: notifications ? '許可済み' : '残り時間の確認にあると便利です',
            ready: notifications,
            optional: true,
            onTap: notifications ? null : () => _call('openNotificationSettings'),
          ),
        ],
      ),
      primaryLabel: saving ? '作成中…' : 'この設定で始める',
      primaryIcon: saving ? null : Icons.check_rounded,
      onPrimary: saving || !requiredReady ? null : onDone,
      secondaryLabel: '状態を再確認',
      onSecondary: onRefresh,
    );
  }
}

class _OnboardingStep extends StatelessWidget {
  const _OnboardingStep({
    required this.title,
    required this.description,
    required this.hero,
    required this.body,
    required this.primaryLabel,
    required this.onPrimary,
    this.primaryIcon,
    this.secondaryLabel,
    this.onSecondary,
  });

  final String title;
  final String description;
  final Widget hero;
  final Widget body;
  final String primaryLabel;
  final IconData? primaryIcon;
  final VoidCallback? onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(22, 12, 22, 18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w900,
                        height: 1.17,
                        letterSpacing: -.5,
                      ),
                ).animate().fadeIn(duration: 320.ms).slideY(begin: .08, end: 0),
                const SizedBox(height: 10),
                Text(
                  description,
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        height: 1.55,
                        color: const Color(0xFF587286),
                      ),
                ).animate(delay: 60.ms).fadeIn(duration: 340.ms),
                const SizedBox(height: 18),
                hero.animate(delay: 100.ms).fadeIn(duration: 420.ms).scaleXY(begin: .97, end: 1),
                const SizedBox(height: 20),
                body.animate(delay: 160.ms).fadeIn(duration: 360.ms).slideY(begin: .05, end: 0),
              ],
            ),
          ),
        ),
        Container(
          decoration: const BoxDecoration(
            color: Color(0xFFF8FBFD),
            border: Border(top: BorderSide(color: Color(0xFFE2EBF1))),
          ),
          padding: const EdgeInsets.fromLTRB(22, 12, 22, 16),
          child: SafeArea(
            top: false,
            child: Row(
              children: [
                if (secondaryLabel != null && onSecondary != null) ...[
                  Expanded(
                    child: OutlinedButton(
                      onPressed: onSecondary,
                      child: Text(secondaryLabel!),
                    ),
                  ),
                  const SizedBox(width: 10),
                ],
                Expanded(
                  flex: secondaryLabel == null ? 1 : 2,
                  child: FilledButton.icon(
                    onPressed: onPrimary,
                    icon: primaryIcon == null
                        ? const SizedBox.shrink()
                        : Icon(primaryIcon),
                    label: Text(primaryLabel),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _AppsHero extends StatelessWidget {
  const _AppsHero({required this.selectedCount});
  final int selectedCount;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 190,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: 176,
            height: 176,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(42),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFFE8F7FD), Color(0xFFDDECFB)],
              ),
              border: Border.all(color: Colors.white, width: 2),
              boxShadow: const [
                BoxShadow(color: Color(0x18245F88), blurRadius: 28, offset: Offset(0, 14)),
              ],
            ),
          ),
          const Positioned(left: 38, top: 26, child: _MiniApp(icon: Icons.chat_bubble_rounded, color: Color(0xFF77C7A4))),
          const Positioned(right: 34, top: 43, child: _MiniApp(icon: Icons.play_arrow_rounded, color: Color(0xFFE57C91))),
          const Positioned(left: 48, bottom: 26, child: _MiniApp(icon: Icons.public_rounded, color: Color(0xFF6FB7E7))),
          const Positioned(right: 42, bottom: 24, child: _MiniApp(icon: Icons.music_note_rounded, color: Color(0xFF9B8BE5))),
          Container(
            width: 72,
            height: 72,
            decoration: const BoxDecoration(shape: BoxShape.circle, color: Colors.white),
            child: Center(
              child: Text(
                selectedCount == 0 ? '＋' : '$selectedCount',
                style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w900, color: appInk),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MiniApp extends StatelessWidget {
  const _MiniApp({required this.icon, required this.color});
  final IconData icon;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 54,
      height: 54,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(17),
        border: Border.all(color: Colors.white, width: 2),
        boxShadow: const [BoxShadow(color: Color(0x17245F88), blurRadius: 12, offset: Offset(0, 6))],
      ),
      child: Icon(icon, color: Colors.white, size: 27),
    ).animate(onPlay: (controller) => controller.repeat(reverse: true)).moveY(
          begin: -3,
          end: 4,
          duration: 2200.ms,
          curve: Curves.easeInOutSine,
        );
  }
}

class _PauseHero extends StatelessWidget {
  const _PauseHero({required this.walking});
  final bool walking;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 170,
      child: Center(
        child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 320),
          transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: ScaleTransition(scale: Tween(begin: .92, end: 1.0).animate(animation), child: child),
          ),
          child: Container(
            key: ValueKey(walking),
            width: 144,
            height: 144,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: walking ? const Color(0xFFE8F7EF) : const Color(0xFFFFF4DE),
              border: Border.all(color: Colors.white, width: 3),
              boxShadow: const [BoxShadow(color: Color(0x15245F88), blurRadius: 24, offset: Offset(0, 12))],
            ),
            child: Icon(
              walking ? Icons.directions_walk_rounded : Icons.hourglass_top_rounded,
              size: 62,
              color: walking ? const Color(0xFF31845C) : const Color(0xFFB77400),
            ),
          ),
        ),
      ),
    );
  }
}

class _ReadyHero extends StatelessWidget {
  const _ReadyHero({required this.ready});
  final bool ready;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 150,
      child: Center(
        child: Container(
          width: 126,
          height: 126,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(38),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: ready
                  ? const [Color(0xFFDDF6E8), Color(0xFFBCE8D0)]
                  : const [Color(0xFFE7F4FB), Color(0xFFCFE5F4)],
            ),
            border: Border.all(color: Colors.white, width: 3),
          ),
          child: Icon(
            ready ? Icons.check_rounded : Icons.shield_outlined,
            size: 58,
            color: ready ? const Color(0xFF26734D) : appBlue,
          ),
        ).animate(onPlay: (controller) => controller.repeat(reverse: true)).scaleXY(
              begin: .985,
              end: 1.025,
              duration: 1900.ms,
              curve: Curves.easeInOutSine,
            ),
      ),
    );
  }
}

class _ChoiceCard extends StatelessWidget {
  const _ChoiceCard({
    required this.selected,
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
  });

  final bool selected;
  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? const Color(0xFFE4F3FC) : Colors.white,
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(15),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: selected ? const Color(0xFFCCE9F8) : const Color(0xFFF0F5F8),
                  borderRadius: BorderRadius.circular(15),
                ),
                child: Icon(icon, color: selected ? appBlue : const Color(0xFF5C7485)),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                    const SizedBox(height: 3),
                    Text(description, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: const Color(0xFF617789))),
                  ],
                ),
              ),
              AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: selected ? appBlue : const Color(0xFFF0F4F7),
                ),
                child: Icon(selected ? Icons.check_rounded : Icons.circle_outlined,
                    size: 17, color: selected ? Colors.white : const Color(0xFF8AA0AF)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  const _PermissionTile({
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
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
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
}
