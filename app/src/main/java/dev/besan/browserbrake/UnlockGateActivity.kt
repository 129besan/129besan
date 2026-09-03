package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import dev.besan.browserbrake.ui.BrowserBrakeTheme
import dev.besan.browserbrake.ui.TargetAppIcons
import dev.besan.browserbrake.ui.formatDuration

class UnlockGateActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RULE_ID = "rule_id"
    }

    private lateinit var ruleId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ruleId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        if (ruleId.isBlank()) {
            Toast.makeText(this, "対象の制限を特定できませんでした", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (RuleRuntimeStore.state(this, ruleId) != RuleRuntimeStore.STATE_READY) {
            Toast.makeText(this, "解除可能な状態ではありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val deadline = RuleRuntimeStore.readyDeadline(this, ruleId)
        if (deadline > 0L && System.currentTimeMillis() >= deadline) {
            RuleRuntimeStore.declineReady(this, ruleId)
            NotificationController.cancel(this, ruleId)
            BrowserBlockService.requestRuntimeSync()
            Toast.makeText(this, "解除資格の有効時間が切れました", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            BrowserBrakeTheme {
                ReadyDecisionScreen(
                    ruleId = ruleId,
                    onStart = ::startSession,
                    onDecline = {
                        RuleRuntimeStore.declineReady(this, ruleId)
                        NotificationController.cancel(this, ruleId)
                        BrowserBlockService.requestRuntimeSync()
                        finish()
                    }
                )
            }
        }
    }

    private fun startSession(usageMs: Long) {
        val pkg = RuleRuntimeStore.pendingTarget(this, ruleId)
        if (pkg.isBlank()) {
            Toast.makeText(this, "対象アプリを特定できませんでした", Toast.LENGTH_LONG).show()
            return
        }

        RuleRuntimeStore.startSession(this, ruleId, usageMs)
        NotificationController.showSession(this, ruleId)
        BrowserBlockService.requestRuntimeSync()

        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            Toast.makeText(this, "対象アプリを開けませんでした", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}

@Composable
private fun ReadyDecisionScreen(
    ruleId: String,
    onStart: (Long) -> Unit,
    onDecline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rule = RuleRuntimeStore.ruleForRuntime(context, ruleId)
        ?: RuleRepository.getRule(context, ruleId)
    if (rule == null) return

    val over = RuleRuntimeStore.challengeOverLimit(context, ruleId) ||
        RuleRuntimeStore.isOverDailyLimit(context, rule)
    val remaining = RuleRuntimeStore.dailyUsageRemainingMs(context, rule)

    val overSession = Prefs.p(context).getLong("over_limit_session_ms", 3L * 60_000L)
    val options = if (over) {
        listOf(60_000L, 3 * 60_000L, overSession)
            .map { it.coerceAtMost(overSession) }
            .filter { it > 0L }
            .distinct()
    } else if (rule.askSessionDuration) {
        listOf(5 * 60_000L, 10 * 60_000L, 15 * 60_000L)
            .map { value -> if (remaining >= 0L) value.coerceAtMost(remaining) else value }
            .filter { it > 0L }
            .distinct()
    } else {
        listOf(
            if (remaining >= 0L) rule.defaultSessionUsageMs.coerceAtMost(remaining)
            else rule.defaultSessionUsageMs
        ).filter { it > 0L }
    }

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    Box(Modifier.fillMaxSize().background(background)) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (over) "短時間だけ追加で使う" else "今回は何分使いますか？",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                TargetAppIcons(context, rule)

                if (over) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("今日の通常利用は終了しています。")
                            Text(
                                "解除条件を達成したため、必要な場合だけ短時間利用できます。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        if (remaining < 0L) "今日の利用時間: 制限なし"
                        else "今日の残り: ${formatDuration(remaining)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                options.forEach { value ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onStart(value) },
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            if (rule.askSessionDuration || over) "${formatDuration(value)}使う"
                            else "利用を開始"
                        )
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                ) {
                    Text("今回はやめる")
                }
            }
        }
    }
}
