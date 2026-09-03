package dev.besan.browserbrake.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.BrowserBlockService
import dev.besan.browserbrake.NotificationController
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.TargetApps
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.rules.TargetGroupCatalog
import java.util.Locale

private enum class EditorSection {
    TARGETS, PLACES, CHALLENGE, SESSION, DAILY, RECOVERY, ESCALATION, APPS, MANAGE
}

private data class AppChoice(val label: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    ruleId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember(ruleId) { RuleRepository.getRule(context, ruleId) }
    if (initial == null) {
        onBack()
        return
    }

    var draft by remember(ruleId) { mutableStateOf(initial) }
    var section by remember { mutableStateOf<EditorSection?>(null) }

    val conflicts = RuleRepository.conflicts(context, draft)
    val hasTargets = draft.browsers || draft.sns || draft.customPackages.isNotEmpty()
    val runtimeActive = RuleRepository.isRuleRuntimeActive(context, draft.id)

    BackHandler {
        when (section) {
            EditorSection.APPS -> section = EditorSection.TARGETS
            null -> onBack()
            else -> section = null
        }
    }

    if (section == EditorSection.MANAGE) {
        RuleManageScreen(
            ruleId = draft.id,
            onBack = {
                draft = RuleRepository.getRule(context, draft.id) ?: draft
                section = null
            },
            onDeleted = onDeleted,
            onChanged = {
                draft = RuleRepository.getRule(context, draft.id) ?: draft
            }
        )
        return
    }

    if (section != null) {
        RuleSectionScreen(
            section = section!!,
            draft = draft,
            onDraftChange = { draft = it },
            onBack = {
                section = if (section == EditorSection.APPS) EditorSection.TARGETS else null
            },
            onOpenApps = { section = EditorSection.APPS }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(draft.name) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("戻る") }
                },
                actions = {
                    TextButton(
                        enabled = conflicts.isEmpty() && hasTargets,
                        onClick = {
                            RuleRepository.saveRule(context, draft)
                            if (runtimeActive) {
                                Toast.makeText(
                                    context,
                                    "保存しました。現在進行中の利用には反映せず、次にこのルールが動くときから適用します。",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            onBack()
                        }
                    ) { Text("保存") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(32)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ルール名") },
                    singleLine = true
                )
            }

            if (runtimeActive) {
                item {
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("このルールは現在動作中です", fontWeight = FontWeight.SemiBold)
                            Text(
                                "ここで変更した内容は、今進んでいる解除条件・利用・休憩には反映されません。次にこのルールが動くときから使われます。",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (!hasTargets) {
                item {
                    WarningCard("対象アプリを1つ以上選んでください。")
                }
            }

            if (conflicts.isNotEmpty()) {
                item {
                    WarningCard(
                        "他の有効ルールと対象が重複しています: " + conflicts.joinToString("、") +
                            "\n同じアプリは1つの有効なルールにだけ設定できます。"
                    )
                }
            }

            item {
                SettingEntry(
                    title = "対象",
                    summary = TargetGroupCatalog.targetSummary(context, draft),
                    symbol = "▦",
                    onClick = { section = EditorSection.TARGETS }
                )
            }

            item {
                val placeSummary = if (draft.allPlaces) {
                    "すべての場所"
                } else {
                    val names = PlaceStore.all(context).filter { it.id in draft.placeIds }.map { it.name }
                    names.ifEmpty { listOf("場所未選択") }.joinToString("・")
                }
                SettingEntry(
                    title = "有効にする場所",
                    summary = placeSummary,
                    symbol = "⌖",
                    onClick = { section = EditorSection.PLACES }
                )
            }

            item {
                SettingEntry(
                    title = "開く前",
                    summary = challengeSummary(draft),
                    symbol = "⏱",
                    onClick = { section = EditorSection.CHALLENGE }
                )
            }

            item {
                SettingEntry(
                    title = "利用",
                    summary = sessionSummary(draft),
                    symbol = "◷",
                    onClick = { section = EditorSection.SESSION }
                )
            }

            item {
                SettingEntry(
                    title = "1日の上限",
                    summary = dailySummary(draft),
                    symbol = "◔",
                    onClick = { section = EditorSection.DAILY }
                )
            }

            item {
                SettingEntry(
                    title = "利用後",
                    summary = if (draft.recoveryMs <= 0) "休憩なし" else "${formatDuration(draft.recoveryMs)}休憩",
                    symbol = "☾",
                    onClick = { section = EditorSection.RECOVERY }
                )
            }

            item {
                SettingEntry(
                    title = "繰り返し利用",
                    symbol = "↻",
                    summary = when (draft.escalationMode) {
                        "off" -> "繰り返しても変えない"
                        "strong" -> "強め"
                        else -> "標準"
                    },
                    onClick = { section = EditorSection.ESCALATION }
                )
            }

            item {
                Text("このルールの動き", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                RulePreview(draft)
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "その他",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingEntry(
                    title = "ルールを管理",
                    summary = "一時停止・無効化・削除",
                    symbol = "⚙",
                    onClick = {
                        if (conflicts.isEmpty() && hasTargets) {
                            RuleRepository.saveRule(context, draft)
                            section = EditorSection.MANAGE
                        }
                    }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleManageScreen(
    ruleId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val rule = remember(revision, ruleId) { RuleRepository.getRule(context, ruleId) }
    var pauseMinutes by remember { mutableStateOf<Int?>(null) }
    var disableDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var typedName by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    if (rule == null) {
        onDeleted()
        return
    }

    val now = System.currentTimeMillis()
    val paused = rule.enabled && rule.pausedUntilMs > now
    val status = when {
        !rule.enabled -> "無効"
        paused -> "一時停止中"
        else -> "有効"
    }

    fun syncAfterWeakening() {
        NotificationController.cancel(context)
        BrowserBlockService.requestRuntimeSync()
        revision++
        onChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ルールを管理") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(rule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("現在: $status")
                        Text(
                            "ここはルールを弱めたり停止したりするための管理画面です。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!rule.enabled) {
                item {
                    val conflicts = RuleRepository.conflicts(context, rule.copy(enabled = true))
                    if (conflicts.isNotEmpty()) {
                        WarningCard("有効化できません。対象が「${conflicts.joinToString("、")}」と重複しています。")
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                RuleRepository.setEnabled(context, rule.id, true)
                                revision++
                                onChanged()
                            }
                        ) { Text("ルールを有効にする") }
                    }
                }
            } else if (paused) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            RuleRepository.pauseRule(context, rule.id, 0L)
                            revision++
                            onChanged()
                        }
                    ) { Text("今すぐ再開する") }
                }
                item {
                    Text(
                        "一時停止は ${formatDuration((rule.pausedUntilMs - now).coerceAtLeast(0L))} 後に自動で終了します。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text("一時停止", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "一時停止中は、このルールによる制限が働きません。必要な場合だけ使ってください。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pauseMinutes = 15 }
                    ) { Text("15分だけ一時停止") }
                }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { pauseMinutes = 60 }
                    ) { Text("1時間だけ一時停止") }
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Text("ルールを無効にする", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "無効にすると、再び有効にするまでこのルールは働きません。衝動的に解除しにくいよう、ルール名の入力を求めます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            typedName = ""
                            disableDialog = true
                        }
                    ) { Text("無効化の手続きへ") }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
            item {
                Text(
                    "さらに下の操作",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        typedName = ""
                        deleteDialog = true
                    }
                ) { Text("このルールを削除") }
            }
        }
    }

    pauseMinutes?.let { minutes ->
        AlertDialog(
            onDismissRequest = { pauseMinutes = null },
            title = { Text("${minutes}分だけ一時停止しますか？") },
            text = {
                Text("この間は「${rule.name}」による制限が働きません。時間が経つと自動で再開します。")
            },
            confirmButton = {
                Button(onClick = {
                    val wasActive = RuleRepository.isRuleRuntimeActive(context, rule.id)
                    RuleRepository.pauseRule(
                        context,
                        rule.id,
                        System.currentTimeMillis() + minutes * 60_000L
                    )
                    pauseMinutes = null
                    if (wasActive) syncAfterWeakening() else {
                        revision++
                        onChanged()
                    }
                }) { Text("一時停止する") }
            },
            dismissButton = {
                TextButton(onClick = { pauseMinutes = null }) { Text("やめる") }
            }
        )
    }

    if (disableDialog) {
        AlertDialog(
            onDismissRequest = {
                disableDialog = false
                typedName = ""
            },
            title = { Text("ルールを無効にする") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("確認のため、ルール名「${rule.name}」を入力してください。")
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ルール名") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = typedName == rule.name,
                    onClick = {
                        val wasActive = RuleRepository.isRuleRuntimeActive(context, rule.id)
                        RuleRepository.setEnabled(context, rule.id, false)
                        disableDialog = false
                        typedName = ""
                        if (wasActive) syncAfterWeakening() else {
                            revision++
                            onChanged()
                        }
                    }
                ) { Text("無効にする") }
            },
            dismissButton = {
                TextButton(onClick = {
                    disableDialog = false
                    typedName = ""
                }) { Text("やめる") }
            }
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = {
                deleteDialog = false
                typedName = ""
            },
            title = { Text("ルールを削除する") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("削除すると設定は元に戻せません。確認のため「${rule.name}」を入力してください。")
                    OutlinedTextField(
                        value = typedName,
                        onValueChange = { typedName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ルール名") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = typedName == rule.name,
                    onClick = {
                        RuleRepository.deleteRule(context, rule.id)
                        NotificationController.cancel(context)
                        BrowserBlockService.requestRuntimeSync()
                        onDeleted()
                    }
                ) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    typedName = ""
                }) { Text("やめる") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSectionScreen(
    section: EditorSection,
    draft: BrowserRule,
    onDraftChange: (BrowserRule) -> Unit,
    onBack: () -> Unit,
    onOpenApps: () -> Unit
) {
    val context = LocalContext.current
    if (section == EditorSection.APPS) {
        AppPickerScreen(draft = draft, onDraftChange = onDraftChange, onBack = onBack)
        return
    }

    val title = when (section) {
        EditorSection.TARGETS -> "対象"
        EditorSection.PLACES -> "有効にする場所"
        EditorSection.CHALLENGE -> "解除条件"
        EditorSection.SESSION -> "利用"
        EditorSection.DAILY -> "1日の上限"
        EditorSection.RECOVERY -> "利用後の休憩"
        EditorSection.ESCALATION -> "繰り返し利用"
        EditorSection.APPS -> "アプリ"
        EditorSection.MANAGE -> "ルールを管理"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (section) {
                EditorSection.TARGETS -> {
                    item {
                        ChoiceToggle(
                            title = "ブラウザ",
                            description = "Chrome、Firefox、Braveなどをまとめて対象にします",
                            checked = draft.browsers,
                            onChecked = {
                                val cleaned = if (it) draft.customPackages - TargetApps.browserPackages(context) else draft.customPackages
                                onDraftChange(draft.copy(browsers = it, customPackages = cleaned))
                            }
                        )
                    }
                    item {
                        ChoiceToggle(
                            title = "SNS",
                            description = "X、Instagram、Reddit、Threads、Blueskyなどをまとめて対象にします",
                            checked = draft.sns,
                            onChecked = {
                                val cleaned = if (it) draft.customPackages.filterNot(TargetGroupCatalog::isSnsPackage).toSet() else draft.customPackages
                                onDraftChange(draft.copy(sns = it, customPackages = cleaned))
                            }
                        )
                    }
                    item {
                        SettingEntry(
                            title = "その他のアプリ",
                            summary = if (draft.customPackages.isEmpty()) "追加なし" else "${draft.customPackages.size}個選択",
                            onClick = onOpenApps
                        )
                    }
                }

                EditorSection.PLACES -> {
                    item {
                        ChoiceToggle(
                            title = "すべての場所",
                            description = "場所に関係なくこのルールを有効にする",
                            checked = draft.allPlaces,
                            onChecked = { onDraftChange(draft.copy(allPlaces = it)) }
                        )
                    }
                    if (!draft.allPlaces) {
                        val places = PlaceStore.all(context)
                        if (places.isEmpty()) {
                            item {
                                WarningCard("登録場所がありません。「設定」タブで場所を追加してください。")
                            }
                        } else {
                            items(places, key = { it.id }) { place ->
                                CheckRow(
                                    title = place.name,
                                    subtitle = "半径 ${place.radiusM.toInt()}m",
                                    checked = place.id in draft.placeIds,
                                    onChecked = { checked ->
                                        val ids = draft.placeIds.toMutableSet()
                                        if (checked) ids += place.id else ids -= place.id
                                        onDraftChange(draft.copy(placeIds = ids))
                                    }
                                )
                            }
                        }
                    }
                }

                EditorSection.CHALLENGE -> {
                    item {
                        Text("解除条件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "複数選んだ場合は、すべて達成するか、どれか1つで解除するかを選べます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        ChoiceToggle("待つ", "他のスマホ操作をしていても時間が進みます", draft.challengeWait) {
                            onDraftChange(draft.copy(challengeWait = it))
                        }
                    }
                    if (draft.challengeWait) {
                        item {
                            OptionPicker(
                                label = "待つ時間",
                                values = listOf(15_000L, 30_000L, 60_000L, 2*60_000L, 3*60_000L, 5*60_000L, 10*60_000L, 15*60_000L),
                                selected = draft.waitMs,
                                formatter = ::formatDuration,
                                onSelect = { onDraftChange(draft.copy(waitMs = it)) }
                            )
                        }
                    }
                    item {
                        ChoiceToggle("スマホ休憩", "クリック・スクロール・入力などでタイマーをやり直します", draft.challengePhoneBreak) {
                            onDraftChange(draft.copy(challengePhoneBreak = it))
                        }
                    }
                    if (draft.challengePhoneBreak) {
                        item {
                            OptionPicker(
                                label = "休憩時間",
                                values = listOf(30_000L, 60_000L, 2*60_000L, 3*60_000L, 5*60_000L, 7*60_000L, 10*60_000L, 15*60_000L, 20*60_000L),
                                selected = draft.phoneBreakMs,
                                formatter = ::formatDuration,
                                onSelect = { onDraftChange(draft.copy(phoneBreakMs = it)) }
                            )
                        }
                    }
                    item {
                        ChoiceToggle("歩く", "端末の歩数センサーで解除条件を数えます", draft.challengeWalk) {
                            onDraftChange(draft.copy(challengeWalk = it))
                        }
                    }
                    if (draft.challengeWalk) {
                        item {
                            OptionPicker(
                                label = "必要歩数",
                                values = listOf(25, 50, 100, 150, 200, 300, 500, 750, 1000),
                                selected = draft.walkSteps,
                                formatter = { "${it}歩" },
                                onSelect = { onDraftChange(draft.copy(walkSteps = it)) }
                            )
                        }
                    }
                    if (listOf(draft.challengeWait, draft.challengePhoneBreak, draft.challengeWalk).count { it } >= 2) {
                        item {
                            Text("複数の解除条件")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = draft.challengeAll,
                                    onClick = { onDraftChange(draft.copy(challengeAll = true)) },
                                    label = { Text("すべて達成") }
                                )
                                FilterChip(
                                    selected = !draft.challengeAll,
                                    onClick = { onDraftChange(draft.copy(challengeAll = false)) },
                                    label = { Text("どれか1つ") }
                                )
                            }
                        }
                    }
                }

                EditorSection.SESSION -> {
                    item {
                        ChoiceToggle(
                            "利用前に時間を決める",
                            "解除後に「今回は何分使う？」を表示します",
                            draft.askSessionDuration
                        ) { onDraftChange(draft.copy(askSessionDuration = it)) }
                    }
                    item {
                        OptionPicker(
                            label = "1回の最大利用時間",
                            values = listOf(60_000L, 3*60_000L, 5*60_000L, 10*60_000L, 15*60_000L, 20*60_000L, 30*60_000L, 45*60_000L, 60*60_000L),
                            selected = draft.defaultSessionUsageMs,
                            formatter = ::formatDuration,
                            onSelect = { onDraftChange(draft.copy(defaultSessionUsageMs = it)) }
                        )
                    }
                    item {
                        OptionPicker(
                            label = "今回の利用を開始できる時間の上限",
                            values = listOf(5*60_000L, 10*60_000L, 15*60_000L, 30*60_000L, 45*60_000L, 60*60_000L, 2*60*60_000L),
                            selected = draft.sessionWindowMs,
                            formatter = ::formatDuration,
                            onSelect = { onDraftChange(draft.copy(sessionWindowMs = it)) }
                        )
                    }
                    item {
                        OptionPicker(
                            label = "解除条件を達成したあと、利用を始められる期限",
                            values = listOf(0L, 5*60_000L, 15*60_000L, 30*60_000L, 60*60_000L),
                            selected = draft.readyTimeoutMs,
                            formatter = { if (it == 0L) "制限なし" else formatDuration(it) },
                            onSelect = { onDraftChange(draft.copy(readyTimeoutMs = it)) }
                        )
                    }
                }

                EditorSection.DAILY -> {
                    item {
                        OptionPicker(
                            label = "1日に実際に使える時間",
                            values = listOf(-1L, 15*60_000L, 30*60_000L, 45*60_000L, 60*60_000L, 90*60_000L, 2*60*60_000L, 3*60*60_000L),
                            selected = draft.dailyUsageLimitMs,
                            formatter = { if (it < 0L) "制限なし" else formatDuration(it) },
                            onSelect = { onDraftChange(draft.copy(dailyUsageLimitMs = it)) }
                        )
                    }
                    item {
                        OptionPicker(
                            label = "1日の利用回数",
                            values = listOf(-1, 1, 2, 3, 4, 5, 6, 8, 10, 15),
                            selected = draft.dailySessionLimit,
                            formatter = { if (it < 0) "制限なし" else "${it}回" },
                            onSelect = { onDraftChange(draft.copy(dailySessionLimit = it)) }
                        )
                    }
                    item {
                        Text(
                            "1日の上限を超えると、通常より強い解除条件になり、利用できる時間も短くなります。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                EditorSection.RECOVERY -> {
                    item {
                        OptionPicker(
                            label = "利用を終えてから、次に使えるまで",
                            values = listOf(0L, 30_000L, 60_000L, 3*60_000L, 5*60_000L, 10*60_000L, 15*60_000L, 30*60_000L),
                            selected = draft.recoveryMs,
                            formatter = { if (it == 0L) "なし" else formatDuration(it) },
                            onSelect = { onDraftChange(draft.copy(recoveryMs = it)) }
                        )
                    }
                }

                EditorSection.ESCALATION -> {
                    item {
                        Text(
                            "短い間に何度も利用すると、次の解除条件を少し厳しくします。しばらく対象アプリを開かなければ、徐々に元へ戻ります。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        listOf(
                            "off" to "OFF",
                            "standard" to "標準",
                            "strong" to "強い"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = draft.escalationMode == value,
                                onClick = { onDraftChange(draft.copy(escalationMode = value)) },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                EditorSection.APPS -> Unit
                EditorSection.MANAGE -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerScreen(
    draft: BrowserRule,
    onDraftChange: (BrowserRule) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember(draft.browsers, draft.sns) {
        launcherApps(context).filterNot { app ->
            (draft.browsers && app.packageName in TargetApps.browserPackages(context)) ||
                (draft.sns && TargetGroupCatalog.isSnsPackage(app.packageName))
        }
    }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("アプリを追加") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("アプリを検索") },
                singleLine = true
            )
            Text(
                "「ブラウザ」や「SNS」で既に対象になっているアプリは、ここには表示しません。",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    CheckRow(
                        title = app.label,
                        subtitle = app.packageName,
                        checked = app.packageName in draft.customPackages,
                        onChecked = { checked ->
                            val packages = draft.customPackages.toMutableSet()
                            if (checked) packages += app.packageName else packages -= app.packageName
                            onDraftChange(draft.copy(customPackages = packages))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RulePreview(rule: BrowserRule) {
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(TargetGroupPreview(rule))
            Text("↓")
            Text(challengeSummary(rule))
            Text("↓")
            Text(if (rule.askSessionDuration) "今回は何分使うか選ぶ" else "利用を開始")
            Text("↓")
            Text("最大 ${formatDuration(rule.defaultSessionUsageMs)}使う")
            if (rule.recoveryMs > 0L) {
                Text("↓")
                Text("${formatDuration(rule.recoveryMs)} 休憩")
            }
        }
    }
}

@Composable
private fun SettingEntry(
    title: String,
    summary: String,
    symbol: String = "›",
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(symbol, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun <T> OptionPicker(
    label: String,
    values: List<T>,
    selected: T,
    formatter: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { value ->
                        FilterChip(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                            label = { Text(formatter(value)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun challengeSummary(rule: BrowserRule): String {
    val parts = buildList {
        if (rule.challengeWait) add("待つ ${formatDuration(rule.waitMs)}")
        if (rule.challengePhoneBreak) add("スマホ休憩 ${formatDuration(rule.phoneBreakMs)}")
        if (rule.challengeWalk) add("歩く ${rule.walkSteps}歩")
    }
    if (parts.isEmpty()) return "解除条件なし"
    val suffix = if (parts.size >= 2) {
        if (rule.challengeAll) "（すべて）" else "（どれか1つ）"
    } else ""
    return parts.joinToString(" + ") + suffix
}

private fun sessionSummary(rule: BrowserRule): String {
    val ask = if (rule.askSessionDuration) "毎回、使う時間を選ぶ" else "決めた時間ですぐ開始"
    return "$ask / 実使用 最大${formatDuration(rule.defaultSessionUsageMs)} / 開始から${formatDuration(rule.sessionWindowMs)}まで"
}

private fun dailySummary(rule: BrowserRule): String {
    val items = buildList {
        if (rule.dailyUsageLimitMs >= 0L) add(formatDuration(rule.dailyUsageLimitMs))
        if (rule.dailySessionLimit >= 0) add("${rule.dailySessionLimit}回")
    }
    return items.joinToString(" / ").ifBlank { "制限なし" }
}

private fun TargetGroupPreview(rule: BrowserRule): String {
    val parts = buildList {
        if (rule.browsers) add("ブラウザ")
        if (rule.sns) add("SNS")
        if (rule.customPackages.isNotEmpty()) add("個別 ${rule.customPackages.size}個")
    }
    return parts.joinToString("・").ifBlank { "対象アプリ" } + "を開く"
}

private fun launcherApps(context: Context): List<AppChoice> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)
    val seen = mutableSetOf<String>()
    return infos.mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
        AppChoice(
            label = info.loadLabel(context.packageManager).toString(),
            packageName = pkg
        )
    }.sortedBy { it.label.lowercase(Locale.JAPAN) }
}
