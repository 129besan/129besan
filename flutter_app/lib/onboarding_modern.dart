import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'catalog_pages.dart';
import 'design_system.dart';

class GuidedSetupModernPage extends StatefulWidget {
  const GuidedSetupModernPage({super.key});

  @override
  State<GuidedSetupModernPage> createState() => _GuidedSetupModernPageState();
}

class _GuidedSetupModernPageState extends State<GuidedSetupModernPage> {
  final PageController controller = PageController();
  int page = 0;
  Set<String> packages = {};
  String challenge = 'wait';

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  Future<void> next() async => controller.nextPage(
      duration: const Duration(milliseconds: 420), curve: Curves.easeOutCubic);

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF4FAFE),
        body: SafeArea(
          child: Column(children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
              child: Row(children: [
                IconButton(
                  onPressed: () {
                    if (page == 0) {
                      Navigator.pop(context);
                    } else {
                      controller.previousPage(
                          duration: const Duration(milliseconds: 360),
                          curve: Curves.easeOutCubic);
                    }
                  },
                  icon: const Icon(Icons.arrow_back_rounded),
                ),
                const Spacer(),
                Text('${page + 1} / 3',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: const Color(0xFF456B88), fontWeight: FontWeight.w700)),
                const SizedBox(width: 12),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 22),
              child: Row(
                children: List.generate(3, (i) => Expanded(
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 280),
                    height: 5,
                    margin: EdgeInsets.only(right: i == 2 ? 0 : 7),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(99),
                      color: i <= page ? const Color(0xFF1769AA) : const Color(0xFFD9E8F2),
                    ),
                  ),
                )),
              ),
            ),
            Expanded(
              child: PageView(
                controller: controller,
                physics: const NeverScrollableScrollPhysics(),
                onPageChanged: (v) => setState(() => page = v),
                children: [
                  _GuideWelcome(onNext: next),
                  _GuideAppsModern(
                    packages: packages,
                    onChanged: (v) => setState(() => packages = v),
                    onNext: next,
                  ),
                  _GuideChallengeModern(
                    value: challenge,
                    onChanged: (v) => setState(() => challenge = v),
                    onDone: _finish,
                  ),
                ],
              ),
            ),
          ]),
        ),
      );

  Future<void> _finish() async {
    final payload = <String, dynamic>{
      'name': '新しい制限', 'enabled': true, 'browsers': false, 'sns': false,
      'customPackages': packages.toList(), 'allPlaces': true, 'placeIds': <String>[],
      'fullLock': false, 'challengeWait': challenge == 'wait',
      'challengePhoneBreak': false, 'challengeWalk': challenge == 'walk',
      'challengeAll': true, 'waitMs': challenge == 'wait' ? 30000 : 15000,
      'phoneBreakMs': 60000, 'walkSteps': challenge == 'walk' ? 100 : 50,
      'readyTimeoutMs': 0, 'askSessionDuration': true,
      'defaultSessionUsageMs': 600000, 'sessionWindowMs': 1800000,
      'dailyUsageLimitMs': 3600000, 'dailySessionLimit': 5,
      'recoveryMs': 300000, 'escalationMode': 'standard', 'confirmed': true,
    };
    const channel = MethodChannel('dev.besan.browserbrake/app');
    await channel.invokeMethod('saveRule', payload);
    if (mounted) Navigator.pop(context, true);
  }
}

class _GuideWelcome extends StatelessWidget {
  const _GuideWelcome({required this.onNext});
  final VoidCallback onNext;
  @override
  Widget build(BuildContext context) => _GuideLayout(
        eyebrow: 'APPLOCKOUT',
        title: '開く前に、\nほんの少しだけ間をつくる。',
        description: '禁止するのではなく、反射的な起動を「自分で選ぶ操作」に変えます。最初の設定は3ステップだけです。',
        visual: const _OrbitVisual(),
        action: FilledButton.icon(
          onPressed: onNext,
          icon: const Icon(Icons.arrow_forward_rounded),
          label: const Text('設定をはじめる'),
        ),
      );
}

class _GuideAppsModern extends StatelessWidget {
  const _GuideAppsModern({required this.packages, required this.onChanged, required this.onNext});
  final Set<String> packages;
  final ValueChanged<Set<String>> onChanged;
  final VoidCallback onNext;
  @override
  Widget build(BuildContext context) => _GuideLayout(
        eyebrow: 'STEP 1 · TARGET',
        title: 'つい開いてしまう\nアプリを選びます。',
        description: '最初は1〜3個がおすすめです。あとからいつでも追加できます。',
        visual: SoftSurface(
          padding: const EdgeInsets.all(18),
          child: Row(children: [
            Container(width: 54, height: 54, decoration: BoxDecoration(color: const Color(0xFFE2F2FC), borderRadius: BorderRadius.circular(17)), child: const Icon(Icons.apps_rounded, color: appBlue)),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(packages.isEmpty ? 'まだ選択していません' : '${packages.length}個のアプリを選択中', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
              const SizedBox(height: 3),
              const Text('アイコン付き一覧から選択', style: TextStyle(color: Color(0xFF54738B))),
            ])),
            const Icon(Icons.chevron_right_rounded),
          ]),
        ),
        secondary: OutlinedButton.icon(
          onPressed: () async {
            final result = await Navigator.push<List<String>>(context, MaterialPageRoute(builder: (_) => AppPickerPage(initial: packages)));
            if (result != null) onChanged(result.toSet());
          },
          icon: const Icon(Icons.grid_view_rounded),
          label: Text(packages.isEmpty ? 'アプリを選ぶ' : '選び直す'),
        ),
        action: FilledButton(onPressed: packages.isEmpty ? null : onNext, child: const Text('次へ')),
      );
}

class _GuideChallengeModern extends StatelessWidget {
  const _GuideChallengeModern({required this.value, required this.onChanged, required this.onDone});
  final String value;
  final ValueChanged<String> onChanged;
  final VoidCallback onDone;
  @override
  Widget build(BuildContext context) => _GuideLayout(
        eyebrow: 'STEP 2 · PAUSE',
        title: '開く前の「ひと手間」を\nひとつ選びます。',
        description: 'スマホ休憩は新規設定から外しました。まずは挙動が明確な2種類に絞ります。',
        visual: Column(children: [
          _ChallengeChoice(
            selected: value == 'wait', icon: Icons.hourglass_top_rounded,
            title: '30秒だけ待つ', text: 'その場で短い時間を置く。シンプルで予測しやすい方法です。',
            onTap: () => onChanged('wait'),
          ),
          const SizedBox(height: 10),
          _ChallengeChoice(
            selected: value == 'walk', icon: Icons.directions_walk_rounded,
            title: '100歩あるく', text: '身体を一度動かしてから使う。座ったままの反射的な起動を切ります。',
            onTap: () => onChanged('walk'),
          ),
        ]),
        action: FilledButton.icon(onPressed: onDone, icon: const Icon(Icons.check_rounded), label: const Text('この設定で作る')),
      );
}

class _GuideLayout extends StatelessWidget {
  const _GuideLayout({required this.eyebrow, required this.title, required this.description, required this.visual, required this.action, this.secondary});
  final String eyebrow, title, description;
  final Widget visual, action;
  final Widget? secondary;
  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 30),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text(eyebrow, style: Theme.of(context).textTheme.labelLarge?.copyWith(color: appBlue, fontWeight: FontWeight.w800, letterSpacing: 1.1)),
          const SizedBox(height: 12),
          Text(title, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w900, height: 1.22, letterSpacing: -.4)),
          const SizedBox(height: 12),
          Text(description, style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.65, color: const Color(0xFF4C6A80))),
          const SizedBox(height: 28),
          visual,
          const SizedBox(height: 28),
          if (secondary != null) ...[secondary!, const SizedBox(height: 10)],
          action,
        ]),
      );
}

class _OrbitVisual extends StatefulWidget {
  const _OrbitVisual();
  @override
  State<_OrbitVisual> createState() => _OrbitVisualState();
}
class _OrbitVisualState extends State<_OrbitVisual> with SingleTickerProviderStateMixin {
  late final AnimationController c = AnimationController(vsync: this, duration: const Duration(seconds: 8))..repeat();
  @override void dispose(){ c.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => SizedBox(height: 230, child: AnimatedBuilder(
    animation: c,
    builder: (_, __) => CustomPaint(painter: _OrbitPainter(c.value), child: const Center(child: Icon(Icons.touch_app_rounded, size: 54, color: appInk))),
  ));
}
class _OrbitPainter extends CustomPainter {
  _OrbitPainter(this.t); final double t;
  @override void paint(Canvas canvas, Size size) {
    final center = Offset(size.width/2, size.height/2);
    final ring = Paint()..style=PaintingStyle.stroke..strokeWidth=1.5..color=const Color(0x553E8CC4);
    canvas.drawCircle(center, 82, ring);
    for (var i=0;i<3;i++) {
      final a = t*6.28318 + i*2.094;
      final p = center + Offset(82*math.cos(a), 82*math.sin(a));
      final r = 18.0 + i*4;
      final fill = Paint()..shader = const LinearGradient(colors:[Color(0xFFC9F1FF),Color(0xFF4E9BD0)]).createShader(Rect.fromCircle(center:p,radius:r));
      canvas.drawCircle(p,r,fill);
      canvas.drawCircle(p,r,Paint()..style=PaintingStyle.stroke..strokeWidth=2..color=const Color(0xCCFFFFFF));
    }
  }
  @override bool shouldRepaint(covariant _OrbitPainter old) => old.t != t;
}

class _ChallengeChoice extends StatelessWidget {
  const _ChallengeChoice({required this.selected, required this.icon, required this.title, required this.text, required this.onTap});
  final bool selected; final IconData icon; final String title,text; final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Material(
    color: selected ? const Color(0xFFE1F2FC) : Colors.white.withValues(alpha:.82),
    borderRadius: BorderRadius.circular(22),
    child: InkWell(
      borderRadius: BorderRadius.circular(22), onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(22), border: Border.all(color: selected ? const Color(0xFF3B8FC8) : const Color(0xFFD7E6EF), width: selected ? 1.8 : 1)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(width:46,height:46,decoration:BoxDecoration(color:selected?const Color(0xFFBFE6FA):const Color(0xFFF0F6FA),borderRadius:BorderRadius.circular(15)),child:Icon(icon,color:appBlue)),
          const SizedBox(width:13),
          Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(title,style:const TextStyle(fontWeight:FontWeight.w800,fontSize:16)),const SizedBox(height:4),Text(text,style:const TextStyle(color:Color(0xFF557186),height:1.45))])),
          AnimatedSwitcher(duration:const Duration(milliseconds:180),child:selected?const Icon(Icons.check_circle_rounded,key:ValueKey(1),color:appBlue):const Icon(Icons.circle_outlined,key:ValueKey(0),color:Color(0xFF9CB4C4))),
        ]),
      ),
    ),
  );
}
