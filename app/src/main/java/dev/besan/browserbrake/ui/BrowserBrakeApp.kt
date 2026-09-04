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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
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
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import kotlinx.coroutines.delay

private enum class AppTab(val label: String, val glyph: String) {
    HOME("ホーム", "⌂"),
    RECORDS("記録", "◷"),
    SETTINGS("設定", "⚙")
}

@Composable
fun BrowserBrakeApp(homeRequestToken: Int = 0) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var newRuleDraft by remember { mutableStateOf<BrowserRule?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var recordsEntryToken by rememberSaveable { mutableStateOf(0) }
    var showOnboarding by remember { mutableStateOf(shouldShowOnboarding(context)) }
    var onboardingReplay by remember { mutableStateOf(false) }

    if (showOnboarding) {
        OnboardingScreen(
            freshInstall = !onboardingReplay,
            onComplete = {
                showOnboarding = false
                onboardingReplay = false
                selectedTab = AppTab.HOME
                revision++
            }
        )
        return
    }

    LaunchedEffect(homeRequestToken) {
        if (homeRequestToken > 0) {
            selectedTab = AppTab.HOME
            editingRuleId = null
            newRuleDraft = null
            revision++
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    BackHandler(enabled = editingRuleId != null || newRuleDraft != null) {
        editingRuleId = null
        newRuleDraft = null
        revision++
    }

    if (editingRuleId != null || newRuleDraft != null) {
        RuleEditorScreen(
            ruleId = editingRuleId,
            initialNewRule = newRuleDraft,
            onBack = {
                editingRuleId = null
                newRuleDraft = null
                revision++
            },
            onDeleted = {
                editingRuleId = null
                newRuleDraft = null
                revision++
            },
            onReturnHome = {
                editingRuleId = null
                newRuleDraft = null
                selectedTab = AppTab.HOME
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
                            onClick = {
                                if (tab == AppTab.RECORDS && selectedTab != AppTab.RECORDS) {
                                    recordsEntryToken++
                                }
                                selectedTab = tab
                            },
                            icon = { Text(tab.glyph) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == AppTab.HOME) {
                    FloatingActionButton(onClick = {
                        // New restrictions live only in Compose state until Save is pressed.
                        // Backing out therefore leaves no half-created restriction behind.
                        newRuleDraft = BrowserRule(
                            browsers = false,
                            challengePhoneBreak = true
                        )
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
                    rules = rules,
                    entryToken = recordsEntryToken
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onChanged = { revision++ },
                    onOpenGuidedRule = {
                        onboardingReplay = true
                        showOnboarding = true
                    }
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
    val activeIds = remember(tick) { RuleRuntimeStore.activeRuleIds(context).toList() }
    val activeRuntimes = activeIds.mapNotNull { ruleId ->
        val rule = RuleRuntimeStore.ruleForRuntime(context, ruleId)
            ?: rules.firstOrNull { it.id == ruleId }
        rule?.let { Triple(ruleId, RuleRuntimeStore.state(context, ruleId), it) }
    }

    var showWhyRuleId by remember { mutableStateOf<String?>(null) }
    var showTechnical by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("AppLockout", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        if (activeRuntimes.isEmpty()) {
            item {
                CalmRuntimeCard()
            }
        } else {
            items(activeRuntimes, key = { "runtime:${it.first}" }) { (ruleId, state, rule) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeCard(
                        ruleId = ruleId,
                        state = state,
                        activeRule = rule,
                        tick = tick,
                        onChooseTime = {
                            context.startActivity(
                                Intent(context, UnlockGateActivity::class.java)
                                    .putExtra(UnlockGateActivity.EXTRA_RULE_ID, ruleId)
                            )
                        },
                        onDeclineReady = {
                            RuleRuntimeStore.declineReady(context, ruleId)
                            NotificationController.cancel(context, ruleId)
                            BrowserBlockService.requestRuntimeSync()
                        },
                        onEndSession = {
                            if (RuleRuntimeStore.state(context, ruleId) == RuleRuntimeStore.STATE_SESSION) {
                                RuleRuntimeStore.finishSession(context, ruleId)
                                if (RuleRuntimeStore.state(context, ruleId) == RuleRuntimeStore.STATE_RECOVERY) {
                                    NotificationController.showRecovery(context, ruleId)
                                } else {
                                    NotificationController.cancel(context, ruleId)
                                }
                                BrowserBlockService.requestRuntimeSync()
                                Toast.makeText(context, "利用を終了しました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (state == RuleRuntimeStore.STATE_CHALLENGING ||
                        state == RuleRuntimeStore.STATE_READY ||
                        state == RuleRuntimeStore.STATE_RECOVERY) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showWhyRuleId = ruleId
                                showTechnical = false
                            }
                        ) {
                            Text("なぜ今は使えない？")
                        }
                    }
                }
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

    val whyRuleId = showWhyRuleId
    if (whyRuleId != null) {
        val rule = RuleRuntimeStore.ruleForRuntime(context, whyRuleId)
            ?: rules.firstOrNull { it.id == whyRuleId }
        val state = RuleRuntimeStore.state(context, whyRuleId)
        if (rule != null && state != RuleRuntimeStore.STATE_LOCKED) {
            val usage = RuleRepository.dailyUsageRaw(context, rule.id)
            val sessions = RuleRepository.dailySessionsRaw(context, rule.id)
            val explanation = humanBlockExplanation(context, whyRuleId, state, rule)

            AlertDialog(
                onDismissRequest = {
                    showWhyRuleId = null
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
                                Text(rule.name, fontWeight = FontWeight.SemiBold)
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
                                    append("Restriction ID: ").append(whyRuleId).append("\n")
                                    append("実使用残り: ")
                                        .append(formatDuration(RuleRuntimeStore.liveSessionUsageRemainingMs(context, whyRuleId)))
                                        .append("\n")
                                    append("利用後の休憩残り: ")
                                        .append(
                                            formatDuration(
                                                (RuleRuntimeStore.recoveryDeadline(context, whyRuleId) -
                                                    System.currentTimeMillis()).coerceAtLeast(0L)
                                            )
                                        )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showWhyRuleId = null
                        showTechnical = false
                    }) { Text("閉じる") }
                }
            )
        }
    }
}

@Composable
private fun CalmRuntimeCard() {
    val tone = runtimeTone(RuleRuntimeStore.STATE_LOCKED, false)
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = tone.container)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "AppLockout",
                style = MaterialTheme.typography.labelLarge,
                color = tone.accent,
                fontWeight = FontWeight.SemiBold
            )
            Text("現在進行中の制限はありません", style = MaterialTheme.typography.headlineSmall, color = tone.content)
            Text("対象アプリを開くと、対応する制限が働きます。", color = tone.content)
        }
    }
}

private fun humanBlockExplanation(
    context: Context,
    ruleId: String,
    state: String,
    rule: BrowserRule
): Pair<String, String> {
    val over = RuleRuntimeStore.challengeOverLimit(context, ruleId) ||
        RuleRuntimeStore.isOverDailyLimit(context, rule)

    return when (state) {
        RuleRuntimeStore.STATE_RECOVERY -> {
            val left = (RuleRuntimeStore.recoveryDeadline(context, ruleId) - System.currentTimeMillis())
                .coerceAtLeast(0L)
            "いまは利用後の休憩中です" to
                "前回の利用後に設定した休憩時間が残っています。あと${formatDuration(left)}で、もう一度解除条件に進めます。"
        }

        RuleRuntimeStore.STATE_CHALLENGING -> {
            val conditions = buildList {
                if (rule.challengeWait) {
                    val left = (RuleRuntimeStore.challengeWaitDeadline(context, ruleId) - System.currentTimeMillis())
                        .coerceAtLeast(0L)
                    add("待ち時間はあと${formatDuration(left)}です。待っている間はほかの操作をしても構いません。")
                }
                if (rule.challengePhoneBreak) {
                    val safeSince = RuleRuntimeStore.challengePhoneSafeSince(context, ruleId)
                    if (safeSince <= 0L) {
                        add("スマホ休憩は、ほかのアプリを閉じてホームまたはAppLockoutに戻ると始まります。ほかのアプリが前面にある間は時間が進みません。")
                    } else {
                        val left = (RuleRuntimeStore.challengePhoneDeadline(context, ruleId) - System.currentTimeMillis())
                            .coerceAtLeast(0L)
                        add("スマホ休憩はあと${formatDuration(left)}です。ほかのアプリを開いたり操作したりすると最初からやり直しになります。")
                    }
                }
                if (rule.challengeWalk) {
                    add("${RuleRuntimeStore.challengeRequiredSteps(context, ruleId)}歩、歩く必要があります。")
                }
            }

            val joiner = if (rule.challengeAll) {
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

        RuleRuntimeStore.STATE_READY ->
            (if (over) "短時間の追加利用を始められます" else "解除条件は達成済みです") to
                if (over) {
                    "今日の通常利用は終了していますが、解除条件を達成したため短時間だけ追加利用できます。利用時間を選んでください。"
                } else {
                    "「利用時間を選ぶ」から今回使う時間を決めると利用を始められます。"
                }

        else -> "現在はブロックされていません" to "対象アプリを開くと、設定した制限が適用されます。"
    }
}

@Composable
private fun RuntimeCard(
    ruleId: String,
    state: String,
    activeRule: BrowserRule,
    tick: Int,
    onChooseTime: () -> Unit,
    onDeclineReady: () -> Unit,
    onEndSession: () -> Unit
) {
    val context = LocalContext.current
    val over = RuleRuntimeStore.challengeOverLimit(context, ruleId) ||
        RuleRuntimeStore.isOverDailyLimit(context, activeRule)
    val emphasizeOverLimit = over &&
        (state == RuleRuntimeStore.STATE_CHALLENGING || state == RuleRuntimeStore.STATE_READY)
    val tone = runtimeTone(state, emphasizeOverLimit)

    val title = when (state) {
        RuleRuntimeStore.STATE_CHALLENGING ->
            if (over) "上限を超えたあとの解除条件" else "解除条件を進めています"
        RuleRuntimeStore.STATE_READY ->
            if (over) "短時間の追加利用を始められます" else "利用する準備ができました"
        RuleRuntimeStore.STATE_SESSION -> "${activeRule.name}を利用中"
        RuleRuntimeStore.STATE_RECOVERY -> "利用後の休憩中"
        else -> "待機中"
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = tone.container)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetAppIcons(context, activeRule)
            Text(
                activeRule.name,
                style = MaterialTheme.typography.labelLarge,
                color = tone.accent,
                fontWeight = FontWeight.SemiBold
            )
            Text(title, style = MaterialTheme.typography.headlineSmall, color = tone.content)

            when (state) {
                RuleRuntimeStore.STATE_CHALLENGING ->
                    Text(challengeHomeText(context, ruleId, activeRule), color = tone.content)

                RuleRuntimeStore.STATE_READY -> {
                    Text("解除条件は完了しています。今回使う時間を決めてください。", color = tone.content)
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onChooseTime) {
                        Text("利用時間を選ぶ")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onDeclineReady) {
                        Text("今回はやめる")
                    }
                }

                RuleRuntimeStore.STATE_SESSION -> {
                    val usage = RuleRuntimeStore.liveSessionUsageRemainingMs(context, ruleId)
                    val wall = (RuleRuntimeStore.sessionWallDeadline(context, ruleId) -
                        System.currentTimeMillis()).coerceAtLeast(0L)
                    Text(
                        "実際に使える時間　${formatDuration(usage)}",
                        color = tone.content,
                        fontWeight = FontWeight.SemiBold
                    )
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

                RuleRuntimeStore.STATE_RECOVERY -> {
                    val left = (RuleRuntimeStore.recoveryDeadline(context, ruleId) -
                        System.currentTimeMillis()).coerceAtLeast(0L)
                    Text("あと ${formatDuration(left)}", style = MaterialTheme.typography.titleLarge, color = tone.content)
                    Text("休憩が終わると、もう一度解除条件に進めます。", color = tone.content)
                }
            }
        }
    }
}

private fun challengeHomeText(context: Context, ruleId: String, rule: BrowserRule): String {
    val parts = buildList {
        if (rule.challengeWait) {
            add(
                "待つ　あと${formatDuration(
                    (RuleRuntimeStore.challengeWaitDeadline(context, ruleId) -
                        System.currentTimeMillis()).coerceAtLeast(0L)
                )}"
            )
        }
        if (rule.challengePhoneBreak) {
            val safeSince = RuleRuntimeStore.challengePhoneSafeSince(context, ruleId)
            if (safeSince <= 0L) {
                add("スマホ休憩　ほかのアプリを閉じると開始")
            } else {
                add(
                    "スマホ休憩　あと${formatDuration(
                        (RuleRuntimeStore.challengePhoneDeadline(context, ruleId) -
                            System.currentTimeMillis()).coerceAtLeast(0L)
                    )}"
                )
            }
        }
        if (rule.challengeWalk) {
            add("歩く　${RuleRuntimeStore.challengeRequiredSteps(context, ruleId)}歩")
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
private fun RecordsScreen(modifier: Modifier, rules: List<BrowserRule>, entryToken: Int) {
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
            HistoryRestrictionCard(context, rule, entryToken)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HistoryRestrictionCard(context: Context, rule: BrowserRule, entryToken: Int) {
    val chartRecords = RuleRepository.historyRecords(context, rule.id, 30)
    val streakRecords = RuleRepository.historyRecords(context, rule.id, 90)
    val streak = currentStreak(streakRecords, rule)
    val best = bestStreak(streakRecords, rule)
    val recorded = chartRecords.filter { it.hasData }
    val successDays = recorded.count { goalMet(it, rule) && !it.commitmentBroken }
    val averageUsage = if (recorded.isEmpty()) 0L else recorded.sumOf { it.usageMs } / recorded.size
    var showUsage by remember(rule.id) { mutableStateOf(false) }

    ElevatedCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    TargetAppIcons(context, rule)
                }
                if (!rule.fullLock) {
                    StreakBadge(streak, entryToken)
                }
            }

            if (rule.fullLock) {
                Text("完全ロック", fontWeight = FontWeight.SemiBold)
                Text(
                    "完全ロックのブロック試行履歴はまだ記録していません。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    when {
                        streak >= 30 -> "${streak}日連続。かなり長く守れています。"
                        streak >= 7 -> "${streak}日連続で守れています。"
                        streak > 0 -> "${streak}日連続で守れています。"
                        recorded.isNotEmpty() -> "次に守れた日から、またストリークが始まります。"
                        else -> "使い始めると、ここに達成の積み重ねが表示されます。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HistoryMetric("現在", "${streak}日")
                    HistoryMetric("最長", "${best}日")
                    HistoryMetric(
                        "30日達成",
                        if (recorded.isEmpty()) "—" else "${successDays}/${recorded.size}"
                    )
                }

                Text("30日間の達成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AchievementCalendar(chartRecords, rule)
                Text(
                    "守れた日が増えるほどマスが埋まります。一時停止・無効化を使った日は達成になりません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showUsage = !showUsage }
                ) {
                    Text(if (showUsage) "利用時間を隠す" else "利用時間の記録を見る")
                }

                if (showUsage) {
                    Text(
                        "実利用時間（参考）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    UsageHistoryChart(chartRecords, rule)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HistoryMetric("記録", "${recorded.size}日")
                        HistoryMetric("上限内", "${successDays}日")
                        HistoryMetric("平均", if (recorded.isEmpty()) "—" else formatDuration(averageUsage))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCalendar(records: List<DailyRecord>, rule: BrowserRule) {
    val success = MaterialTheme.colorScheme.primary
    val failed = MaterialTheme.colorScheme.error
    val missing = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
        ) {
            if (records.isEmpty()) return@Canvas
            val columns = 10
            val rows = 3
            val gap = 5.dp.toPx()
            val cellWidth = ((size.width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
            val cellHeight = ((size.height - gap * (rows - 1)) / rows).coerceAtLeast(1f)
            val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())

            records.takeLast(columns * rows).forEachIndexed { index, record ->
                val row = index / columns
                val col = index % columns
                val x = col * (cellWidth + gap)
                val y = row * (cellHeight + gap)
                val color = when {
                    !record.hasData -> missing
                    record.commitmentBroken -> failed
                    goalMet(record, rule) -> success
                    else -> failed
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellWidth, cellHeight),
                    cornerRadius = radius
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("30日前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("今日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val timeExceeded =
                        rule.dailyUsageLimitMs >= 0L && record.usageMs > rule.dailyUsageLimitMs
                    drawRoundRect(
                        color = if (timeExceeded) error else primary,
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
private fun StreakBadge(streak: Int, entryToken: Int) {
    val progress = remember(streak) { Animatable(1f) }

    LaunchedEffect(entryToken, streak) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value.coerceIn(0f, 1f)
    val shown = (streak * p).roundToInt().coerceIn(if (streak > 0) 1 else 0, streak)
    val pop = sin((p * PI).toFloat()).coerceAtLeast(0f)
    val scale = 0.82f + 0.18f * p + 0.10f * pop
    val alpha = (0.28f + 0.72f * p).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            alpha = alpha
        ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                shown.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "日連続",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
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
        if (!record.hasData || record.commitmentBroken || !goalMet(record, rule)) break
        streak++
    }
    return streak
}

private fun bestStreak(records: List<DailyRecord>, rule: BrowserRule): Int {
    var best = 0
    var running = 0
    records.forEach { record ->
        if (record.hasData && !record.commitmentBroken && goalMet(record, rule)) {
            running++
            if (running > best) best = running
        } else {
            running = 0
        }
    }
    return best
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    onChanged: () -> Unit,
    onOpenGuidedRule: () -> Unit
) {
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
            SectionTitle("Settings Protection")
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("制限を弱める変更を保護", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "対象を減らす、解除条件を短くする、1日の上限を広げるなどの変更には30秒の確認が入ります。強くする変更はすぐ保存できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = onOpenGuidedRule
            ) { Text("ガイド形式で新しい制限を作る") }
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
