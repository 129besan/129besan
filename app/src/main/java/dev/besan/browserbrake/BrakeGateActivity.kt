package dev.besan.browserbrake

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.besan.browserbrake.ui.BrowserBrakeTheme
import dev.besan.browserbrake.ui.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
                        goHomeAndFinish()
                    },
                    onLeave = ::goHomeAndFinish
                )
            }
        }
    }

    private fun goHomeAndFinish() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
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

    LaunchedEffect(fullLock) {
        if (fullLock) return@LaunchedEffect
        while (true) {
            delay(250)
            tick++
        }
    }

    BackHandler(onBack = onLeave)

    val state = if (fullLock) Prefs.STATE_LOCKED else remember(tick) { Prefs.state(context) }
    val transition = rememberInfiniteTransition(label = "gate")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val inhale = breath < 0.5f

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

            HarmonicBreathingPattern(
                breath = breath,
                phase = phase,
                label = if (inhale) "吸って" else "吐いて"
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

            Text(
                if (state == Prefs.STATE_READY) "解除条件を達成しました" else "解除条件を進めています",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (state == Prefs.STATE_READY) {
                Text(
                    "今回使う時間を決めてから開きます。",
                    color = Color.White.copy(alpha = 0.84f),
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
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDecline
                ) { Text("今回はやめる") }
            } else {
                Text(
                    "状態が更新されました。",
                    color = Color.White.copy(alpha = 0.82f)
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLeave
                ) { Text("ホームへ戻る") }
            }
        }
    }
}

@Composable
private fun HarmonicBreathingPattern(
    breath: Float,
    phase: Float,
    label: String
) {
    Box(
        modifier = Modifier.size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension * (0.28f + 0.055f * breath)
            val ringColors = listOf(
                Color.White.copy(alpha = 0.52f),
                Color(0xFFD7E6FF).copy(alpha = 0.34f),
                Color(0xFF9FC4FF).copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.16f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f + 0.10f * breath),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.48f
                ),
                radius = size.minDimension * 0.48f,
                center = center
            )

            ringColors.forEachIndexed { layer, color ->
                val path = Path()
                val layerPhase = phase * (if (layer % 2 == 0) 1f else -0.7f) + layer * 0.75f
                val layerBase = base * (1f + layer * 0.095f)

                for (i in 0..360) {
                    val theta = (i / 360f) * (2f * PI.toFloat())
                    val ripple =
                        1f +
                            0.13f * cos(6f * theta + layerPhase) +
                            0.045f * cos(3f * theta - layerPhase * 0.55f)
                    val r = layerBase * ripple
                    val x = center.x + r * cos(theta)
                    val y = center.y + r * sin(theta)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = (1.4f + layer * 0.45f).dp.toPx())
                )
            }

            repeat(8) { index ->
                val theta = phase * 0.42f + index * (PI.toFloat() / 4f)
                val radius = base * (1.34f + 0.06f * sin(phase + index))
                val dot = Offset(
                    center.x + radius * cos(theta),
                    center.y + radius * sin(theta)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f + 0.12f * breath),
                    radius = (2.2f + breath * 1.5f).dp.toPx(),
                    center = dot
                )
            }
        }

        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
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
                    "ほかのアプリを操作すると最初からやり直します。",
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
