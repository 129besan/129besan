package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.ui.BrowserBrakeTheme
import dev.besan.browserbrake.ui.formatDuration

class UnlockGateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Prefs.state(this) != Prefs.STATE_READY) {
            Toast.makeText(this, "解除可能な状態ではありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val deadline = Prefs.readyDeadline(this)
        if (deadline > 0L && System.currentTimeMillis() >= deadline) {
            Prefs.declineReady(this)
            NotificationController.cancel(this)
            BrowserBlockService.requestRuntimeSync()
            Toast.makeText(this, "解除資格の有効時間が切れました", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            BrowserBrakeTheme {
                ReadyDecisionScreen(
                    onStart = ::startSession,
                    onDecline = {
                        Prefs.declineReady(this)
                        NotificationController.cancel(this)
                        BrowserBlockService.requestRuntimeSync()
                        finish()
                    }
                )
            }
        }
    }

    private fun startSession(usageMs: Long) {
        val pkg = Prefs.pendingTarget(this)
        if (pkg.isBlank()) {
            Toast.makeText(this, "対象アプリを特定できませんでした", Toast.LENGTH_LONG).show()
            return
        }

        Prefs.startSession(this, usageMs)
        NotificationController.showSession(this)

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
    onStart: (Long) -> Unit,
    onDecline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeId = RuleRepository.activeRuntimeRuleId(context)
    val rule = RuleRepository.getRule(context, activeId)
    val over = Prefs.isOverDailyLimit(context)
    val remaining = Prefs.dailyUsageRemainingMs(context)

    val options = if (over) {
        listOf(60_000L, 3 * 60_000L, RuleConfig.overLimitSessionMs(context))
            .map { it.coerceAtMost(RuleConfig.overLimitSessionMs(context)) }
            .distinct()
    } else if (RuleConfig.askSessionDuration(context)) {
        listOf(5 * 60_000L, 10 * 60_000L, 15 * 60_000L)
            .map { value -> if (remaining >= 0L) value.coerceAtMost(remaining) else value }
            .filter { it > 0L }
            .distinct()
    } else {
        listOf(
            if (remaining >= 0L) RuleConfig.defaultSessionUsageMs(context).coerceAtMost(remaining)
            else RuleConfig.defaultSessionUsageMs(context)
        ).filter { it > 0L }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (over) "今日の上限を超えています" else "解除条件を達成しました",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                rule?.name ?: RuleConfig.ruleName(context),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (over) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("通常利用は終了しています。")
                        Text("必要な場合だけ、短時間の追加利用ができます。")
                    }
                }
            } else {
                val dailyText = if (remaining < 0L) "1日の利用時間: 制限なし"
                else "今日の残り: ${formatDuration(remaining)}"
                Text(dailyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                if (RuleConfig.askSessionDuration(context) || over) "今回は何分使いますか？" else "利用を開始しますか？",
                style = MaterialTheme.typography.titleLarge
            )

            options.forEach { value ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onStart(value) },
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(if (RuleConfig.askSessionDuration(context) || over) "${formatDuration(value)}使う" else "利用を開始")
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
