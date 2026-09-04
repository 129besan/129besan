package dev.besan.browserbrake.ui

import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

private const val FLUID_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float2 touch;
uniform float touchStrength;

float softBlob(float2 p, float2 c, float r) {
    float d = length(p - c);
    return 1.0 - smoothstep(r * 0.18, r, d);
}

half4 main(float2 fragCoord) {
    float shortSide = min(resolution.x, resolution.y);
    float2 uv = (fragCoord * 2.0 - resolution) / shortSide;
    float t = time * 0.38;

    float2 q = uv;
    q += float2(
        sin(q.y * 2.45 + t * 1.07) + 0.45 * sin(q.y * 4.1 - t * 0.63),
        cos(q.x * 2.15 - t * 0.91) + 0.38 * cos(q.x * 3.7 + t * 0.54)
    ) * 0.085;

    float2 mouse = (touch * 2.0 - resolution) / shortSide;
    float2 delta = mouse - q;
    float inv = 1.0 / (0.13 + dot(delta, delta));
    q += delta * (0.052 * inv * touchStrength);
    q += float2(-delta.y, delta.x) * (0.030 * inv * touchStrength);

    float2 c1 = float2(0.58 * sin(t * 0.83), 0.42 * cos(t * 0.67));
    float2 c2 = float2(0.50 * cos(t * 0.57 + 1.4), 0.56 * sin(t * 0.74 + 0.7));
    float2 c3 = float2(0.36 * sin(t * 0.48 + 2.2), 0.62 * cos(t * 0.51 + 0.2));
    float2 c4 = float2(0.68 * cos(t * 0.39 + 2.7), 0.28 * sin(t * 0.92 + 1.8));

    float field = 0.0;
    field += 1.02 * softBlob(q, c1, 0.78);
    field += 0.94 * softBlob(q, c2, 0.72);
    field += 0.86 * softBlob(q, c3, 0.66);
    field += 0.72 * softBlob(q, c4, 0.64);

    float wave = 0.5 + 0.5 * sin(q.x * 3.15 + sin(q.y * 2.2 - t) * 1.4 + t * 0.76);
    float density = smoothstep(0.18, 1.62, field + wave * 0.30);
    float rim = smoothstep(0.26, 0.72, density) - smoothstep(0.72, 1.0, density);

    float hueShift = 0.5 + 0.5 * sin(t * 0.31 + q.x * 1.15 - q.y * 0.74);
    float3 cyan = float3(0.16, 0.72, 1.00);
    float3 blue = float3(0.13, 0.28, 0.98);
    float3 violet = float3(0.64, 0.25, 1.00);
    float3 ice = float3(0.78, 0.94, 1.00);

    float3 color = mix(blue, violet, hueShift);
    color = mix(color, cyan, smoothstep(0.36, 0.94, field));
    color = mix(color, ice, rim * 0.48);

    float vignette = 1.0 - smoothstep(0.78, 1.48, length(uv));
    float alpha = clamp((0.13 + density * 0.84) * (0.58 + 0.42 * vignette), 0.0, 0.98);
    color *= 0.58 + density * 0.68;

    return half4(color * alpha, alpha);
}
"""

/**
 * GPU-driven intervention visual. On Android 13+ this is a single AGSL fragment shader rather
 * than a set of individually drawn particles. Older supported devices use a soft-blob fallback.
 */
@Composable
fun InteractiveParticleField(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FluidShaderField(modifier)
    } else {
        SoftBlobFallback(modifier)
    }
}

@Suppress("NewApi")
@Composable
private fun FluidShaderField(modifier: Modifier) {
    val shader = remember { RuntimeShader(FLUID_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameNanos = it }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touch = down.position
                    touching = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.firstOrNull { it.pressed } ?: break
                        touch = pressed.position
                    }
                    touching = false
                }
            }
    ) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", frameNanos / 1_000_000_000f)
        shader.setFloatUniform(
            "touch",
            if (touch == Offset.Zero) size.width / 2f else touch.x,
            if (touch == Offset.Zero) size.height / 2f else touch.y
        )
        shader.setFloatUniform("touchStrength", if (touching) 1f else 0f)

        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(size.minDimension * 0.12f)
        )
    }
}

@Composable
private fun SoftBlobFallback(modifier: Modifier) {
    var frameNanos by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameNanos = it }
        }
    }

    val elapsed = frameNanos / 1_000_000_000f

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touch = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.firstOrNull { it.pressed } ?: break
                        touch = pressed.position
                    }
                    touch = null
                }
            }
    ) {
        val centers = listOf(
            Offset(size.width * (0.50f + 0.24f * sin(elapsed * 0.42f)), size.height * (0.43f + 0.22f * cos(elapsed * 0.37f))),
            Offset(size.width * (0.48f + 0.28f * cos(elapsed * 0.31f + 1.4f)), size.height * (0.55f + 0.24f * sin(elapsed * 0.46f))),
            Offset(size.width * (0.50f + 0.20f * sin(elapsed * 0.28f + 2.1f)), size.height * (0.50f + 0.31f * cos(elapsed * 0.34f + 0.7f)))
        ).map { center ->
            touch?.let { finger -> Offset(center.x * 0.72f + finger.x * 0.28f, center.y * 0.72f + finger.y * 0.28f) } ?: center
        }

        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0x55213CFF), Color(0x44135FD1), Color.Transparent)),
            cornerRadius = CornerRadius(size.minDimension * 0.12f)
        )
        val colors = listOf(Color(0xAA55D6FF), Color(0xAA695BFF), Color(0x999D5CFF))
        centers.forEachIndexed { index, center ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors[index], colors[index].copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * (0.48f + index * 0.04f)
                ),
                radius = size.minDimension * 0.54f,
                center = center
            )
        }
    }
}
