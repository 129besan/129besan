from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing patch target: {label}')
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Native rule defaults + fresh-install behavior + durable history ids
# ---------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/BrowserRule.kt')
s = p.read_text()
s = s.replace('val challengeWait: Boolean = false,\n    val challengePhoneBreak: Boolean = true,',
              'val challengeWait: Boolean = true,\n    val challengePhoneBreak: Boolean = false,', 1)
s = s.replace('challengeWait = o.optBoolean("challengeWait", false),\n            challengePhoneBreak = o.optBoolean("challengePhoneBreak", true),',
              'challengeWait = o.optBoolean("challengeWait", true),\n            challengePhoneBreak = o.optBoolean("challengePhoneBreak", false),', 1)
p.write_text(s)

p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/RuleRepository.kt')
s = p.read_text()
needle = '''        val prefs = Prefs.p(context)
        if (prefs.contains(KEY_RULES)) return

        val migrated = BrowserRule(
'''
replacement = '''        val prefs = Prefs.p(context)
        if (prefs.contains(KEY_RULES)) return

        // A truly fresh install has no legacy preferences to migrate. Keep the
        // rule list empty so the Flutter onboarding creates the first deliberate
        // restriction instead of inventing a synthetic default rule.
        if (prefs.all.isEmpty()) {
            writeRules(context, emptyList())
            syncGlobalEnabled(context)
            return
        }

        val migrated = BrowserRule(
'''
s = replace_once(s, needle, replacement, 'fresh install migration')

marker = '''    @JvmStatic
    fun syncGlobalEnabled(context: Context) {
'''
helper = '''    @JvmStatic
    fun metricRuleIds(context: Context): Set<String> = buildSet {
        addAll(getRules(context).map { it.id })
        Prefs.p(context).all.keys.forEach { key ->
            if (!key.startsWith("rule:")) return@forEach
            val id = key.removePrefix("rule:").substringBefore(':')
            if (id.isNotBlank()) add(id)
        }
    }

'''
if 'fun metricRuleIds(context: Context)' not in s:
    if marker not in s:
        raise SystemExit('missing metric id marker')
    s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# ---------------------------------------------------------------------------
# Native Flutter bridge: gate status, onboarding state, safe delete accounting
# ---------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = replace_once(
    s,
    '''                    "getHealth" -> result.success(health(activity))
''',
    '''                    "getHealth" -> result.success(health(activity))
                    "getGateStatus" -> result.success(gateStatus(activity))
                    "completeOnboarding" -> {
                        Prefs.p(activity).edit().putBoolean("flutter_onboarding_complete", true).apply()
                        result.success(null)
                    }
''',
    'bridge gate/onboarding methods',
)
s = s.replace('challengeWait = bool("challengeWait", false),\n            challengePhoneBreak = bool("challengePhoneBreak", true),',
              'challengeWait = bool("challengeWait", true),\n            challengePhoneBreak = bool("challengePhoneBreak", false),', 1)

old = '''                    "deleteRule" -> {
                        val id = call.argument<String>("id").orEmpty()
                        if (id.isNotBlank()) RuleRepository.deleteRule(activity, id)
                        BrowserBlockService.requestRuntimeSync()
                        result.success(null)
                    }
'''
new = '''                    "deleteRule" -> {
                        val id = call.argument<String>("id").orEmpty()
                        if (id.isNotBlank()) {
                            RuleRepository.markCommitmentBreak(activity, id, "delete")
                            RuleRepository.deleteRule(activity, id)
                        }
                        BrowserBlockService.requestRuntimeSync()
                        result.success(null)
                    }
'''
s = replace_once(s, old, new, 'delete accounting')

s = replace_once(
    s,
    '''        return mapOf("view" to "home")
    }

    private fun ruleMaps''',
    '''        val shouldOnboard = RuleRepository.getRules(context).isEmpty() &&
            !Prefs.p(context).getBoolean("flutter_onboarding_complete", false)
        return mapOf("view" to "home", "shouldOnboard" to shouldOnboard)
    }

    private fun gateStatus(activity: FlutterActivity): Map<String, Any?> {
        val id = activity.intent.getStringExtra(BrakeGateActivity.EXTRA_RULE_ID).orEmpty()
        val rule = RuleRuntimeStore.ruleForRuntime(activity, id) ?: RuleRepository.getRule(activity, id)
        val state = if (id.isBlank()) RuleRuntimeStore.STATE_LOCKED else RuleRuntimeStore.state(activity, id)
        val now = System.currentTimeMillis()
        val waitRemaining = if (id.isBlank()) 0L else
            (RuleRuntimeStore.challengeWaitDeadline(activity, id) - now).coerceAtLeast(0L)
        val phoneDeadline = if (id.isBlank()) 0L else RuleRuntimeStore.challengePhoneDeadline(activity, id)
        val phoneRemaining = if (phoneDeadline > 0L) (phoneDeadline - now).coerceAtLeast(0L) else 0L
        return mapOf(
            "state" to state,
            "challengeWait" to (rule?.challengeWait == true),
            "waitRemainingMs" to waitRemaining,
            "challengeWalk" to (rule?.challengeWalk == true),
            "requiredSteps" to (if (id.isBlank()) 0 else RuleRuntimeStore.challengeRequiredSteps(activity, id)),
            "walkedSteps" to (if (id.isBlank()) 0 else BrowserBlockService.currentWalkedSteps(activity, id)),
            "challengePhoneBreak" to (rule?.challengePhoneBreak == true),
            "phoneRemainingMs" to phoneRemaining,
            "challengeAll" to (rule?.challengeAll != false)
        )
    }

    private fun ruleMaps''',
    'gate status helper',
)

old = '''    private fun recordMaps(context: Context): List<Map<String, Any?>> {
        val rules = RuleRepository.getRules(context)
        if (rules.isEmpty()) return emptyList()
        val histories = rules.map { RuleRepository.historyRecords(context, it.id, 30) }
'''
new = '''    private fun recordMaps(context: Context): List<Map<String, Any?>> {
        val ruleIds = RuleRepository.metricRuleIds(context).toList()
        if (ruleIds.isEmpty()) return emptyList()
        val histories = ruleIds.map { RuleRepository.historyRecords(context, it, 30) }
'''
s = replace_once(s, old, new, 'deleted rule record history')
p.write_text(s)

# ---------------------------------------------------------------------------
# Accessibility runtime: live walking progress + context-aware enforcement
# ---------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()
marker = '''    public static void requestRuntimeSync() {
'''
helper = '''    public static int currentWalkedSteps(Context context, String ruleId) {
        BrowserBlockService service = activeService.get();
        if (service == null || service.currentStepTotal < 0f || ruleId == null || ruleId.isEmpty()) return 0;
        return RuleRuntimeStore.walkedSteps(context, ruleId, service.currentStepTotal);
    }

'''
if 'public static int currentWalkedSteps' not in s:
    if marker not in s:
        raise SystemExit('missing runtime sync marker')
    s = s.replace(marker, helper + marker, 1)

s = s.replace('BrowserRule rule = RuleRepository.findMatchingRule(this, lastForegroundPackage);',
              'BrowserRule rule = findContextMatchingRuleForPackage(lastForegroundPackage);', 1)

old = '''    private void updatePausedForeground(String pkg) {
        BrowserRule pausedRule = RuleRepository.findPausedRule(this, pkg);
        if (pausedRule != null && !isRuleContextEligible(pausedRule)) pausedRule = null;

        String nextRuleId = pausedRule == null ? "" : pausedRule.getId();
'''
new = '''    private void updatePausedForeground(String pkg) {
        BrowserRule pausedRule = findContextPausedRuleForPackage(pkg);

        String nextRuleId = pausedRule == null ? "" : pausedRule.getId();
'''
s = replace_once(s, old, new, 'paused context matching')

marker = '''    private BrowserRule findActiveRuntimeRuleForPackage(String pkg) {
'''
helper = '''    private BrowserRule findContextPausedRuleForPackage(String pkg) {
        if (pkg == null || pkg.isBlank()) return null;
        long now = System.currentTimeMillis();
        for (BrowserRule rule : RuleRepository.getRules(this)) {
            if (rule.getEnabled()
                    && rule.getPausedUntilMs() > now
                    && TargetGroupCatalog.packageBelongs(this, rule, pkg)
                    && isRuleContextEligible(rule)) {
                return rule;
            }
        }
        return null;
    }

'''
if 'private BrowserRule findContextPausedRuleForPackage' not in s:
    if marker not in s:
        raise SystemExit('missing active runtime matcher')
    s = s.replace(marker, helper + marker, 1)
p.write_text(s)

# ---------------------------------------------------------------------------
# Safe place deletion: never strand rules with a dangling place id
# ---------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterCatalogBridge.kt')
s = p.read_text()
if 'import dev.besan.browserbrake.rules.RuleRepository' not in s:
    s = s.replace('import dev.besan.browserbrake.rules.TargetGroupCatalog\n',
                  'import dev.besan.browserbrake.rules.RuleRepository\nimport dev.besan.browserbrake.rules.TargetGroupCatalog\n', 1)
old = '''                    "deletePlace" -> {
                        call.argument<String>("id")?.let { PlaceStore.delete(activity, it) }
                        result.success(null)
                    }
'''
new = '''                    "deletePlace" -> {
                        val id = call.argument<String>("id").orEmpty()
                        val usedBy = if (id.isBlank()) emptyList() else
                            RuleRepository.getRules(activity).filter { id in it.placeIds }.map { it.name }
                        if (id.isBlank()) {
                            result.success(mapOf("deleted" to false, "usedBy" to emptyList<String>()))
                        } else if (usedBy.isNotEmpty()) {
                            result.success(mapOf("deleted" to false, "usedBy" to usedBy))
                        } else {
                            PlaceStore.delete(activity, id)
                            result.success(mapOf("deleted" to true, "usedBy" to emptyList<String>()))
                        }
                    }
'''
s = replace_once(s, old, new, 'safe native place delete')
p.write_text(s)

# ---------------------------------------------------------------------------
# Flutter place deletion feedback
# ---------------------------------------------------------------------------
p = Path('lib/catalog_pages.dart')
s = p.read_text()
s = s.replace("content: const Text('この場所を使っている制限では、次に設定を開いたとき場所を選び直してください。'),",
              "content: const Text('使われていない場所だけ削除できます。'),", 1)
old = '''    if (ok != true) return;
    await CatalogBridge.call('deletePlace', {'id': id});
    selected.remove(id);
    await _load();
'''
new = '''    if (ok != true) return;
    final result = await CatalogBridge.map('deletePlace', {'id': id});
    if (!mounted) return;
    if (result?['deleted'] != true) {
      final usedBy = (result?['usedBy'] as List? ?? const []).map((e) => e.toString()).toList();
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('この場所は削除できません'),
          content: Text(usedBy.isEmpty
              ? '場所の削除に失敗しました。'
              : '次の制限で使用されています。先に制限側の場所設定を変更してください。\\n\\n${usedBy.map((e) => '・$e').join('\\n')}'),
          actions: [FilledButton(onPressed: () => Navigator.pop(context), child: const Text('確認'))],
        ),
      );
      return;
    }
    selected.remove(id);
    await _load();
'''
s = replace_once(s, old, new, 'safe dart place delete')
p.write_text(s)

# ---------------------------------------------------------------------------
# Flutter main: first-run onboarding, protected delete, real challenge surface
# ---------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace("_ => const HomeShell(),", "_ => HomeShell(showOnboarding: _initial['shouldOnboard'] == true),", 1)

start = s.index('class HomeShell extends StatefulWidget {')
end = s.index('\nclass HomePage extends StatefulWidget {', start)
new_home_shell = r'''class HomeShell extends StatefulWidget {
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
    await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const GuidedSetupModernPage(), fullscreenDialog: true),
    );
    try {
      await NativeBridge.call('completeOnboarding');
    } catch (_) {}
    if (mounted) setState(() => generation++);
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
                icon: Icon(Icons.info_outline),
                selectedIcon: Icon(Icons.info),
                label: '情報'),
          ],
        ),
      ),
    );
  }
}
'''
s = s[:start] + new_home_shell + s[end:]

s = s.replace("subtitle: const Text('3ステップで、対象と開く前の摩擦を決めます'),",
              "subtitle: const Text('4ステップで、対象と開く前の摩擦と動作準備を決めます'),", 1)

# Deleting a rule is a stronger bypass than disabling it, so use the same 30s guard.
old = '''  Future<void> deleteRule() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('この制限を削除しますか？'),
        content: const Text('この操作は元に戻せません。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('キャンセル')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('削除')),
        ],
      ),
    );
    if (ok != true) return;
    await NativeBridge.call('deleteRule', {'id': draft['id']});
    if (mounted) Navigator.pop(context, true);
  }
'''
new = '''  Future<void> deleteRule() async {
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
'''
s = replace_once(s, old, new, 'protected rule delete')

# Replace the misleading stateless Brake screen with live challenge progress.
start = s.index('class BrakeView extends StatelessWidget {')
end = s.index('\nclass UnlockView extends StatelessWidget {', start)
new_brake = r'''class BrakeView extends StatefulWidget {
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
'''
s = s[:start] + new_brake + s[end:]
p.write_text(s)

# ---------------------------------------------------------------------------
# Onboarding: don't create a rule that cannot work yet
# ---------------------------------------------------------------------------
p = Path('lib/onboarding_modern.dart')
s = p.read_text()
s = replace_once(
    s,
    '''    final notifications = health['notifications'] == true;
    return _GuideLayout(
''',
    '''    final notifications = health['notifications'] == true;
    final requiredReady = accessibility && (challenge != 'walk' || activity);
    return _GuideLayout(
''',
    'onboarding required readiness',
)
s = replace_once(
    s,
    '''      action: FilledButton.icon(
        onPressed: saving ? null : onDone,
''',
    '''      action: FilledButton.icon(
        onPressed: saving || !requiredReady ? null : onDone,
''',
    'onboarding ready action',
)
p.write_text(s)
