package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.rules.RuleRepository
import dev.besan.browserbrake.runtime.RuleRuntimeStore
import dev.besan.browserbrake.ui.BrowserBrakeTheme
import dev.besan.browserbrake.ui.InteractiveParticleField
import dev.besan.browserbrake.ui.formatDuration
import kotlinx.coroutines.delay

class BrakeGateActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FULL_LOCK = "full_lock"
        const val EXTRA_RESTRICTION_NAME = "restriction_name"
        const val EXTRA_RULE_ID = "rule_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fullLock = intent.getBooleanExtra(EXTRA_FULL_LOCK, false)
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        val restrictionName = intent.getStringExtra(EXTRA_RESTRICTION_NAME)
            ?: RuleRepository.getRule(this, ruleId)?.name
            ?: "AppLockout"

        setContent {
            BrowserBrakeTheme {
                BrakeGateScreen(
                    fullLock = fullLock,
                    ruleId = ruleId,
                    restrictionName = restrictionName,
                    onChooseTime = {
                        startActivity(
                            Intent(this, UnlockGateActivity::class.java)
                                .putExtra(UnlockGateActivity.EXTRA_RULE_ID, ruleId)
                        )
                        finish()
                    },
                    onDecline = {
                        if (!fullLock && ruleId.isNotBlank()) {
                            RuleRuntimeStore.clearRuntime(this, ruleId)
                            NotificationController.cancel(this, ruleId)
                            BrowserBlockService.requestRuntimeSync()
                        }
                        goAppHomeAndFinish()
                    },
                    onLeave = ::goAppHomeAndFinish
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        if (ruleId.isNotBlank()) {
            BrowserBlockService.setBrakeGateVisible(ruleId, true)
        }
    }

    override fun onStop() {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        if (ruleId.isNotBlank()) {
            BrowserBlockService.setBrakeGateVisible(ruleId, false)
        }
        super.onStop()
    }

    private fun goAppHomeAndFinish() {
        val appHome = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_HOME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(appHome) }
        finish()
    }
}

@Composable
private fun BrakeGateScreen(
    fullLock: Boolean,
    ruleId: String,
    restrictionName: String,
    onChooseTime: () -> Unit,
    onDecline: () -> Unit,
    onLeave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullLock) {
        if (fullLock) return@LaunchedEffect
        while (true) {
            delay(250)
            tick++
        }
    }

    BackHandler(onBack = onLeave)

    val state = if (fullLock) RuleRuntimeStore.STATE_LOCKED
    else remember(tick, ruleId) { RuleRuntimeStore.state(context, ruleId) }

    LaunchedEffect(state, fullLock) {
        if (fullLock) return@LaunchedEffect
        when (state) {
            RuleRuntimeStore.STATE_READY -> {
                delay(180)
                onChooseTime()
            }
            RuleRuntimeStore.STATE_CHALLENGING -> Unit
            else -> onLeave()
        }
    }

    val background = Brush.verticalGradient(
        listOf(
            Color(0xFF071A46),
            Color(0xFF143F9F),
            Color(0xFF3978EA),
            Color(0xFF8DB9FF)
        )
    )

    Box(
        modifier = Modifier.fillMaxSize().background(background).padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                restrictionName,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium
            )

            InteractiveParticleField(
                modifier = Modifier.size(250.dp)
            )

            if (fullLock) {
                Text(
                    "ロック中",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "この制限が有効な間は、対象アプリを開けません。",
                    color = Color.White.copy(alpha = 0.84f),
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLeave
                ) {
                    Text("ホームへ戻る")
                }
                return@Column
            }

            if (state == RuleRuntimeStore.STATE_READY) {
                Text(
                    "準備できました",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            } else if (state == RuleRuntimeStore.STATE_CHALLENGING) {
                Text(
                    "いったん離れる",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                ChallengeStatusCard(ruleId, tick)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                ) { Text("今回はやめる") }
            }
        }
    }
}

@Composable
private fun ChallengeStatusCard(ruleId: String, tick: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = remember(tick) { System.currentTimeMillis() }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("解除条件", fontWeight = FontWeight.SemiBold)
            if (RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengeWait == true) {
                Text(
                    "待つ　あと " +
                        formatDuration((RuleRuntimeStore.challengeWaitDeadline(context, ruleId) - now).coerceAtLeast(0L))
                )
            }
            if (RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengePhoneBreak == true) {
                val safeSince = RuleRuntimeStore.challengePhoneSafeSince(context, ruleId)
                if (safeSince <= 0L) {
                    Text("スマホ休憩　ほかのアプリを閉じると開始")
                } else {
                    Text(
                        "スマホ休憩　あと " +
                            formatDuration((RuleRuntimeStore.challengePhoneDeadline(context, ruleId) - now).coerceAtLeast(0L))
                    )
                }
                Text(
                    "ほかのアプリが前面にある間は時間が進みません。ホームかAppLockoutで操作を止めてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengeWalk == true) {
                Text("歩く　${RuleRuntimeStore.challengeRequiredSteps(context, ruleId)}歩")
            }
            val count = listOf(
                RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengeWait == true,
                RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengePhoneBreak == true,
                RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengeWalk == true
            ).count { it }
            if (count >= 2) {
                Text(
                    if (RuleRuntimeStore.ruleForRuntime(context, ruleId)?.challengeAll == true) "すべて達成すると利用できます"
                    else "どれか1つを達成すると利用できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
