package dev.besan.browserbrake.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val PARTICLE_COUNT = 78
private const val TWO_PI = (2.0 * PI).toFloat()

private class ParticleFieldState(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val phase = FloatArray(count)
    val radius = FloatArray(count)

    init {
        val random = Random(0xA11CE)
        repeat(count) { index ->
            x[index] = random.nextFloat()
            y[index] = random.nextFloat()
            vx[index] = (random.nextFloat() - 0.5f) * 0.06f
            vy[index] = (random.nextFloat() - 0.5f) * 0.06f
            phase[index] = random.nextFloat() * TWO_PI
            radius[index] = 1.4f + random.nextFloat() * 2.2f
        }
    }

    fun step(dtSeconds: Float, elapsedSeconds: Float, attractor: Offset?) {
        val dt = dtSeconds.coerceIn(0f, 0.034f)
        if (dt <= 0f) return

        for (i in x.indices) {
            val px = x[i]
            val py = y[i]
            val p = phase[i]

            val flowX =
                sin(py * TWO_PI * 1.7f + elapsedSeconds * 0.55f + p) * 0.12f +
                    cos(px * TWO_PI * 1.25f - elapsedSeconds * 0.31f - p * 0.6f) * 0.06f
            val flowY =
                cos(px * TWO_PI * 1.55f - elapsedSeconds * 0.47f + p * 0.8f) * 0.12f -
                    sin(py * TWO_PI * 1.1f + elapsedSeconds * 0.27f + p) * 0.06f

            var nextVx = vx[i] * 0.965f + flowX * dt
            var nextVy = vy[i] * 0.965f + flowY * dt

            attractor?.let { target ->
                val dx = target.x - px
                val dy = target.y - py
                val distanceSquared = dx * dx + dy * dy
                val distance = sqrt(distanceSquared).coerceAtLeast(0.025f)

                val pull = (0.20f / (0.10f + distanceSquared)).coerceAtMost(1.65f)
                nextVx += (dx / distance) * pull * dt
                nextVy += (dy / distance) * pull * dt

                val swirl = (0.07f / (0.16f + distanceSquared)).coerceAtMost(0.42f)
                nextVx += (-dy / distance) * swirl * dt
                nextVy += (dx / distance) * swirl * dt
            }

            val speedSquared = nextVx * nextVx + nextVy * nextVy
            val maxSpeed = if (attractor == null) 0.17f else 0.34f
            if (speedSquared > maxSpeed * maxSpeed) {
                val scale = maxSpeed / sqrt(speedSquared)
                nextVx *= scale
                nextVy *= scale
            }

            var nextX = px + nextVx * dt
            var nextY = py + nextVy * dt

            if (nextX < -0.06f) nextX = 1.06f
            if (nextX > 1.06f) nextX = -0.06f
            if (nextY < -0.06f) nextY = 1.06f
            if (nextY > 1.06f) nextY = -0.06f

            x[i] = nextX
            y[i] = nextY
            vx[i] = nextVx
            vy[i] = nextVy
        }
    }
}

/**
 * Non-verbal gate visual. The field continuously wanders and bends toward the user's finger while
 * it is pressed, providing a small interactive distraction while the configured Challenge runs.
 */
@Composable
fun InteractiveParticleField(modifier: Modifier = Modifier) {
    val particles = remember { ParticleFieldState(PARTICLE_COUNT) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var attractor by remember { mutableStateOf<Offset?>(null) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(particles) {
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (previousFrame == 0L) 0f else (now - previousFrame) / 1_000_000_000f
                previousFrame = now
                particles.step(dt, now / 1_000_000_000f, attractor)
                frameNanos = now
            }
        }
    }

    val elapsed = frameNanos / 1_000_000_000f
    val touch = attractor

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        attractor = Offset(
                            (down.position.x / canvasSize.width).coerceIn(0f, 1f),
                            (down.position.y / canvasSize.height).coerceIn(0f, 1f)
                        )
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.firstOrNull { it.pressed } ?: break
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            attractor = Offset(
                                (pressed.position.x / canvasSize.width).coerceIn(0f, 1f),
                                (pressed.position.y / canvasSize.height).coerceIn(0f, 1f)
                            )
                        }
                    }

                    attractor = null
                }
            }
    ) {
        val colorDrift = 18f * sin(elapsed * 0.18f)

        touch?.let { normalized ->
            val center = Offset(normalized.x * size.width, normalized.y * size.height)
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = size.minDimension * 0.30f,
                center = center
            )
            drawCircle(
                color = Color(0xFF9FC8FF).copy(alpha = 0.08f),
                radius = size.minDimension * 0.16f,
                center = center
            )
        }

        for (i in 0 until PARTICLE_COUNT) {
            val px = particles.x[i] * size.width
            val py = particles.y[i] * size.height
            val pulse = 0.5f + 0.5f * sin(elapsed * 1.15f + particles.phase[i])
            val hue =
                (205f + colorDrift + 58f * sin(particles.phase[i] + elapsed * 0.11f) + 360f) % 360f
            val core = Color.hsv(hue, 0.42f, 1f, 0.62f + pulse * 0.30f)
            val center = Offset(px, py)
            val radiusPx = particles.radius[i] * density * (0.78f + pulse * 0.42f)

            drawCircle(
                color = core.copy(alpha = 0.09f + pulse * 0.08f),
                radius = radiusPx * 3.8f,
                center = center
            )
            drawCircle(
                color = core,
                radius = radiusPx,
                center = center
            )

            touch?.let { normalized ->
                val dx = normalized.x - particles.x[i]
                val dy = normalized.y - particles.y[i]
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < 0.24f) {
                    val target = Offset(normalized.x * size.width, normalized.y * size.height)
                    drawLine(
                        color = core.copy(alpha = ((0.24f - distance) / 0.24f) * 0.16f),
                        start = center,
                        end = target,
                        strokeWidth = 0.7f * density
                    )
                }
            }
        }
    }
}
