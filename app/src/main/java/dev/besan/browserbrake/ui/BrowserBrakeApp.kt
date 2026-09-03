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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Browser Brake", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "衝動では開かない。必要なら、意図して使う。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state == Prefs.STATE_READY) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("解除条件を達成しました", style = MaterialTheme.typography.titleLarge)
                        Text("本当に使うなら、ここで利用時間を決めます。")
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                context.startActivity(Intent(context, UnlockGateActivity::class.java))
                            }
                        ) {
                            Text("利用時間を選ぶ")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                Prefs.declineReady(context)
                                NotificationController.cancel(context)
                                BrowserBlockService.requestRuntimeSync()
                            }
                        ) {
                            Text("今回はやめる")
                        }
                    }
                }
            }
        }

        item {
            RuntimeCard(state = state, activeRule = activeRule, tick = tick)
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
}

@Composable
private fun RuntimeCard(state: String, activeRule: BrowserRule?, tick: Int) {
    val context = LocalContext.current
    val headline = when (state) {
        Prefs.STATE_CHALLENGING -> "解除条件を進めています"
        Prefs.STATE_READY -> "利用するか決められます"
        Prefs.STATE_SESSION -> "利用中"
        Prefs.STATE_RECOVERY -> "利用後の休憩中"
        else -> "待機中"
    }
    val detail = when (state) {
        Prefs.STATE_SESSION ->
            "実使用 残り ${NotificationController.format(Prefs.liveSessionUsageRemainingMs(context))}"
        Prefs.STATE_RECOVERY ->
            "あと ${NotificationController.format((Prefs.recoveryDeadline(context) - System.currentTimeMillis()).coerceAtLeast(0L))}"
        Prefs.STATE_CHALLENGING -> activeRule?.name ?: "Brake中"
        Prefs.STATE_READY -> activeRule?.name ?: "READY"
        else -> "対象アプリを開くと、該当するルールが動きます"
    }
    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("現在", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(headline, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RulesScreen(
    modifier: Modifier,
    rules: List<BrowserRule>,
    onEdit: (String) -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var controlling by remember { mutableStateOf<BrowserRule?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("ルール", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "対象・場所・解除条件・利用量を、ルールごとにまとめます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(rules, key = { it.id }) { rule ->
            RuleCard(
                rule = rule,
                onClick = { onEdit(rule.id) },
                onStatusClick = { controlling = rule }
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    controlling?.let { rule ->
        RuleControlDialog(
            rule = rule,
            onDismiss = { controlling = null },
            onChanged = {
                controlling = null
                onChanged()
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: BrowserRule,
    onClick: () -> Unit,
    onStatusClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val paused = rule.enabled && rule.pausedUntilMs > now
    val status = when {
        !rule.enabled -> "無効"
        paused -> "停止中"
        else -> "有効"
    }
    val placeSummary = if (rule.allPlaces) "すべての場所" else {
        val names = PlaceStore.all(context)
            .filter { it.id in rule.placeIds }
            .map { it.name }
        names.ifEmpty { listOf("場所未選択") }.joinToString("・")
    }
    val brakeSummary = when {
        rule.challengePhoneBreak -> "スマホ休憩 ${formatDuration(rule.phoneBreakMs)}"
        rule.challengeWait -> "待つ ${formatDuration(rule.waitMs)}"
        rule.challengeWalk -> "歩く ${rule.walkSteps}歩"
        else -> "解除条件なし"
    }

    Card(onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        TargetGroupCatalog.targetSummary(context, rule),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onStatusClick != null) {
                    AssistChip(onClick = onStatusClick, label = { Text(status) })
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            HorizontalDivider()
            Text(placeSummary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$brakeSummary → 最大${formatDuration(rule.defaultSessionUsageMs)}利用",
                style = MaterialTheme.typography.bodyMedium
            )
            val daily = buildList {
                if (rule.dailyUsageLimitMs >= 0) add("1日${formatDuration(rule.dailyUsageLimitMs)}")
                if (rule.dailySessionLimit >= 0) add("${rule.dailySessionLimit}回")
            }.joinToString(" / ").ifBlank { "1日の上限なし" }
            Text(daily, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RuleControlDialog(
    rule: BrowserRule,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf(false) }

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
                    Button(
                        modifier = Modifier.fillMaxWidth(),
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
            Text("今日の記録", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "まずは利用時間とSession回数を端末内だけで記録します。",
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
                    Text("Session　${sessions}回")
                    Text(
                        "Escalation　Level ${RuleRepository.storedEscalationLevel(context, rule.id)}",
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
            SectionTitle("System Health")
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
                    Text("ローカルファースト", style = MaterialTheme.typography.titleMedium)
                    Text("アカウントなし / 広告なし / analyticsなし / INTERNET権限なし")
                    Text(
                        "Accessibilityの画面内容取得は無効です。",
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
