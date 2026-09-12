from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()

s = s.replace("label: '1日の利用時間上限',", "label: '1日の通常利用時間',", 1)
s = s.replace("label: '1日の利用回数上限',", "label: '1日の通常利用回数',", 1)

s = s.replace(
    "    final fullLock = b('fullLock');\n",
    "    final fullLock = b('fullLock');\n    final paused = n('pausedUntilMs') > DateTime.now().millisecondsSinceEpoch;\n",
    1,
)
s = s.replace(
    "                  OutlinedButton(onPressed: () => pause(0), child: const Text('再開')),\n",
    "                  if (paused)\n                    OutlinedButton(onPressed: () => pause(0), child: const Text('再開')),\n",
    1,
)

old_time = """              _InfoTile(
                icon: Icons.timelapse_rounded,
                title: '利用時間の数え方',
                text: '利用セッションでは、対象アプリが実際に前面にある時間を中心に消費します。残り時間や休憩状態は通知から確認できます。'),
"""
new_time = """              _InfoTile(
                icon: Icons.timelapse_rounded,
                title: '利用時間の数え方',
                text: '利用セッションでは、対象アプリが実際に前面にある時間を中心に消費します。通常利用の上限に達した後も、必要なときは強い解除条件を経て短時間だけ追加利用できます。'),
"""
if old_time not in s:
    raise SystemExit('missing info time tile')
s = s.replace(old_time, new_time, 1)

needle = """          const _GlassCard(
            child: _InfoTile(
              icon: Icons.info_outline_rounded,
              title: 'Android上の制約',
              text: '強制停止や一部メーカー独自の省電力機能でAccessibilityが停止すると、制限も動作できません。ホームの警告や設定画面から状態を確認できます。',
            ),
          ),
"""
extra = needle + """          const SizedBox(height: 12),
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
"""
if needle not in s:
    raise SystemExit('missing Android constraints info card')
s = s.replace(needle, extra, 1)

p.write_text(s)
