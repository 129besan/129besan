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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.BrowserBlockService
import dev.besan.browserbrake.NotificationController
import dev.besan.browserbrake.Place
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.UnlockGateActivity
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.rules.TargetGroupCatalog
import kotlinx.coroutines.delay

private enum class AppTab(val label: String, val glyph: String) {
    HOME("ホーム", "⌂"),
    RULES("ルール", "☷"),
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

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            if (selectedTab == AppTab.RULES) {
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
            AppTab.RULES -> RulesScreen(
                modifier = Modifier.padding(padding),
                rules = rules,
                onEdit = { editingRuleId = it },
                onChanged = { revision++ }
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
            Text("Browser Brake", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "必要なときは使える。でも、衝動では開かない。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Text("ルール", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (rules.isEmpty()) {
            item {
                Card {
                    Text(
                        "まだルールがありません。「ルール」タブから作成できます。",
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, onClick = { onEdit(rule.id) })
            }
        }
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
                            Text("適用中のルール", style = MaterialTheme.typography.labelLarge)
                            Text(RuleConfig.ruleName(context), fontWeight = FontWeight.SemiBold)
                            Text("今日の利用　${formatDuration(usage)}")
                            Text("今日の利用回数　${sessions}回")
                        }
                    }
                    TextButton(onClick = { showTechnical = !showTechnical }) {
                        Text(if (showTechnical) "技術情報を隠す" else "技術情報を表示")
                    }
                    if (showTechnical) {
                        Text(
                            buildString {
                                append("内部状態: ").append(state).append("\n")
                                append("Rule ID: ").append(activeRuleId.ifBlank { "なし" }).append("\n")
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
    if (Prefs.isOverDailyLimit(context) && state != Prefs.STATE_SESSION) {
        return "今日の通常利用は終了しています" to
            "設定した1日の利用上限に達しています。必要な場合は、通常より強い解除条件を達成すると短時間だけ利用できます。"
    }

    return when (state) {
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
            val joiner = if (RuleConfig.challengeAll(context)) " すべて達成すると利用できます。" else " どれか1つを達成すると利用できます。"
            "開く前に解除条件があります" to (conditions.joinToString("\n") + joiner)
        }
        Prefs.STATE_READY ->
            "解除条件は達成済みです" to
                "まだ自動ではアプリを開きません。「利用時間を選ぶ」から今回使う時間を決めると利用を始められます。"
        Prefs.STATE_RECOVERY -> {
            val left = (Prefs.recoveryDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L)
            "いまは利用後の休憩中です" to
                "前回の利用後に設定した休憩時間が残っています。あと${formatDuration(left)}で、もう一度解除条件に進めます。"
        }
        else -> "現在はブロックされていません" to "対象アプリを開くと、設定したルールが適用されます。"
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
    val over = Prefs.isOverDailyLimit(context)
    val tone = runtimeTone(state, over)
    val ruleName = if (state == Prefs.STATE_LOCKED) null else RuleConfig.ruleName(context)

    val title = when {
        over && state != Prefs.STATE_SESSION -> "今日の通常利用は終了しています"
        state == Prefs.STATE_CHALLENGING -> "解除条件を進めています"
        state == Prefs.STATE_READY -> "利用する準備ができました"
        state == Prefs.STATE_SESSION -> "${ruleName ?: "対象アプリ"}を利用中"
        state == Prefs.STATE_RECOVERY -> "利用後の休憩中"
        else -> "今日は落ち着いています"
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = tone.container)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                ruleName ?: "Browser Brake",
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
                else -> Text("設定したアプリを開くと、そのアプリに対応するルールが働きます。", color = tone.content)
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
private fun RulesScreen(
    modifier: Modifier,
    rules: List<BrowserRule>,
    onEdit: (String) -> Unit,
    onChanged: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("ルール", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "アプリごとに、どこで・どう止めるかをまとめます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(rules, key = { it.id }) { rule ->
            RuleCard(rule = rule, onClick = { onEdit(rule.id) })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun RuleCard(
    rule: BrowserRule,
    onClick: () -> Unit
) {
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
    val brakeSummary = challengeRuleSummary(rule)
    val usage = RuleRepository.dailyUsageRaw(context, rule.id)
    val sessions = RuleRepository.dailySessionsRaw(context, rule.id)
    val usageProgress = if (rule.dailyUsageLimitMs > 0L) {
        (usage.toFloat() / rule.dailyUsageLimitMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    ElevatedCard(onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RuleIconBadge(rule)
                Column(Modifier.weight(1f)) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        TargetGroupCatalog.targetSummary(context, rule),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
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

            Text("📍  ${placeSummary}", style = MaterialTheme.typography.bodyMedium)
            Text("⏱  ${brakeSummary}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "→ 1回 最大${formatDuration(rule.defaultSessionUsageMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (rule.dailyUsageLimitMs > 0L) {
                LinearProgressIndicator(
                    progress = { usageProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "今日 ${formatDuration(usage)} / ${formatDuration(rule.dailyUsageLimitMs)}" +
                        if (rule.dailySessionLimit >= 0) "　・　${sessions} / ${rule.dailySessionLimit}回" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (rule.dailySessionLimit >= 0) {
                Text(
                    "今日 ${sessions} / ${rule.dailySessionLimit}回",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
private fun RuleControlDialog(
    rule: BrowserRule,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf(false) }
    val enableConflicts = if (!rule.enabled) {
        RuleRepository.conflicts(context, rule.copy(enabled = true))
    } else emptyList()

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("ルールを無効にしますか？") },
            text = { Text("Brakeが完全に止まる変更です。再度有効にするまでこのルールは動きません。") },
            confirmButton = {
                Button(onClick = {
                    RuleRepository.setEnabled(context, rule.id, false)
                    onChanged()
                }) { Text("無効にする") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("戻る") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!rule.enabled) {
                    if (enableConflicts.isNotEmpty()) {
                        Text(
                            "有効化できません。対象が「" + enableConflicts.joinToString("、") + "」と重複しています。",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enableConflicts.isEmpty(),
                        onClick = {
                            RuleRepository.setEnabled(context, rule.id, true)
                            onChanged()
                        }
                    ) { Text("ルールを有効にする") }
                } else {
                    if (rule.pausedUntilMs > System.currentTimeMillis()) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                RuleRepository.pauseRule(context, rule.id, 0L)
                                onChanged()
                            }
                        ) { Text("今すぐ再開") }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                RuleRepository.pauseRule(context, rule.id, System.currentTimeMillis() + 15 * 60_000L)
                                onChanged()
                            }
                        ) { Text("15分だけ停止") }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                RuleRepository.pauseRule(context, rule.id, System.currentTimeMillis() + 60 * 60_000L)
                                onChanged()
                            }
                        ) { Text("1時間停止") }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDisable = true }
                    ) { Text("ルールを無効にする") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
private fun RecordsScreen(modifier: Modifier, rules: List<BrowserRule>) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("今日の利用", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "ルールごとの利用時間と利用回数を、この端末の中だけで記録します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(rules, key = { it.id }) { rule ->
            val usage = RuleRepository.dailyUsageRaw(context, rule.id)
            val sessions = RuleRepository.dailySessionsRaw(context, rule.id)
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("実使用　${formatDuration(usage)}")
                    Text("利用回数　${sessions}回")
                    Text(
                        "繰り返し利用の強さ　${RuleRepository.storedEscalationLevel(context, rule.id)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
            Text("設定", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
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
                "場所名は自由です。ルールではここで登録した場所を複数選べます。",
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
