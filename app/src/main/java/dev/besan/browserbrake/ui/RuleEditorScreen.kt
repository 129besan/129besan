package dev.besan.browserbrake.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
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
import dev.besan.browserbrake.PlaceStore
import dev.besan.browserbrake.TargetApps
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.rules.TargetGroupCatalog
import java.util.Locale

private enum class EditorSection {
    TARGETS, PLACES, CHALLENGE, SESSION, DAILY, RECOVERY, ESCALATION, APPS
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
    var confirmDelete by remember { mutableStateOf(false) }

    val conflicts = RuleRepository.conflicts(context, draft)
    val hasTargets = draft.browsers || draft.sns || draft.customPackages.isNotEmpty()

    if (section != null) {
        RuleSectionScreen(
            section = section!!,
            draft = draft,
            onDraftChange = { draft = it },
            onBack = { section = null },
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

            if (!hasTargets) {
                item {
                    WarningCard("対象アプリを1つ以上選んでください。")
                }
            }

            if (conflicts.isNotEmpty()) {
                item {
                    WarningCard(
                        "他の有効ルールと対象が重複しています: " + conflicts.joinToString("、") +
                            "\n同じアプリはv0.4では1つのルールにだけ所属できます。"
                    )
                }
            }

            item {
                SettingEntry(
                    title = "対象",
                    summary = TargetGroupCatalog.targetSummary(context, draft),
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
                    onClick = { section = EditorSection.PLACES }
                )
            }

            item {
                SettingEntry(
                    title = "開く前",
                    summary = challengeSummary(draft),
                    onClick = { section = EditorSection.CHALLENGE }
                )
            }

            item {
                SettingEntry(
                    title = "利用",
                    summary = sessionSummary(draft),
                    onClick = { section = EditorSection.SESSION }
                )
            }

            item {
                SettingEntry(
                    title = "1日の上限",
                    summary = dailySummary(draft),
                    onClick = { section = EditorSection.DAILY }
                )
            }

            item {
                SettingEntry(
                    title = "利用後",
                    summary = if (draft.recoveryMs <= 0) "休憩なし" else "${formatDuration(draft.recoveryMs)}休憩",
                    onClick = { section = EditorSection.RECOVERY }
                )
            }

            item {
                SettingEntry(
                    title = "繰り返し利用",
                    summary = when (draft.escalationMode) {
                        "off" -> "Escalation OFF"
                        "strong" -> "Escalation Strong"
                        else -> "Escalation Standard"
                    },
                    onClick = { section = EditorSection.ESCALATION }
                )
            }

            item {
                Text("このルールの動き", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                RulePreview(draft)
            }

            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { confirmDelete = true }
                ) { Text("このルールを削除") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ルールを削除しますか？") },
            text = { Text("このルールの設定は元に戻せません。") },
            confirmButton = {
                Button(onClick = {
                    RuleRepository.deleteRule(context, draft.id)
                    onDeleted()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") }
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
                            title = "Browsers",
                            description = "Chrome、Firefox、Braveなどをまとめて対象",
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
                            description = "X、Instagram、Reddit、Threads、Blueskyなど",
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
                            label = "利用権の有効時間",
                            values = listOf(5*60_000L, 10*60_000L, 15*60_000L, 30*60_000L, 45*60_000L, 60*60_000L, 2*60*60_000L),
                            selected = draft.sessionWindowMs,
                            formatter = ::formatDuration,
                            onSelect = { onDraftChange(draft.copy(sessionWindowMs = it)) }
                        )
                    }
                    item {
                        OptionPicker(
                            label = "解除条件達成後の有効時間",
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
                            label = "1日の実使用時間",
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
                            "上限を超えた場合は現在のalpha設定では解除条件が強化され、利用時間も短くなります。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                EditorSection.RECOVERY -> {
                    item {
                        OptionPicker(
                            label = "Session終了後、次に使えるまで",
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
                            "実際にSessionを開始するたびにBrakeを少し強くし、対象アプリをしばらく試さなければ徐々に戻します。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        listOf(
                            "off" to "OFF",
                            "standard" to "Standard",
                            "strong" to "Strong"
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
                "Browsers / SNSで既に対象になるアプリはここには表示しません。",
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
            Text("最大 ${formatDuration(rule.defaultSessionUsageMs)} 利用")
            if (rule.recoveryMs > 0L) {
                Text("↓")
                Text("${formatDuration(rule.recoveryMs)} 休憩")
            }
        }
    }
}

@Composable
private fun SettingEntry(title: String, summary: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val ask = if (rule.askSessionDuration) "毎回利用時間を選ぶ" else "既定時間で開始"
    return "$ask / 最大${formatDuration(rule.defaultSessionUsageMs)} / 利用権${formatDuration(rule.sessionWindowMs)}"
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
        if (rule.browsers) add("Browsers")
        if (rule.sns) add("SNS")
        if (rule.customPackages.isNotEmpty()) add("${rule.customPackages.size} apps")
    }
    return parts.joinToString(" + ").ifBlank { "対象アプリ" } + " を開く"
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
