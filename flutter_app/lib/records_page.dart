import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'design_system.dart';

class RecordsPage extends StatefulWidget {
  const RecordsPage({super.key});

  @override
  State<RecordsPage> createState() => _RecordsPageState();
}

class _RecordsPageState extends State<RecordsPage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('dev.besan.browserbrake/app');
  List<Map<String, dynamic>> records = const [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load(silent: true);
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent && mounted) setState(() => loading = true);
    try {
      final raw = await _channel.invokeMethod<dynamic>('getRecords');
      final value = (raw as List? ?? const [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList();
      if (mounted) setState(() {
        records = value;
        loading = false;
      });
    } catch (_) {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final today = records.isEmpty ? null : records.first;
    final recent7 = records.take(7).toList();
    final sevenUsage = recent7.fold<int>(0, (sum, r) => sum + _int(r, 'usageMs'));
    final sevenSessions = recent7.fold<int>(0, (sum, r) => sum + _int(r, 'sessions'));

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(18, 22, 18, 30),
        children: [
          _pageTitle(
            context,
            '記録',
            '対象アプリを実際に使った時間と回数を表示します。',
          ),
          const SizedBox(height: 18),
          if (loading && records.isEmpty)
            const SizedBox(height: 280, child: Center(child: CircularProgressIndicator()))
          else if (records.isEmpty)
            const _EmptyRecords()
          else ...[
            Row(children: [
              Expanded(
                child: _SummaryCard(
                  icon: Icons.schedule_rounded,
                  label: '今日の利用',
                  value: _duration(_int(today!, 'usageMs')),
                  caption: '${_int(today, 'sessions')} 回',
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _SummaryCard(
                  icon: Icons.calendar_view_week_rounded,
                  label: '直近7日',
                  value: _duration(sevenUsage),
                  caption: '$sevenSessions 回',
                ),
              ),
            ]),
            const SizedBox(height: 12),
            SoftSurface(
              padding: const EdgeInsets.fromLTRB(18, 18, 18, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Text(
                      '7日間の利用時間',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w800,
                          ),
                    ),
                    const Spacer(),
                    Text(
                      _duration(sevenUsage),
                      style: const TextStyle(
                        color: Color(0xFF456B88),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ]),
                  const SizedBox(height: 20),
                  _UsageBars(records: recent7),
                  const SizedBox(height: 8),
                  Text(
                    '1日は午前4時に切り替わります',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: const Color(0xFF6A8192),
                        ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            SoftSurface(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '直近30日',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '濃いほど利用時間が長く、赤は通常利用の上限に達した日です。',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: const Color(0xFF617A8D),
                          height: 1.45,
                        ),
                  ),
                  const SizedBox(height: 14),
                  _UsageHeatmap(records: records),
                  const SizedBox(height: 12),
                  const Wrap(
                    spacing: 14,
                    runSpacing: 6,
                    children: [
                      _Legend(color: Color(0xFFDCEBF4), text: '少ない'),
                      _Legend(color: Color(0xFF4D9FD0), text: '多い'),
                      _Legend(color: Color(0xFFE79A9A), text: '通常上限に到達'),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Text(
              '日ごとの記録',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
            ),
            const SizedBox(height: 8),
            SoftSurface(
              padding: EdgeInsets.zero,
              child: Column(
                children: [
                  for (var i = 0; i < records.take(14).length; i++) ...[
                    _DayRow(record: records[i]),
                    if (i != records.take(14).length - 1)
                      const Divider(height: 1, indent: 16, endIndent: 16),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.caption,
  });

  final IconData icon;
  final String label;
  final String value;
  final String caption;

  @override
  Widget build(BuildContext context) => SoftSurface(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(icon, size: 18, color: appBlue),
              const SizedBox(width: 7),
              Flexible(
                child: Text(
                  label,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: const Color(0xFF587389),
                        fontWeight: FontWeight.w700,
                      ),
                ),
              ),
            ]),
            const SizedBox(height: 12),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(
                value,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w900,
                      letterSpacing: -.5,
                    ),
              ),
            ),
            const SizedBox(height: 2),
            Text(
              caption,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: const Color(0xFF6A8192),
                  ),
            ),
          ],
        ),
      );
}

class _UsageBars extends StatelessWidget {
  const _UsageBars({required this.records});
  final List<Map<String, dynamic>> records;

  @override
  Widget build(BuildContext context) {
    final days = records.reversed.toList();
    final maxUsage = days.fold<int>(1, (m, r) {
      final value = _int(r, 'usageMs');
      return value > m ? value : m;
    });

    return SizedBox(
      height: 126,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (final day in days)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 3),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Expanded(
                      child: Align(
                        alignment: Alignment.bottomCenter,
                        child: Tooltip(
                          message: '${day['label'] ?? ''}  ${_duration(_int(day, 'usageMs'))}',
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 240),
                            width: 24,
                            height: _barHeight(_int(day, 'usageMs'), maxUsage),
                            decoration: BoxDecoration(
                              color: day['overLimit'] == true
                                  ? const Color(0xFFE79A9A)
                                  : const Color(0xFF67B3DE),
                              borderRadius: BorderRadius.circular(7),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      _shortLabel(day['label']?.toString() ?? ''),
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: const Color(0xFF6A8192),
                          ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  static double _barHeight(int value, int maxValue) {
    if (value <= 0) return 4;
    return 12 + 72 * (value / maxValue);
  }
}

class _UsageHeatmap extends StatelessWidget {
  const _UsageHeatmap({required this.records});
  final List<Map<String, dynamic>> records;

  @override
  Widget build(BuildContext context) {
    final days = records.take(30).toList().reversed.toList();
    final maxUsage = days.fold<int>(1, (m, r) {
      final value = _int(r, 'usageMs');
      return value > m ? value : m;
    });
    final offset = 30 - days.length;

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 10,
        crossAxisSpacing: 6,
        mainAxisSpacing: 6,
      ),
      itemCount: 30,
      itemBuilder: (context, index) {
        if (index < offset) return _heatCell(const Color(0xFFEAF1F5));
        final record = days[index - offset];
        final usage = _int(record, 'usageMs');
        final over = record['overLimit'] == true;
        final changed = record['commitmentBroken'] == true;
        final color = over
            ? const Color(0xFFE79A9A)
            : _usageColor(usage, maxUsage);
        return Tooltip(
          message: '${record['label'] ?? ''}  ${_duration(usage)}'
              '${changed ? '  · 設定変更あり' : ''}',
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              Positioned.fill(child: _heatCell(color)),
              if (changed)
                Positioned(
                  right: -1,
                  top: -1,
                  child: Container(
                    width: 7,
                    height: 7,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: Color(0xFFF1B85B),
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  static Widget _heatCell(Color color) => DecoratedBox(
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(7),
          border: Border.all(color: Colors.white.withValues(alpha: .85)),
        ),
      );

  static Color _usageColor(int usage, int maxUsage) {
    if (usage <= 0) return const Color(0xFFEAF1F5);
    final t = (usage / maxUsage).clamp(.18, 1.0);
    return Color.lerp(const Color(0xFFDCEBF4), const Color(0xFF4D9FD0), t)!;
  }
}

class _Legend extends StatelessWidget {
  const _Legend({required this.color, required this.text});
  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 11,
            height: 11,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(3),
            ),
          ),
          const SizedBox(width: 5),
          Text(text, style: Theme.of(context).textTheme.labelSmall),
        ],
      );
}

class _DayRow extends StatelessWidget {
  const _DayRow({required this.record});
  final Map<String, dynamic> record;

  @override
  Widget build(BuildContext context) {
    final usage = _int(record, 'usageMs');
    final sessions = _int(record, 'sessions');
    final over = record['overLimit'] == true;
    final changed = record['commitmentBroken'] == true;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      child: Row(
        children: [
          SizedBox(
            width: 52,
            child: Text(
              record['label']?.toString() ?? '',
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  usage == 0 ? '利用なし' : _duration(usage),
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                if (over || changed) ...[
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: [
                      if (over)
                        const _StatusTag(
                          text: '通常上限に到達',
                          background: Color(0xFFFFE6E6),
                          foreground: Color(0xFF9B4343),
                        ),
                      if (changed)
                        const _StatusTag(
                          text: '制限を変更',
                          background: Color(0xFFFFF1D8),
                          foreground: Color(0xFF805D17),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          Text(
            '$sessions 回',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: const Color(0xFF627D8F),
                  fontWeight: FontWeight.w700,
                ),
          ),
        ],
      ),
    );
  }
}

class _StatusTag extends StatelessWidget {
  const _StatusTag({
    required this.text,
    required this.background,
    required this.foreground,
  });
  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(99),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: foreground,
            fontSize: 10.5,
            fontWeight: FontWeight.w700,
          ),
        ),
      );
}

class _EmptyRecords extends StatelessWidget {
  const _EmptyRecords();

  @override
  Widget build(BuildContext context) => SoftSurface(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 36),
        child: Column(
          children: [
            const Icon(Icons.query_stats_rounded, size: 44, color: appBlue),
            const SizedBox(height: 13),
            Text(
              'まだ利用記録はありません',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
            ),
            const SizedBox(height: 6),
            const Text(
              '対象アプリを利用すると、実際に使った時間と回数がここに表示されます。',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
}

Widget _pageTitle(BuildContext context, String title, String subtitle) => Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: -.4,
              ),
        ),
        const SizedBox(height: 3),
        Text(
          subtitle,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: const Color(0xFF526E82),
              ),
        ),
      ],
    );

int _int(Map<String, dynamic>? map, String key) =>
    (map?[key] as num?)?.toInt() ?? 0;

String _duration(int ms) {
  final minutes = (ms / 60000).floor();
  if (minutes <= 0) return '0分';
  if (minutes < 60) return '$minutes分';
  final hours = minutes ~/ 60;
  final rest = minutes % 60;
  return rest == 0 ? '$hours時間' : '$hours時間 $rest分';
}

String _shortLabel(String label) {
  if (label == '今日') return '今日';
  return label;
}
