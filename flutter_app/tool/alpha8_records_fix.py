from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'missing pattern: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# RuleRepository: persist whether the normal daily limit was reached.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/rules/RuleRepository.kt')
s = p.read_text()
s = replace_once(
    s,
    '''data class DailyRecord(\n    val dayKey: String,\n    val label: String,\n    val usageMs: Long,\n    val sessions: Int,\n    val hasData: Boolean,\n    val commitmentBroken: Boolean = false\n)\n''',
    '''data class DailyRecord(\n    val dayKey: String,\n    val label: String,\n    val usageMs: Long,\n    val sessions: Int,\n    val hasData: Boolean,\n    val commitmentBroken: Boolean = false,\n    val overLimit: Boolean = false\n)\n''',
    'DailyRecord overLimit',
)

s = replace_once(
    s,
    '''    @JvmStatic\n    fun dailySessionsRaw(context: Context, ruleId: String): Int {\n        ensureRuleDay(context, ruleId)\n        return Prefs.p(context).getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)\n    }\n\n''',
    '''    @JvmStatic\n    fun dailySessionsRaw(context: Context, ruleId: String): Int {\n        ensureRuleDay(context, ruleId)\n        return Prefs.p(context).getInt(ruleMetricKey(ruleId, "daily_sessions"), 0)\n    }\n\n    @JvmStatic\n    fun markDailyLimitReached(context: Context, ruleId: String) {\n        if (ruleId.isBlank()) return\n        ensureRuleDay(context, ruleId)\n        Prefs.p(context).edit()\n            .putBoolean(ruleMetricKey(ruleId, "daily_limit_reached"), true)\n            .apply()\n    }\n\n''',
    'daily limit marker helper',
)

s = replace_once(
    s,
    '''        val current = Prefs.p(context).getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)\n        Prefs.p(context).edit()\n            .putLong(ruleMetricKey(ruleId, "daily_usage_ms"), current + charged)\n            .apply()\n''',
    '''        val current = Prefs.p(context).getLong(ruleMetricKey(ruleId, "daily_usage_ms"), 0L)\n        val updated = current + charged\n        Prefs.p(context).edit()\n            .putLong(ruleMetricKey(ruleId, "daily_usage_ms"), updated)\n            .apply()\n        val rule = getRule(context, ruleId)\n        if (rule != null && rule.dailyUsageLimitMs > 0L && updated >= rule.dailyUsageLimitMs) {\n            markDailyLimitReached(context, ruleId)\n        }\n''',
    'paused usage limit marker',
)

s = replace_once(
    s,
    '''                    prefs.getInt(ruleMetricKey(ruleId, "daily_sessions"), 0),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false)\n                )\n''',
    '''                    prefs.getInt(ruleMetricKey(ruleId, "daily_sessions"), 0),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "daily_limit_reached"), false)\n                )\n''',
    'archive over limit',
)

s = replace_once(
    s,
    '''                .putBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false)\n                .remove(ruleMetricKey(ruleId, "daily_commitment_break_reason"))\n''',
    '''                .putBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false)\n                .putBoolean(ruleMetricKey(ruleId, "daily_limit_reached"), false)\n                .remove(ruleMetricKey(ruleId, "daily_commitment_break_reason"))\n''',
    'reset daily limit marker',
)

s = replace_once(
    s,
    '''        sessions: Int,\n        commitmentBroken: Boolean = false\n    ) {\n''',
    '''        sessions: Int,\n        commitmentBroken: Boolean = false,\n        overLimit: Boolean = false\n    ) {\n''',
    'archiveDay signature',
)

s = replace_once(
    s,
    '''        val breakKey = ruleMetricKey(ruleId, "history:$dayKey:commitment_broken")\n        val broken = commitmentBroken || prefs.getBoolean(breakKey, false)\n        prefs.edit()\n            .putLong(ruleMetricKey(ruleId, "history:$dayKey:usage_ms"), usageMs)\n            .putInt(ruleMetricKey(ruleId, "history:$dayKey:sessions"), sessions)\n            .putBoolean(breakKey, broken)\n            .putBoolean(ruleMetricKey(ruleId, "history:$dayKey:present"), true)\n''',
    '''        val breakKey = ruleMetricKey(ruleId, "history:$dayKey:commitment_broken")\n        val limitKey = ruleMetricKey(ruleId, "history:$dayKey:over_limit")\n        val broken = commitmentBroken || prefs.getBoolean(breakKey, false)\n        val reachedLimit = overLimit || prefs.getBoolean(limitKey, false)\n        prefs.edit()\n            .putLong(ruleMetricKey(ruleId, "history:$dayKey:usage_ms"), usageMs)\n            .putInt(ruleMetricKey(ruleId, "history:$dayKey:sessions"), sessions)\n            .putBoolean(breakKey, broken)\n            .putBoolean(limitKey, reachedLimit)\n            .putBoolean(ruleMetricKey(ruleId, "history:$dayKey:present"), true)\n''',
    'archiveDay store marker',
)

s = replace_once(
    s,
    '''                val broken = prefs.getBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false)\n                DailyRecord(key, label, usage, sessions, usage > 0L || sessions > 0 || broken, broken)\n''',
    '''                val broken = prefs.getBoolean(ruleMetricKey(ruleId, "daily_commitment_broken"), false)\n                val overLimit = prefs.getBoolean(ruleMetricKey(ruleId, "daily_limit_reached"), false)\n                DailyRecord(\n                    key, label, usage, sessions,\n                    usage > 0L || sessions > 0 || broken || overLimit,\n                    broken, overLimit\n                )\n''',
    'today DailyRecord',
)

s = replace_once(
    s,
    '''                    prefs.getBoolean(presentKey, false),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "history:$key:commitment_broken"), false)\n                )\n''',
    '''                    prefs.getBoolean(presentKey, false),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "history:$key:commitment_broken"), false),\n                    prefs.getBoolean(ruleMetricKey(ruleId, "history:$key:over_limit"), false)\n                )\n''',
    'history DailyRecord',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Runtime: mark the daily-limit state as soon as it is reached.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/java/dev/besan/browserbrake/runtime/RuleRuntimeStore.kt')
s = p.read_text()
s = replace_once(
    s,
    '''        val nextLevel = 0\n        val sessions = RuleRepository.dailySessionsRaw(context, ruleId)\n\n        Prefs.p(context).edit()\n''',
    '''        val nextLevel = 0\n        val sessions = RuleRepository.dailySessionsRaw(context, ruleId)\n        val nextSessions = sessions + 1\n\n        Prefs.p(context).edit()\n''',
    'next sessions variable',
)
s = replace_once(
    s,
    '''            .putInt(RuleRepository.ruleMetricKey(ruleId, "daily_sessions"), sessions + 1)\n''',
    '''            .putInt(RuleRepository.ruleMetricKey(ruleId, "daily_sessions"), nextSessions)\n''',
    'store next sessions',
)
s = replace_once(
    s,
    '''            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)\n            .apply()\n    }\n\n    @JvmStatic\n    fun sessionUsageRemainingMs''',
    '''            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), now)\n            .apply()\n        if (over || (rule.dailySessionLimit >= 0 && nextSessions >= rule.dailySessionLimit)) {\n            RuleRepository.markDailyLimitReached(context, ruleId)\n        }\n    }\n\n    @JvmStatic\n    fun sessionUsageRemainingMs''',
    'session limit marker',
)

s = replace_once(
    s,
    '''        val currentDaily = RuleRepository.dailyUsageRaw(context, ruleId)\n\n        Prefs.p(context).edit()\n            .putLong(key(ruleId, "session_usage_remaining_ms"), remaining)\n''',
    '''        val currentDaily = RuleRepository.dailyUsageRaw(context, ruleId)\n        val updatedDaily = currentDaily + charged\n\n        Prefs.p(context).edit()\n            .putLong(key(ruleId, "session_usage_remaining_ms"), remaining)\n''',
    'updated daily variable',
)
s = replace_once(
    s,
    '''            .putLong(RuleRepository.ruleMetricKey(ruleId, "daily_usage_ms"), currentDaily + charged)\n            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), end)\n            .apply()\n    }\n''',
    '''            .putLong(RuleRepository.ruleMetricKey(ruleId, "daily_usage_ms"), updatedDaily)\n            .putLong(RuleRepository.ruleMetricKey(ruleId, "last_target_attempt"), end)\n            .apply()\n        val rule = ruleForRuntime(context, ruleId)\n        if (rule != null && rule.dailyUsageLimitMs > 0L && updatedDaily >= rule.dailyUsageLimitMs) {\n            RuleRepository.markDailyLimitReached(context, ruleId)\n        }\n    }\n''',
    'foreground usage limit marker',
)

s = replace_once(
    s,
    '''        val timeOver = rule.dailyUsageLimitMs > 0L && usage >= rule.dailyUsageLimitMs\n        val sessionOver = rule.dailySessionLimit >= 0 && sessions >= rule.dailySessionLimit\n        return timeOver || sessionOver\n''',
    '''        val timeOver = rule.dailyUsageLimitMs > 0L && usage >= rule.dailyUsageLimitMs\n        val sessionOver = rule.dailySessionLimit >= 0 && sessions >= rule.dailySessionLimit\n        val over = timeOver || sessionOver\n        if (over) RuleRepository.markDailyLimitReached(context, rule.id)\n        return over\n''',
    'isOver marker',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Flutter bridge: aggregate the new daily marker across all rules.
# -----------------------------------------------------------------------------
p = Path('android/app/src/main/kotlin/dev/besan/browserbrake/FlutterBridge.kt')
s = p.read_text()
s = replace_once(
    s,
    '''            val commitmentBroken = rows.any { it.commitmentBroken }\n            val hasActivity = usage > 0L || sessions > 0\n            mapOf(\n''',
    '''            val commitmentBroken = rows.any { it.commitmentBroken }\n            val overLimit = rows.any { it.overLimit }\n            val hasActivity = usage > 0L || sessions > 0\n            mapOf(\n''',
    'aggregate overLimit',
)
s = replace_once(
    s,
    '''                "hasActivity" to hasActivity,\n                "hasData" to (hasActivity || commitmentBroken),\n                "commitmentBroken" to commitmentBroken\n''',
    '''                "hasActivity" to hasActivity,\n                "hasData" to (hasActivity || commitmentBroken || overLimit),\n                "commitmentBroken" to commitmentBroken,\n                "overLimit" to overLimit\n''',
    'record map overLimit',
)
p.write_text(s)

# -----------------------------------------------------------------------------
# Records UI: red = normal limit reached; orange dot = settings changed.
# -----------------------------------------------------------------------------
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace(
    "                      '高さは利用時間。赤い点は一時停止・無効化などの設定変更があった日です。',\n",
    "                      '高さは利用時間。赤い棒は通常利用の上限に達した日、橙の点は設定変更です。',\n",
    1,
)
s = replace_once(
    s,
    '''                              color: record['commitmentBroken'] == true\n                                  ? const Color(0xFF9BCBE8)\n                                  : const Color(0xFF5FA8D3),\n''',
    '''                              color: record['overLimit'] == true\n                                  ? const Color(0xFFE79A9A)\n                                  : const Color(0xFF5FA8D3),\n''',
    'chart overLimit color',
)
s = s.replace('color: Color(0xFFD46A6A),', 'color: Color(0xFFF0A84B),', 1)

s = replace_once(
    s,
    '''    final changed = record['commitmentBroken'] == true;\n    return Padding(\n''',
    '''    final changed = record['commitmentBroken'] == true;\n    final overLimit = record['overLimit'] == true;\n    return Padding(\n''',
    'record row overLimit variable',
)

old_badge = '''          if (changed)\n            Container(\n              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),\n              decoration: BoxDecoration(\n                color: const Color(0xFFFFEAEA),\n                borderRadius: BorderRadius.circular(99),\n              ),\n              child: const Text(\n                '設定変更あり',\n                style: TextStyle(\n                  color: Color(0xFF9A4646),\n                  fontSize: 11,\n                  fontWeight: FontWeight.w800,\n                ),\n              ),\n            ),\n'''
new_badge = '''          if (overLimit || changed)\n            Column(\n              crossAxisAlignment: CrossAxisAlignment.end,\n              children: [\n                if (overLimit)\n                  Container(\n                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),\n                    decoration: BoxDecoration(\n                      color: const Color(0xFFFFE7E7),\n                      borderRadius: BorderRadius.circular(99),\n                    ),\n                    child: const Text(\n                      '通常上限',\n                      style: TextStyle(\n                        color: Color(0xFF984848),\n                        fontSize: 10.5,\n                        fontWeight: FontWeight.w800,\n                      ),\n                    ),\n                  ),\n                if (overLimit && changed) const SizedBox(height: 4),\n                if (changed)\n                  Container(\n                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),\n                    decoration: BoxDecoration(\n                      color: const Color(0xFFFFF0D8),\n                      borderRadius: BorderRadius.circular(99),\n                    ),\n                    child: const Text(\n                      '設定変更',\n                      style: TextStyle(\n                        color: Color(0xFF805D17),\n                        fontSize: 10.5,\n                        fontWeight: FontWeight.w800,\n                      ),\n                    ),\n                  ),\n              ],\n            ),\n'''
s = replace_once(s, old_badge, new_badge, 'record row badges')

s = s.replace(
    "                    _UsageWeekChart(records: week),\n",
    "                    _UsageWeekChart(records: week),\n                    const SizedBox(height: 8),\n                    Text(\n                      '1日は午前4時に切り替わります',\n                      style: Theme.of(context).textTheme.bodySmall?.copyWith(\n                            color: const Color(0xFF718797),\n                          ),\n                    ),\n",
    1,
)
p.write_text(s)
