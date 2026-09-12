from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Version.
# -----------------------------------------------------------------------------
p = Path('pubspec.yaml')
s = p.read_text()
s = re.sub(r'^version: .*$', 'version: 0.6.0-alpha.8+26', s, flags=re.MULTILINE)
p.write_text(s)

# -----------------------------------------------------------------------------
# Main Flutter UI.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()

# New rules never start with group-level browser/SNS targeting.
s = replace_once(
    s,
    "    if (widget.isNew) draft['challengePhoneBreak'] = false;\n",
    """    if (widget.isNew) {
      draft['challengePhoneBreak'] = false;
      draft['browsers'] = false;
      draft['sns'] = false;
    }
""",
    'new rule defaults',
)

# Replace the old group toggles with one category-aware app picker.
start = s.find("            _EditorCard(\n              title: '対象アプリ',")
end_marker = "            const SizedBox(height: 12),\n            _EditorCard(\n              title: '場所',"
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('target app editor block not found')
new_target = r'''            _EditorCard(
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
'''
s = s[:start] + new_target + s[end + len("            const SizedBox(height: 12),\n"):]

# Records: make the page describe actual usage, not a vague success/failure score.
records_start = s.find('class RecordsPage extends StatefulWidget {')
records_end = s.find('class SettingsPage extends StatefulWidget {', records_start)
if records_start < 0 or records_end < 0:
    raise SystemExit('records page block not found')
new_records = r'''class RecordsPage extends StatefulWidget {
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
                      '高さはその日の対象アプリ利用時間です。',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: const Color(0xFF61798B),
                          ),
                    ),
                    const SizedBox(height: 18),
                    _UsageWeekChart(records: week),
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
                              color: record['commitmentBroken'] == true
                                  ? const Color(0xFF9BCBE8)
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
                              color: Color(0xFFD46A6A),
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
          if (changed)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
              decoration: BoxDecoration(
                color: const Color(0xFFFFEAEA),
                borderRadius: BorderRadius.circular(99),
              ),
              child: const Text(
                '設定変更あり',
                style: TextStyle(
                  color: Color(0xFF9A4646),
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

'''
s = s[:records_start] + new_records + s[records_end:]

s = s.replace(
    "subtitle: const Text('4ステップで、対象と開く前の摩擦と動作準備を決めます'),",
    "subtitle: const Text('3ステップで、対象アプリと開く前のひと手間を決めます'),",
)

p.write_text(s)

# -----------------------------------------------------------------------------
# Native Flutter bridge: new group defaults and honest record semantics.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = s.replace('browsers = bool("browsers", true)', 'browsers = bool("browsers", false)')
old_records = '''    private fun recordMaps(context: Context): List<Map<String, Any?>> {\n        val ruleIds = RuleRepository.metricRuleIds(context).toList()\n        if (ruleIds.isEmpty()) return emptyList()\n        val histories = ruleIds.map { RuleRepository.historyRecords(context, it, 30) }\n        return (0 until 30).mapNotNull { index ->\n            val rows = histories.mapNotNull { it.getOrNull(index) }\n            val first = rows.firstOrNull() ?: return@mapNotNull null\n            mapOf(\n                "dayKey" to first.dayKey,\n                "label" to first.label,\n                "usageMs" to rows.sumOf { it.usageMs },\n                "sessions" to rows.sumOf { it.sessions },\n                "hasData" to rows.any { it.hasData },\n                "commitmentBroken" to rows.any { it.commitmentBroken }\n            )\n        }.reversed()\n    }\n'''
new_records_native = '''    private fun recordMaps(context: Context): List<Map<String, Any?>> {\n        val ruleIds = RuleRepository.metricRuleIds(context).toList()\n        if (ruleIds.isEmpty()) return emptyList()\n        val histories = ruleIds.map { RuleRepository.historyRecords(context, it, 30) }\n        return (0 until 30).mapNotNull { index ->\n            val rows = histories.mapNotNull { it.getOrNull(index) }\n            val first = rows.firstOrNull() ?: return@mapNotNull null\n            val usage = rows.sumOf { it.usageMs }\n            val sessions = rows.sumOf { it.sessions }\n            val commitmentBroken = rows.any { it.commitmentBroken }\n            val hasActivity = usage > 0L || sessions > 0\n            mapOf(\n                "dayKey" to first.dayKey,\n                "label" to first.label,\n                "usageMs" to usage,\n                "sessions" to sessions,\n                "hasActivity" to hasActivity,\n                "hasData" to (hasActivity || commitmentBroken),\n                "commitmentBroken" to commitmentBroken\n            )\n        }.reversed()\n    }\n'''
s = replace_once(s, old_records, new_records_native, 'record maps')
p.write_text(s)
