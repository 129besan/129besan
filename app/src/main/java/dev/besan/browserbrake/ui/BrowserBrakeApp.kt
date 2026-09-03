package dev.besan.browserbrake.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.BrowserBlockService
import dev.besan.browserbrake.NotificationController
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.RuleConfig
import dev.besan.browserbrake.UnlockGateActivity
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.DailyRecord
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.rules.TargetGroupCatalog
import kotlinx.coroutines.delay

private enum class AppTab(val label: String, val glyph: String) {
    HOME("ホーム", "⌂"),
    RECORDS("記録", "◷"),
    SETTINGS("設定", "⚙")
}

@Composable
fun BrowserBrakeApp() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    BackHandler(enabled = editingRuleId != null) {
        editingRuleId = null
        revision++
    }

    if (editingRuleId != null) {
        RuleEditorScreen(
            ruleId = editingRuleId!!,
            onBack = {
                editingRuleId = null
                revision++
            },
            onDeleted = {
                editingRuleId = null
                revision++
            }
        )
        return
    }

    val rules = remember(revision, tick / 5) { RuleRepository.getRules(context) }
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tab.glyph) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == AppTab.HOME) {
                    FloatingActionButton(onClick = {
                        val newRule = RuleRepository.createRule(context)
                        editingRuleId = newRule.id
                        revision++
                    }) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        ) { padding ->
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    rules = rules,
                    tick = tick,
                    onEdit = { editingRuleId = it }
                )
                AppTab.RECORDS -> RecordsScreen(
                    modifier = Modifier.padding(padding),
                    rules = rules
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onChanged = { revision++ }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    rules: List<BrowserRule>,
    tick: Int,
    onEdit: (String) -> Unit
) {
    val context = LocalContext.current
    val state = Prefs.state(context)
    val activeRuleId = RuleRepository.activeRuntimeRuleId(context)
    val activeRule = rules.firstOrNull { it.id == activeRuleId }
    var showWhy by remember { mutableStateOf(false) }
    var showTechnical by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("AppLockout", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        item {
            RuntimeCard(
                state = state,
                activeRule = activeRule,
                tick = tick,
                onChooseTime = {
                    context.startActivity(Intent(context, UnlockGateActivity::class.java))
                },
                onDeclineReady = {
                    Prefs.declineReady(context)
                    NotificationController.cancel(context)
                    BrowserBlockService.requestRuntimeSync()
                },
                onEndSession = {
                    if (Prefs.state(context) == Prefs.STATE_SESSION) {
                        Prefs.finishSession(context)
                        if (Prefs.state(context) == Prefs.STATE_RECOVERY) {
                            NotificationController.showRecovery(context)
                        } else {
                            NotificationController.cancel(context)
                        }
                        BrowserBlockService.requestRuntimeSync()
                        Toast.makeText(context, "利用を終了しました", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (state == Prefs.STATE_CHALLENGING ||
            state == Prefs.STATE_READY ||
            state == Prefs.STATE_RECOVERY) {
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showWhy = true }
                ) { Text("なぜ今は使えない？") }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("制限", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${rules.size}件",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (rules.isEmpty()) {
            item {
                Card {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("まだ制限がありません", fontWeight = FontWeight.SemiBold)
                        Text("右下の＋から、対象アプリと解除条件を設定できます。")
                    }
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RestrictionCard(rule = rule, onClick = { onEdit(rule.id) })
            }
        }

        item { Spacer(Modifier.height(70.dp)) }
    }

    if (showWhy) {
        val usage = activeRule?.let { RuleRepository.dailyUsageRaw(context, it.id) } ?: 0L
        val sessions = activeRule?.let { RuleRepository.dailySessionsRaw(context, it.id) } ?: 0
        val explanation = humanBlockExplanation(context, state)
        AlertDialog(
            onDismissRequest = {
                showWhy = false
                showTechnical = false
            },
            title = { Text(explanation.first) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(explanation.second)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("適用中の制限", style = MaterialTheme.typography.labelLarge)
                            Text(RuleConfig.ruleName(context), fontWeight = FontWeight.SemiBold)
                            if (activeRule?.fullLock != true) {
                                Text("今日の利用　${formatDuration(usage)}")
                                Text("今日の利用回数　${sessions}回")
                            }
                        }
                    }
                    TextButton(onClick = { showTechnical = !showTechnical }) {
                        Text(if (showTechnical) "技術情報を隠す" else "技術情報を表示")
                    }
                    if (showTechnical) {
                        Text(
                            buildString {
                                append("内部状態: ").append(state).append("\n")
                                append("Restriction ID: ").append(activeRuleId.ifBlank { "なし" }).append("\n")
                                append("実使用残り: ").append(formatDuration(Prefs.liveSessionUsageRemainingMs(context))).append("\n")
                                append("利用後の休憩残り: ")
                                    .append(formatDuration((Prefs.recoveryDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWhy = false
                    showTechnical = false
                }) { Text("閉じる") }
            }
        )
    }
}

private fun humanBlockExplanation(context: Context, state: String): Pair<String, String> {
    val over = Prefs.isOverDailyLimit(context)
    return when (state) {
        Prefs.STATE_RECOVERY -> {
            val left = (Prefs.recoveryDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
            "いまは利用後の休憩中です" to
                "前回の利用後に設定した休憩時間が残っています。あと${formatDuration(left)}で、もう一度解除条件に進めます。"
        }
        Prefs.STATE_CHALLENGING -> {
            val conditions = buildList {
                if (RuleConfig.challengeWait(context)) {
                    val left = (Prefs.challengeWaitDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
                    add("待ち時間はあと${formatDuration(left)}です。待っている間はほかの操作をしても構いません。")
                }
                if (RuleConfig.challengePhoneBreak(context)) {
                    val left = (Prefs.challengePhoneDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
                    add("スマホ休憩はあと${formatDuration(left)}です。スマホを操作すると最初からやり直しになります。")
                }
                if (RuleConfig.challengeWalk(context)) {
                    add("${Prefs.challengeRequiredSteps(context)}歩、歩く必要があります。")
                }
            }
            val joiner = if (RuleConfig.challengeAll(context)) {
                " すべて達成すると利用できます。"
            } else {
                " どれか1つを達成すると利用できます。"
            }
            val prefix = if (over) {
                "今日の通常利用上限に達しているため、通常より強い解除条件になっています。\n"
            } else ""
            (if (over) "今日の上限を超えたあとの解除条件です" else "開く前に解除条件があります") to
                (prefix + conditions.joinToString("\n") + joiner)
        }
        Prefs.STATE_READY ->
            (if (over) "短時間の追加利用を始められます" else "解除条件は達成済みです") to
                (if (over) {
                    "今日の通常利用は終了していますが、解除条件を達成したため短時間だけ追加利用できます。利用時間を選んでください。"
                } else {
                    "まだ自動ではアプリを開きません。「利用時間を選ぶ」から今回使う時間を決めると利用を始められます。"
                })
        else -> {
            if (over) {
                "今日の通常利用は終了しています" to
                    "設定した1日の利用上限に達しています。必要な場合は、通常より強い解除条件を達成すると短時間だけ利用できます。"
            } else {
                "現在はブロックされていません" to "対象アプリを開くと、設定した制限が適用されます。"
            }
        }
    }
}

@Composable
private fun RuntimeCard(
    state: String,
    activeRule: BrowserRule?,
    tick: Int,
    onChooseTime: () -> Unit,
    onDeclineReady: () -> Unit,
    onEndSession: () -> Unit
) {
    val context = LocalContext.current
    val over = state != Prefs.STATE_LOCKED && Prefs.isOverDailyLimit(context)
    val emphasizeOverLimit = over && (state == Prefs.STATE_CHALLENGING || state == Prefs.STATE_READY)
    val tone = runtimeTone(state, emphasizeOverLimit)
    val restrictionName = if (state == Prefs.STATE_LOCKED) null else RuleConfig.ruleName(context)

    val title = when (state) {
        Prefs.STATE_CHALLENGING -> if (over) "上限を超えたあとの解除条件" else "解除条件を進めています"
        Prefs.STATE_READY -> if (over) "短時間の追加利用を始められます" else "利用する準備ができました"
        Prefs.STATE_SESSION -> "${restrictionName ?: "対象アプリ"}を利用中"
        Prefs.STATE_RECOVERY -> "利用後の休憩中"
        else -> "今日は落ち着いています"
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = tone.container)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (activeRule != null && state != Prefs.STATE_LOCKED) {
                TargetAppIcons(context, activeRule)
            }
            Text(
                restrictionName ?: "AppLockout",
                style = MaterialTheme.typography.labelLarge,
                color = tone.accent,
                fontWeight = FontWeight.SemiBold
            )
            Text(title, style = MaterialTheme.typography.headlineSmall, color = tone.content)

            when (state) {
                Prefs.STATE_CHALLENGING -> Text(challengeHomeText(context), color = tone.content)
                Prefs.STATE_READY -> {
                    Text("解除条件は完了しています。必要なら、今回使う時間を決めてください。", color = tone.content)
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onChooseTime) {
                        Text("利用時間を選ぶ")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDeclineReady) {
                        Text("今回はやめる")
                    }
                }
                Prefs.STATE_SESSION -> {
                    val usage = Prefs.liveSessionUsageRemainingMs(context)
                    val wall = (Prefs.sessionWallDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
                    Text("実際に使える時間　${formatDuration(usage)}", color = tone.content, fontWeight = FontWeight.SemiBold)
                    Text("この利用の有効期限　${formatDuration(wall)}", color = tone.content)
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onEndSession) {
                        Text("利用を終了する")
                    }
                    Text(
                        "終了すると、残っている利用時間は破棄されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tone.content.copy(alpha = 0.78f)
                    )
                }
                Prefs.STATE_RECOVERY -> {
                    val left = (Prefs.recoveryDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
                    Text("あと ${formatDuration(left)}", style = MaterialTheme.typography.titleLarge, color = tone.content)
                    Text("休憩が終わると、もう一度解除条件に進めます。", color = tone.content)
                }
                else -> Text("対象アプリを開くと、対応する制限が働きます。", color = tone.content)
            }
        }
    }
}

private fun challengeHomeText(context: Context): String {
    val parts = buildList {
        if (RuleConfig.challengeWait(context)) {
            add("待つ　あと${formatDuration((Prefs.challengeWaitDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L))}")
        }
        if (RuleConfig.challengePhoneBreak(context)) {
            add("スマホ休憩　あと${formatDuration((Prefs.challengePhoneDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L))}")
        }
        if (RuleConfig.challengeWalk(context)) {
            add("歩く　${Prefs.challengeRequiredSteps(context)}歩")
        }
    }
    return parts.joinToString("\n").ifBlank { "解除条件を確認しています。" }
}

@Composable
private fun RestrictionCard(rule: BrowserRule, onClick: () -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val paused = rule.enabled && rule.pausedUntilMs > now
    val status = when {
        !rule.enabled -> "無効"
        paused -> "一時停止中"
        else -> "有効"
    }
    val statusContainer = when {
        !rule.enabled -> MaterialTheme.colorScheme.surfaceVariant
        paused -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusContent = when {
        !rule.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        paused -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val placeSummary = if (rule.allPlaces) "すべての場所" else {
        PlaceStore.all(context)
            .filter { it.id in rule.placeIds }
            .map { it.name }
            .ifEmpty { listOf("場所が選ばれていません") }
            .joinToString("・")
    }
    val usage = RuleRepository.dailyUsageRaw(context, rule.id)
    val sessions = RuleRepository.dailySessionsRaw(context, rule.id)
    val usageProgress = if (rule.dailyUsageLimitMs > 0L) {
        (usage.toFloat() / rule.dailyUsageLimitMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    ElevatedCard(onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TargetAppIcons(context, rule)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusContainer,
                    contentColor = statusContent
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider()
            Text("📍  $placeSummary", style = MaterialTheme.typography.bodyMedium)

            if (rule.fullLock) {
                Text("🔒  完全ロック", fontWeight = FontWeight.SemiBold)
                Text(
                    "この制限が有効な場所では対象アプリを開けません。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("⏱  ${challengeRuleSummary(rule)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "1回 最大${formatDuration(rule.defaultSessionUsageMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (rule.dailyUsageLimitMs > 0L) {
                    LinearProgressIndicator(
                        progress = { usageProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    todayCompactSummary(rule, usage, sessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun todayCompactSummary(rule: BrowserRule, usage: Long, sessions: Int): String {
    val parts = buildList {
        if (rule.dailyUsageLimitMs >= 0L) {
            add("今日 ${formatDuration(usage)} / ${formatDuration(rule.dailyUsageLimitMs)}")
        } else {
            add("今日 ${formatDuration(usage)}")
        }
        if (rule.dailySessionLimit >= 0) {
            add("$sessions / ${rule.dailySessionLimit}回")
        } else {
            add("${sessions}回")
        }
    }
    return parts.joinToString("　・　")
}

private fun challengeRuleSummary(rule: BrowserRule): String {
    val parts = buildList {
        if (rule.challengeWait) add("待つ ${formatDuration(rule.waitMs)}")
        if (rule.challengePhoneBreak) add("スマホ休憩 ${formatDuration(rule.phoneBreakMs)}")
        if (rule.challengeWalk) add("歩く ${rule.walkSteps}歩")
    }
    if (parts.isEmpty()) return "解除条件なし"
    val suffix = if (parts.size > 1) {
        if (rule.challengeAll) "（すべて）" else "（どれか1つ）"
    } else ""
    return parts.joinToString(" + ") + suffix
}

@Composable
private fun RecordsScreen(modifier: Modifier, rules: List<BrowserRule>) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("記録", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "長い流れと、続けられている日数を確認します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (rules.isEmpty()) {
            item {
                Card {
                    Text("制限を使い始めると、ここに推移が表示されます。", modifier = Modifier.padding(18.dp))
                }
            }
        }

        items(rules, key = { it.id }) { rule ->
            HistoryRestrictionCard(context, rule)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HistoryRestrictionCard(context: Context, rule: BrowserRule) {
    val chartRecords = RuleRepository.historyRecords(context, rule.id, 30)
    val streakRecords = RuleRepository.historyRecords(context, rule.id, 90)
    val streak = currentStreak(streakRecords, rule)
    val recorded = chartRecords.filter { it.hasData }
    val successDays = recorded.count { goalMet(it, rule) }
    val averageUsage = if (recorded.isEmpty()) 0L else recorded.sumOf { it.usageMs } / recorded.size

    ElevatedCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TargetAppIcons(context, rule)
                }
                if (!rule.fullLock && streak > 0) {
                    StreakBadge(streak)
                }
            }

            if (rule.fullLock) {
                Text("完全ロック", fontWeight = FontWeight.SemiBold)
                Text(
                    "完全ロックのブロック試行履歴はまだ記録していません。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("30日間の利用時間", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                UsageHistoryChart(chartRecords, rule)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HistoryMetric("記録", "${recorded.size}日")
                    HistoryMetric("上限内", "${successDays}日")
                    HistoryMetric("平均", if (recorded.isEmpty()) "—" else formatDuration(averageUsage))
                }

                Text(
                    when {
                        streak >= 30 -> "${streak}日連続で上限内です。"
                        streak >= 7 -> "${streak}日連続。かなり安定して続いています。"
                        streak >= 3 -> "${streak}日連続で上限内です。"
                        streak > 0 -> "${streak}日連続で上限内です。"
                        recorded.isNotEmpty() -> "上限内の日が続くとストリークが伸びます。"
                        else -> "使い始めると、30日間の推移がここにたまります。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageHistoryChart(records: List<DailyRecord>, rule: BrowserRule) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceContainerHighest
    val error = MaterialTheme.colorScheme.error
    val limitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxUsage = records.maxOfOrNull { it.usageMs } ?: 0L
    val scaleMax = maxOf(
        maxUsage,
        if (rule.dailyUsageLimitMs > 0L) rule.dailyUsageLimitMs else 0L,
        60_000L
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (records.isEmpty()) return@Canvas
            val gap = 2.dp.toPx()
            val count = records.size
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            records.forEachIndexed { index, record ->
                val x = index * (barWidth + gap)
                if (!record.hasData) {
                    drawRoundRect(
                        color = muted,
                        topLeft = Offset(x, size.height - 3.dp.toPx()),
                        size = Size(barWidth, 3.dp.toPx()),
                        cornerRadius = radius
                    )
                } else {
                    val ratio = (record.usageMs.toFloat() / scaleMax.toFloat()).coerceIn(0f, 1f)
                    val height = (size.height * ratio).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (goalMet(record, rule)) primary else error,
                        topLeft = Offset(x, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius
                    )
                }
            }

            if (rule.dailyUsageLimitMs > 0L) {
                val ratio = (rule.dailyUsageLimitMs.toFloat() / scaleMax.toFloat()).coerceIn(0f, 1f)
                val y = size.height * (1f - ratio)
                drawLine(
                    color = limitColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("30日前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (rule.dailyUsageLimitMs > 0L) {
                Text(
                    "横線: 1日の上限 ${formatDuration(rule.dailyUsageLimitMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("今日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val transition = rememberInfiniteTransition(label = "streak")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = when {
            streak >= 30 -> 1.13f
            streak >= 7 -> 1.09f
            else -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                when {
                    streak >= 30 -> 700
                    streak >= 7 -> 950
                    else -> 1400
                }
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakScale"
    )
    Surface(
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            "${streak}日連続",
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun goalMet(record: DailyRecord, rule: BrowserRule): Boolean {
    val timeOk = rule.dailyUsageLimitMs < 0L || record.usageMs <= rule.dailyUsageLimitMs
    val sessionOk = rule.dailySessionLimit < 0 || record.sessions <= rule.dailySessionLimit
    return timeOk && sessionOk
}

private fun currentStreak(records: List<DailyRecord>, rule: BrowserRule): Int {
    if (records.isEmpty()) return 0
    val source = if (!records.last().hasData) records.dropLast(1) else records
    var streak = 0
    for (record in source.asReversed()) {
        if (!record.hasData || !goalMet(record, rule)) break
        streak++
    }
    return streak
}

@Composable
private fun SettingsScreen(modifier: Modifier, onChanged: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var addPlaceDialog by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("") }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { revision++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { revision++ }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { revision++ }

    val places = remember(revision) { PlaceStore.all(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("設定", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        item {
            SectionTitle("動作チェック")
            HealthRow("Accessibility", accessibilityEnabled(context)) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            HealthRow(
                "通知",
                Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            HealthRow(
                "位置情報",
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            if (Build.VERSION.SDK_INT >= 29) {
                HealthRow(
                    "歩数",
                    context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                ) {
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }

        item {
            SectionTitle("場所")
            Text(
                "場所名は自由です。制限では、ここで登録した場所を複数選べます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(places, key = { it.id }) { place ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(place.name, fontWeight = FontWeight.SemiBold)
                        Text("半径 ${place.radiusM.toInt()}m", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        PlaceStore.delete(context, place.id)
                        revision++
                        onChanged()
                    }) { Text("削除") }
                }
            }
        }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    } else {
                        addPlaceDialog = true
                    }
                }
            ) { Text("現在地を場所として追加") }
        }

        item {
            SectionTitle("プライバシー")
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("この端末の中で完結", style = MaterialTheme.typography.titleMedium)
                    Text("アカウント・広告・アクセス解析・クラウド通信は使いません。")
                    Text(
                        "Accessibilityでは画面の内容を読み取りません。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            ) { Text("Androidのアプリ設定を開く") }
        }
    }

    if (addPlaceDialog) {
        AlertDialog(
            onDismissRequest = { addPlaceDialog = false },
            title = { Text("現在地を登録") },
            text = {
                OutlinedTextField(
                    value = placeName,
                    onValueChange = { placeName = it },
                    label = { Text("場所名") },
                    placeholder = { Text("自宅、大学、実家など") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val location = bestLastKnownLocation(context)
                    if (location == null) {
                        Toast.makeText(context, "現在地を取得できませんでした", Toast.LENGTH_LONG).show()
                    } else {
                        val name = placeName.trim().ifBlank { "場所 ${places.size + 1}" }
                        PlaceStore.add(context, name, location.latitude, location.longitude, 200f)
                        placeName = ""
                        addPlaceDialog = false
                        revision++
                        onChanged()
                    }
                }) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { addPlaceDialog = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun HealthRow(label: String, healthy: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(
                if (healthy) "OK" else "要設定",
                color = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

private fun accessibilityEnabled(context: Context): Boolean {
    return runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val mine = android.content.ComponentName(context, BrowserBlockService::class.java)
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo?.serviceInfo ?: return@any false
            val component = android.content.ComponentName(service.packageName, service.name)
            TextUtils.equals(component.flattenToString(), mine.flattenToString()) ||
                TextUtils.equals(component.flattenToShortString(), mine.flattenToShortString())
        }
    }.getOrDefault(false)
}

private fun bestLastKnownLocation(context: Context): Location? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull()
}

internal fun formatDuration(ms: Long): String {
    if (ms < 0L) return "制限なし"
    val sec = (ms.coerceAtLeast(0L) + 999L) / 1000L
    if (sec < 60L) return "${sec}秒"
    val min = sec / 60L
    if (min < 60L) return "${min}分"
    val h = min / 60L
    val rem = min % 60L
    return if (rem == 0L) "${h}時間" else "${h}時間${rem}分"
}
