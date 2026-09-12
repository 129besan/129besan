from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing pattern: {label}")
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Flutter dependencies/version.
# -----------------------------------------------------------------------------
p = Path('pubspec.yaml')
s = p.read_text()
s = re.sub(r'^version: .*$', 'version: 0.6.0-alpha.7+25', s, flags=re.MULTILINE)
if 'flutter_animate:' not in s:
    s = s.replace('  cupertino_icons: ^1.0.8\n', '  cupertino_icons: ^1.0.8\n  flutter_animate: ^4.5.2\n')
p.write_text(s)

# -----------------------------------------------------------------------------
# Main UI: restore Info, simplify rule editor, preserve legacy challenge UI.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()

s = replace_once(
    s,
    '''    final pages = <Widget>[\n      const HomePage(),\n      const RecordsPage(),\n      const SettingsPage(),\n    ];\n''',
    '''    final pages = <Widget>[\n      const HomePage(),\n      const RecordsPage(),\n      const SettingsPage(),\n      const InfoPage(),\n    ];\n''',
    'restore info page',
)

s = replace_once(
    s,
    '''            NavigationDestination(\n                icon: Icon(Icons.tune_outlined),\n                selectedIcon: Icon(Icons.tune),\n                label: '設定'),\n''',
    '''            NavigationDestination(\n                icon: Icon(Icons.tune_outlined),\n                selectedIcon: Icon(Icons.tune),\n                label: '設定'),\n            NavigationDestination(\n                icon: Icon(Icons.info_outline_rounded),\n                selectedIcon: Icon(Icons.info_rounded),\n                label: '情報'),\n''',
    'restore info destination',
)

# Existing legacy Phone Break rules must still participate in ALL/ANY choice.
s = s.replace(
    "if ([b('challengeWait'), b('challengeWalk')]\n",
    "if ([b('challengeWait'), b('challengeWalk'), b('challengePhoneBreak')]\n",
    1,
)

# Escalation was difficult to explain and is no longer part of the product UI.
escalation_row = '''                  _ChoiceRow(\n                    label: '繰り返し利用への強さ',\n                    value: (draft['escalationMode'] as String?) ?? 'standard',\n                    options: const {'none': 'なし', 'standard': '標準', 'strong': '強め'},\n                    onChanged: (v) => setValue('escalationMode', v),\n                  ),\n'''
if escalation_row in s:
    s = s.replace(escalation_row, '', 1)

# Settings should stay operational; explanatory material lives in Info again.
about_section = '''          const SizedBox(height: 18),\n          const SectionLabel('AppLockoutについて'),\n          const _GlassCard(\n            child: Column(children: [\n              _InfoTile(\n                icon: Icons.psychology_alt_outlined,\n                title: '考え方',\n                text: '反射的なアプリ起動の前に、短い選び直しの時間をつくります。'),\n              Divider(height: 1),\n              _InfoTile(\n                icon: Icons.lock_outline_rounded,\n                title: 'プライバシー',\n                text: '制限設定や利用記録はアプリ内に保存し、AppLockout独自のサーバーへ送信しません。Accessibilityは前面アプリの検知に使います。'),\n            ]),\n          ),\n'''
if about_section in s:
    s = s.replace(about_section, '', 1)

info_page = r'''class InfoPage extends StatelessWidget {
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
                text: '利用セッションでは、対象アプリが実際に前面にある時間を中心に消費します。残り時間や休憩状態は通知から確認できます。'),
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
        ],
      );
}

'''
marker = 'class BrakeView extends StatefulWidget {'
if info_page not in s:
    if marker not in s:
        raise SystemExit('missing BrakeView marker')
    s = s.replace(marker, info_page + marker, 1)

p.write_text(s)

# -----------------------------------------------------------------------------
# Onboarding: Flutter Animate + bounded floating circles. No fuzzy blobs.
# -----------------------------------------------------------------------------
p = Path('lib/onboarding_modern.dart')
s = p.read_text()
s = s.replace("import 'dart:math' as math;\n\n", '')
if "package:flutter_animate/flutter_animate.dart" not in s:
    s = s.replace(
        "import 'package:flutter/material.dart';\n",
        "import 'package:flutter/material.dart';\nimport 'package:flutter_animate/flutter_animate.dart';\n",
    )
s = s.replace("'escalationMode': 'standard'", "'escalationMode': 'none'")
s = s.replace('visual: const _OrbitVisual(),', 'visual: const _BubbleGardenVisual(),')

layout_start = s.find('class _GuideLayout extends StatelessWidget {')
visual_end_marker = 'class _ChallengeChoice extends StatelessWidget {'
layout_end = s.find(visual_end_marker, layout_start)
if layout_start < 0 or layout_end < 0:
    raise SystemExit('missing onboarding layout/visual block')

new_layout_visual = r'''class _GuideLayout extends StatelessWidget {
  const _GuideLayout({
    required this.eyebrow,
    required this.title,
    required this.description,
    required this.visual,
    required this.action,
    this.secondary,
  });

  final String eyebrow;
  final String title;
  final String description;
  final Widget visual;
  final Widget action;
  final Widget? secondary;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 30),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              eyebrow,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: appBlue,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1.1,
                  ),
            ).animate().fadeIn(duration: 280.ms).slideY(begin: .12, end: 0),
            const SizedBox(height: 12),
            Text(
              title,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.w900,
                    height: 1.22,
                    letterSpacing: -.4,
                  ),
            ).animate(delay: 55.ms).fadeIn(duration: 360.ms).slideY(begin: .10, end: 0),
            const SizedBox(height: 12),
            Text(
              description,
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    height: 1.65,
                    color: const Color(0xFF4C6A80),
                  ),
            ).animate(delay: 100.ms).fadeIn(duration: 380.ms).slideY(begin: .08, end: 0),
            const SizedBox(height: 26),
            visual
                .animate(delay: 150.ms)
                .fadeIn(duration: 480.ms)
                .scaleXY(begin: .965, end: 1, curve: Curves.easeOutBack),
            const SizedBox(height: 26),
            if (secondary != null) ...[
              secondary!
                  .animate(delay: 210.ms)
                  .fadeIn(duration: 360.ms)
                  .slideY(begin: .08, end: 0),
              const SizedBox(height: 10),
            ],
            action
                .animate(delay: 250.ms)
                .fadeIn(duration: 360.ms)
                .slideY(begin: .10, end: 0),
          ],
        ),
      );
}

class _BubbleGardenVisual extends StatelessWidget {
  const _BubbleGardenVisual();

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 238,
        child: Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            Positioned(
              left: 18,
              top: 24,
              child: _SoftBubble(
                size: 54,
                light: const Color(0xFFE8FBFF),
                dark: const Color(0xFF63C7EA),
                duration: 3100.ms,
                travel: const Offset(6, 10),
              ),
            ),
            Positioned(
              right: 24,
              top: 10,
              child: _SoftBubble(
                size: 42,
                light: const Color(0xFFF1ECFF),
                dark: const Color(0xFF8E83E8),
                duration: 3700.ms,
                travel: const Offset(-8, 7),
              ),
            ),
            Positioned(
              left: 42,
              bottom: 18,
              child: _SoftBubble(
                size: 34,
                light: const Color(0xFFE9FFF7),
                dark: const Color(0xFF5BC9A5),
                duration: 2800.ms,
                travel: const Offset(7, -8),
              ),
            ),
            Positioned(
              right: 48,
              bottom: 28,
              child: _SoftBubble(
                size: 60,
                light: const Color(0xFFFFF2F8),
                dark: const Color(0xFFE18BB5),
                duration: 4200.ms,
                travel: const Offset(-6, -10),
              ),
            ),
            Container(
              width: 132,
              height: 132,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: const RadialGradient(
                  center: Alignment(-.38, -.42),
                  radius: .95,
                  colors: [
                    Color(0xFFF2FCFF),
                    Color(0xFF8EDAF3),
                    Color(0xFF2F82BD),
                  ],
                  stops: [0, .50, 1],
                ),
                border: Border.all(color: Colors.white, width: 3),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x33245F88),
                    blurRadius: 30,
                    offset: Offset(0, 14),
                  ),
                ],
              ),
              child: Stack(
                alignment: Alignment.center,
                children: const [
                  Icon(Icons.touch_app_rounded, size: 50, color: Color(0xFF0B426D)),
                  Positioned(
                    right: 22,
                    top: 19,
                    child: Icon(Icons.auto_awesome_rounded, size: 20, color: Colors.white),
                  ),
                ],
              ),
            )
                .animate(onPlay: (controller) => controller.repeat(reverse: true))
                .scaleXY(begin: .975, end: 1.035, duration: 2500.ms, curve: Curves.easeInOutCubic)
                .moveY(begin: 3, end: -4, duration: 2500.ms, curve: Curves.easeInOutCubic),
          ],
        ),
      );
}

class _SoftBubble extends StatelessWidget {
  const _SoftBubble({
    required this.size,
    required this.light,
    required this.dark,
    required this.duration,
    required this.travel,
  });

  final double size;
  final Color light;
  final Color dark;
  final Duration duration;
  final Offset travel;

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            center: const Alignment(-.35, -.40),
            radius: .95,
            colors: [Colors.white.withValues(alpha: .95), light, dark],
            stops: const [0, .46, 1],
          ),
          border: Border.all(color: Colors.white.withValues(alpha: .9), width: 2),
          boxShadow: const [
            BoxShadow(color: Color(0x24245F88), blurRadius: 16, offset: Offset(0, 7)),
          ],
        ),
      )
          .animate(onPlay: (controller) => controller.repeat(reverse: true))
          .move(
            begin: Offset(-travel.dx / 2, -travel.dy / 2),
            end: Offset(travel.dx / 2, travel.dy / 2),
            duration: duration,
            curve: Curves.easeInOutSine,
          )
          .scaleXY(begin: .96, end: 1.04, duration: duration, curve: Curves.easeInOutSine);
}

'''
s = s[:layout_start] + new_layout_visual + s[layout_end:]
p.write_text(s)

# -----------------------------------------------------------------------------
# Escalation removal. Keep the serialized field for old JSON compatibility, but
# disable the behavior globally and default all new/saved rules to none.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/BrowserRule.kt')
s = p.read_text()
s = s.replace('val escalationMode: String = "standard"', 'val escalationMode: String = "none"')
s = s.replace('escalationMode = o.optString("escalationMode", "standard")', 'escalationMode = o.optString("escalationMode", "none")')
p.write_text(s)

p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = s.replace('escalationMode = m["escalationMode"] as? String ?: "standard"', 'escalationMode = "none"')
p.write_text(s)

p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/RuleRepository.kt')
s = p.read_text()
s = s.replace('''            if (escalationRank(after.escalationMode) < escalationRank(before.escalationMode)) {\n                reasons += "繰り返し利用への制限を弱くする"\n            }\n''', '')
rank_start = s.find('    private fun escalationRank(mode: String): Int = when (mode) {')
if rank_start >= 0:
    rank_end = s.find('    @JvmStatic\n    fun conflicts', rank_start)
    if rank_end < 0:
        raise SystemExit('missing conflicts after escalationRank')
    s = s[:rank_start] + s[rank_end:]
p.write_text(s)

p = Path('android/app/src/main/java/dev/besan/browserbrake/runtime/RuleRuntimeStore.kt')
s = p.read_text()
s = replace_once(
    s,
    '''        recordTargetAttempt(context, rule.id, rule, now)\n        val over = isOverDailyLimit(context, rule)\n        val level = effectiveEscalationLevel(context, rule.id, rule, now)\n        var multiplier = escalationMultiplier(rule.escalationMode, level)\n        if (over) multiplier *= overLimitMultiplier(context)\n''',
    '''        recordTargetAttempt(context, rule.id, rule, now)\n        val over = isOverDailyLimit(context, rule)\n        val multiplier = if (over) overLimitMultiplier(context) else 1.0\n''',
    'challenge escalation removal',
)

helper_start = s.find('    private fun escalationDisabled(mode: String): Boolean')
helper_end = s.find('    @JvmStatic\n    fun challengeWaitDeadline', helper_start)
if helper_start >= 0 and helper_end >= 0:
    s = s[:helper_start] + s[helper_end:]

s = replace_once(
    s,
    '''        val currentLevel = effectiveEscalationLevel(context, ruleId, rule, now)\n        val nextLevel = if (escalationDisabled(rule.escalationMode)) 0 else min(4, currentLevel + 1)\n        val sessions = RuleRepository.dailySessionsRaw(context, ruleId)\n''',
    '''        val nextLevel = 0\n        val sessions = RuleRepository.dailySessionsRaw(context, ruleId)\n''',
    'session escalation removal',
)

s = replace_once(
    s,
    '''        val rule = ruleForRuntime(context, ruleId) ?: return\n        val now = System.currentTimeMillis()\n        val level = effectiveEscalationLevel(context, ruleId, rule, now)\n        Prefs.p(context).edit()\n            .putLong(key(ruleId, "session_foreground_since"), now)\n            .putLong(key(ruleId, "session_foreground_checkpoint"), now)\n            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), level)\n''',
    '''        ruleForRuntime(context, ruleId) ?: return\n        val now = System.currentTimeMillis()\n        Prefs.p(context).edit()\n            .putLong(key(ruleId, "session_foreground_since"), now)\n            .putLong(key(ruleId, "session_foreground_checkpoint"), now)\n            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), 0)\n''',
    'foreground escalation removal',
)

record_start = s.find('    @JvmStatic\n    @JvmOverloads\n    fun recordTargetAttempt')
record_end = s.find('    @JvmStatic\n    fun isOverDailyLimit', record_start)
if record_start < 0 or record_end < 0:
    raise SystemExit('missing escalation record block')
replacement = '''    @JvmStatic\n    @JvmOverloads\n    fun recordTargetAttempt(\n        context: Context,\n        ruleId: String,\n        rule: BrowserRule? = null,\n        now: Long = System.currentTimeMillis()\n    ) {\n        rule ?: ruleForRuntime(context, ruleId) ?: RuleRepository.getRule(context, ruleId) ?: return\n        Prefs.p(context).edit()\n            .putInt(RuleRepository.ruleMetricKey(ruleId, "escalation_level"), 0)\n            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)\n            .apply()\n    }\n\n    @JvmStatic\n    fun effectiveEscalationLevel(\n        context: Context,\n        ruleId: String,\n        rule: BrowserRule,\n        now: Long\n    ): Int = 0\n\n'''
s = s[:record_start] + replacement + s[record_end:]
p.write_text(s)
