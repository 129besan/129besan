package dev.besan.browserbrake.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.BrowserBlockService
import dev.besan.browserbrake.Prefs
import dev.besan.browserbrake.rules.BrowserRule
import dev.besan.browserbrake.rules.RuleRepository
import kotlinx.coroutines.delay

private const val ONBOARDING_COMPLETE_KEY = "onboarding_v1_complete"
private const val RULES_STORAGE_KEY = "v04_rules_json"

private data class OnboardingApp(
    val label: String,
    val packageName: String
)

internal fun shouldShowOnboarding(context: Context): Boolean {
    val prefs = Prefs.p(context)
    if (prefs.getBoolean(ONBOARDING_COMPLETE_KEY, false)) return false

    // Existing alpha/legacy installs already have a deliberate rule. Do not
    // suddenly put those users back through first-run onboarding on upgrade.
    val existingSetup = prefs.contains(RULES_STORAGE_KEY) ||
        prefs.contains("rule_name") ||
        prefs.contains("runtime_state")
    if (existingSetup) {
        prefs.edit().putBoolean(ONBOARDING_COMPLETE_KEY, true).apply()
        return false
    }
    return true
}

private fun markOnboardingComplete(context: Context) {
    Prefs.p(context).edit().putBoolean(ONBOARDING_COMPLETE_KEY, true).apply()
}

private fun onboardingApps(context: Context): List<OnboardingApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val seen = mutableSetOf<String>()
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
            OnboardingApp(
                label = info.loadLabel(context.packageManager).toString(),
                packageName = pkg
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
internal fun OnboardingScreen(
    freshInstall: Boolean,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var draft by remember {
        mutableStateOf(
            BrowserRule(
                name = "最初の制限",
                browsers = false,
                sns = false,
                customPackages = emptySet(),
                challengeWait = false,
                challengePhoneBreak = true,
                challengeWalk = false,
                phoneBreakMs = 60_000L,
                dailyUsageLimitMs = 60L * 60_000L,
                dailySessionLimit = 5,
                recoveryMs = 5L * 60_000L
            )
        )
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    val apps = remember { onboardingApps(context) }
    val filteredApps = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(step) {
        if (step != 3) return@LaunchedEffect
        while (true) {
            delay(700)
            tick++
        }
    }

    BackHandler(enabled = step > 0) { step-- }

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(Modifier.fillMaxSize().background(background)) {
        when (step) {
            0 -> {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "AppLockout",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "反射的にアプリを開く瞬間に、短い間をつくります。完全に禁止するのではなく、本当に今使うかを一度選び直せるようにします。",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(14.dp))
                    Card {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("最初にやること", fontWeight = FontWeight.SemiBold)
                            Text("1. 制限したいアプリを選ぶ")
                            Text("2. 介入方法を選ぶ")
                            Text("3. AndroidのAccessibilityを有効にする")
                            Text("4. 実際に1回試す")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { step = 1 }) {
                        Text("はじめる")
                    }
                }
            }

            1 -> {
                Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text("どのアプリに間をつくる？", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "まずは1〜3個がおすすめです。あとから自由に増やせます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        if (draft.customPackages.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                draft.customPackages.take(3).forEach { pkg ->
                                    AppIcon(context = context, packageName = pkg, sizeDp = 42)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("アプリを検索") },
                            singleLine = true
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val selected = app.packageName in draft.customPackages
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val next = draft.customPackages.toMutableSet()
                                    if (selected) {
                                        next -= app.packageName
                                    } else if (next.size < 3) {
                                        next += app.packageName
                                    } else {
                                        Toast.makeText(context, "最初は3個まで選べます", Toast.LENGTH_SHORT).show()
                                    }
                                    draft = draft.copy(customPackages = next)
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AppIcon(context = context, packageName = app.packageName, sizeDp = 42)
                                    Text(app.label, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(if (selected) "選択中" else "追加", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { step = 0 }) { Text("戻る") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = draft.customPackages.isNotEmpty(),
                            onClick = { step = 2 }
                        ) { Text("次へ") }
                    }
                }
            }

            2 -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("最初の介入方法", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("あとで制限ごとに細かく調整できます。まずは挙動を体験しやすい設定にします。", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OnboardingMethodCard(
                        title = "スマホ休憩 1分",
                        description = "おすすめ。ほかのアプリを触っている間は進まず、スマホから離れた時間だけ数えます。",
                        selected = draft.challengePhoneBreak
                    ) {
                        draft = draft.copy(
                            challengeWait = false,
                            challengePhoneBreak = true,
                            challengeWalk = false,
                            phoneBreakMs = 60_000L
                        )
                    }
                    OnboardingMethodCard(
                        title = "待つ 15秒",
                        description = "まず軽く試したいとき向け。ほかの操作をしていても時間は進みます。",
                        selected = draft.challengeWait
                    ) {
                        draft = draft.copy(
                            challengeWait = true,
                            challengePhoneBreak = false,
                            challengeWalk = false,
                            waitMs = 15_000L
                        )
                    }
                    OnboardingMethodCard(
                        title = "歩く 50歩",
                        description = "身体を動かして切り替えます。Androidの歩数権限が必要です。",
                        selected = draft.challengeWalk
                    ) {
                        draft = draft.copy(
                            challengeWait = false,
                            challengePhoneBreak = false,
                            challengeWalk = true,
                            walkSteps = 50
                        )
                        if (Build.VERSION.SDK_INT >= 29 &&
                            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                            activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { step = 1 }) { Text("戻る") }
                        Button(modifier = Modifier.weight(1f), onClick = { step = 3 }) { Text("次へ") }
                    }
                }
            }

            3 -> {
                val accessibilityOk = remember(tick) { onboardingAccessibilityEnabled(context) }
                val activityOk = remember(tick, draft.challengeWalk) {
                    !draft.challengeWalk || Build.VERSION.SDK_INT < 29 ||
                        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                }

                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("AppLockoutを動かす準備", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Accessibilityは、どのアプリが前面に来たかを検知して介入するために使います。画面の文章を収集したり、外部へ送信したりはしません。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    OnboardingHealthRow("Accessibility", accessibilityOk) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    if (draft.challengeWalk) {
                        Spacer(Modifier.height(10.dp))
                        OnboardingHealthRow("歩数", activityOk) {
                            if (Build.VERSION.SDK_INT >= 29) activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (!accessibilityOk) {
                        Text("Android設定で AppLockout を有効にしてから戻ってきてください。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { step = 2 }) { Text("戻る") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = accessibilityOk && activityOk,
                            onClick = { step = 4 }
                        ) { Text("準備できた") }
                    }
                }
            }

            else -> {
                val firstPackage = draft.customPackages.firstOrNull()
                val firstLabel = apps.firstOrNull { it.packageName == firstPackage }?.label ?: "選んだアプリ"
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("最後に1回、試してみます", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    TargetAppIcons(context = context, rule = draft, maxIcons = 3)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "「$firstLabel を開いて試す」を押すと、今作った制限を保存して対象アプリを開きます。介入画面が出れば準備完了です。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Settings Protectionも有効です", fontWeight = FontWeight.SemiBold)
                            Text("あとで制限を弱める変更をするときは30秒の確認が入ります。強くする変更はすぐ保存できます。")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val finalRule = draft.copy(name = "$firstLabel の制限")
                            if (freshInstall) RuleRepository.saveInitialRule(context, finalRule)
                            else RuleRepository.saveRule(context, finalRule)
                            markOnboardingComplete(context)
                            BrowserBlockService.requestRuntimeSync()
                            onComplete()

                            val launchIntent = firstPackage?.let(context.packageManager::getLaunchIntentForPackage)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } else {
                                Toast.makeText(context, "制限を保存しました。ホームから対象アプリを開いて試してください", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("$firstLabel を開いて試す") }
                    TextButton(onClick = {
                        val finalRule = draft.copy(name = "$firstLabel の制限")
                        if (freshInstall) RuleRepository.saveInitialRule(context, finalRule)
                        else RuleRepository.saveRule(context, finalRule)
                        markOnboardingComplete(context)
                        BrowserBlockService.requestRuntimeSync()
                        onComplete()
                    }) { Text("試さずホームへ") }
                }
            }
        }
    }
}

@Composable
private fun OnboardingMethodCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected) Text("選択中", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OnboardingHealthRow(label: String, healthy: Boolean, onClick: () -> Unit) {
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

private fun onboardingAccessibilityEnabled(context: Context): Boolean {
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
