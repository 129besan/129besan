package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.ui.BrowserBrakeTheme
import dev.besan.browserbrake.ui.formatDuration
import kotlinx.coroutines.delay

class BrakeGateActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FULL_LOCK = "full_lock"
        const val EXTRA_RESTRICTION_NAME = "restriction_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fullLock = intent.getBooleanExtra(EXTRA_FULL_LOCK, false)
        val restrictionName = intent.getStringExtra(EXTRA_RESTRICTION_NAME)
            ?: RuleConfig.ruleName(this)

        setContent {
            BrowserBrakeTheme {
                BrakeGateScreen(
                    fullLock = fullLock,
                    restrictionName = restrictionName,
                    onChooseTime = {
                        startActivity(Intent(this, UnlockGateActivity::class.java))
                        finish()
                    },
                    onDecline = {
                        if (!fullLock) {
                            Prefs.clearTransientState(this)
                            NotificationController.cancel(this)
                            BrowserBlockService.requestRuntimeSync()
                        }
                        finish()
                    },
                    onLeave = { finish() }
                )
            }
        }
    }
}

@Composable
private fun BrakeGateScreen(
    fullLock: Boolean,
    restrictionName: String,
    onChooseTime: () -> Unit,
    onDecline: () -> Unit,
    onLeave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var inhale by remember { mutableStateOf(true) }

    LaunchedEffect(fullLock) {
        if (fullLock) return@LaunchedEffect
        while (true) {
            delay(250)
            tick++
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            inhale = !inhale
        }
    }

    BackHandler(onBack = onLeave)

    val state = if (fullLock) {
        Prefs.STATE_LOCKED
    } else {
        remember(tick) { Prefs.state(context) }
    }
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val background = Brush.verticalGradient(
        listOf(
            Color(0xFF0D2F7D),
            Color(0xFF2457D6),
            Color(0xFF6EA5FF)
        )
    )

    Box(
        modifier = Modifier.fillMaxSize().background(background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                restrictionName,
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.titleMedium
            )

            if (fullLock) {
                Surface(
                    modifier = Modifier.size(118.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🔒", style = MaterialTheme.typography.displayMedium)
                    }
                }
                Text(
                    "今は開けません",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "この制限は完全ロックです。設定した場所・条件が有効な間は、対象アプリを利用できません。",
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLeave
                ) {
                    Text("離れる")
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (inhale) "吸って" else "吐いて",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                if (state == Prefs.STATE_READY) "解除条件を達成しました" else "ひと呼吸",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            if (state == Prefs.STATE_READY) {
                Text(
                    "必要なら、今回使う時間を決めてから進みます。",
                    color = Color.White.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onChooseTime
                ) { Text("利用時間を選ぶ") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                ) { Text("今回はやめる") }
            } else if (state == Prefs.STATE_CHALLENGING) {
                ChallengeStatusCard(tick)
                if (RuleConfig.challengeWait(context) && !RuleConfig.challengePhoneBreak(context)) {
                    Text(
                        "この画面から戻っても、待ち時間はそのまま進みます。",
                        color = Color.White.copy(alpha = 0.76f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                ) { Text("今回はやめる") }
            } else {
                Text(
                    "解除条件の状態が変わりました。",
                    color = Color.White.copy(alpha = 0.88f)
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLeave
                ) { Text("戻る") }
            }
        }
    }
}

@Composable
private fun ChallengeStatusCard(tick: Int) {
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
            if (RuleConfig.challengeWait(context)) {
                Text(
                    "待つ　あと " +
                        formatDuration((Prefs.challengeWaitDeadline(context) - now).coerceAtLeast(0L))
                )
            }
            if (RuleConfig.challengePhoneBreak(context)) {
                Text(
                    "スマホ休憩　あと " +
                        formatDuration((Prefs.challengePhoneDeadline(context) - now).coerceAtLeast(0L))
                )
                Text(
                    "スマホを操作すると最初からやり直します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (RuleConfig.challengeWalk(context)) {
                Text("歩く　${Prefs.challengeRequiredSteps(context)}歩")
            }
            val count = listOf(
                RuleConfig.challengeWait(context),
                RuleConfig.challengePhoneBreak(context),
                RuleConfig.challengeWalk(context)
            ).count { it }
            if (count >= 2) {
                Text(
                    if (RuleConfig.challengeAll(context)) "すべて達成すると利用できます"
                    else "どれか1つを達成すると利用できます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
