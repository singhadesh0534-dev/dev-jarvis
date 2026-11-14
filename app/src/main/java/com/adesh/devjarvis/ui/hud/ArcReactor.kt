package com.adesh.devjarvis.ui.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.Orbitron
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Arc reactor voice visualizer. Rings ghoomte hain, core "energy" ke saath phoolta hai:
 * listening = mic level (+ ripples), speaking = TTS level (voice ring), thinking = fast spin + comet arc,
 * idle = slow saans jaisa pulse. Bahar ke HUD arcs: left = battery, right = API fuel.
 * [accent] follows the role (cyan for JARVIS / DOST, pink for GF).
 */
@Composable
fun ArcReactor(
    state: JarvisState,
    micLevel: Float,
    speechLevel: Float,
    batteryPercent: Int,
    charging: Boolean,
    fuelPercent: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
) {
    val targetEnergy = when (state) {
        JarvisState.LISTENING -> 0.35f + 0.65f * micLevel
        JarvisState.SPEAKING -> 0.3f + 0.7f * speechLevel
        JarvisState.THINKING -> 0.55f
        JarvisState.IDLE -> 0.3f
    }
    val energy by animateFloatAsState(targetEnergy, spring(stiffness = Spring.StiffnessMediumLow), label = "energy")
    val targetVoice = when (state) {
        JarvisState.LISTENING -> micLevel
        JarvisState.SPEAKING -> speechLevel
        else -> 0f
    }
    val voice by animateFloatAsState(targetVoice, spring(stiffness = Spring.StiffnessMedium), label = "voice")
    val targetSpeed = when (state) {
        JarvisState.THINKING -> 4.5f
        JarvisState.LISTENING -> 1.8f
        JarvisState.SPEAKING -> 1.4f
        JarvisState.IDLE -> 0.6f
    }
    val speed by animateFloatAsState(targetSpeed, label = "speed")
    val currentSpeed by rememberUpdatedState(speed)
    val thinking by animateFloatAsState(if (state == JarvisState.THINKING) 1f else 0f, tween(450), label = "thinking")
    val listening by animateFloatAsState(if (state == JarvisState.LISTENING) 1f else 0f, tween(350), label = "listening")
    val awake by animateFloatAsState(if (state == JarvisState.IDLE) 0f else 1f, tween(600), label = "awake")
    val toneTarget = if (state == JarvisState.THINKING) lerp(accent, Hud.Ice, 0.45f) else accent
    val tone by animateColorAsState(toneTarget, tween(600), label = "tone")

    // Manual frame loop so spin speed can change smoothly without the rings jumping.
    var angle by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    angle = (angle + dt * 30f * currentSpeed) % 360f
                    time = (time + dt) % 3600f
                }
                last = now
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Orbitron, fontSize = 9.sp, letterSpacing = 1.sp)
    val batteryColor = if (batteryPercent in 0..19 && !charging) Hud.Amber else tone
    val fuelColor = if (fuelPercent < 15) Hud.Amber else tone
    val triangle = remember { Path() }

    Canvas(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        )
    ) {
        val r = min(size.width, size.height) / 2f
        val c = center
        val hair = 1.dp.toPx()
        val pulse = 0.5f + 0.5f * sin(time * 2f * PI.toFloat() / if (state == JarvisState.THINKING) 0.9f else 3.2f)
        val e = (energy + if (state == JarvisState.IDLE || state == JarvisState.THINKING) 0.12f * pulse else 0f)
            .coerceIn(0f, 1f)

        // Ambient glow: wider and brighter when awake.
        drawCircle(
            Brush.radialGradient(
                listOf(tone.copy(alpha = 0.08f + 0.14f * e + 0.06f * awake), tone.copy(alpha = 0.02f), Color.Transparent),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )

        // Listening: soft ripples rolling outward from the housing.
        if (listening > 0.01f) {
            for (k in 0 until 2) {
                val p = ((time / 1.8f) + k * 0.5f) % 1f
                drawCircle(
                    tone.copy(alpha = (1f - p) * 0.30f * listening),
                    radius = r * (0.44f + 0.50f * p),
                    center = c,
                    style = Stroke(width = hair * (1.5f - p)),
                )
            }
        }

        // Outer HUD gauges — both fill upward from the bottom.
        gauge(c, r * 0.97f, startDeg = 125f, sweepDeg = 110f, fraction = batteryPercent / 100f, color = batteryColor, fillFromEnd = false, width = 2.5.dp.toPx())
        gauge(c, r * 0.97f, startDeg = -55f, sweepDeg = 110f, fraction = fuelPercent / 100f, color = fuelColor, fillFromEnd = true, width = 2.5.dp.toPx())
        // Hairline outer ring.
        drawCircle(tone.copy(alpha = 0.16f), radius = r * 0.905f, center = c, style = Stroke(hair))

        // Tick ring
        rotate(angle * 0.25f, c) {
            val outer = r * 0.86f
            for (i in 0 until 72) {
                val a = i * 5f * PI.toFloat() / 180f
                val major = i % 6 == 0
                val len = if (major) r * 0.05f else r * 0.022f
                val p1 = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer)
                val p2 = Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len))
                drawLine(tone.copy(alpha = if (major) 0.75f else 0.30f), p1, p2, strokeWidth = if (major) 2f else 1.5f)
            }
        }

        // Voice ring: fine spikes that breathe with the mic / speech level.
        run {
            val base = r * 0.775f
            val spikes = 96
            for (i in 0 until spikes) {
                val a = i * (2f * PI.toFloat() / spikes)
                val wave = (0.55f + 0.45f * sin(i * 0.61f + time * 7f)) * (0.65f + 0.35f * sin(i * 0.23f - time * 2.6f))
                val amp = (voice * wave).coerceIn(0f, 1f)
                val len = r * (0.008f + 0.075f * amp)
                val p1 = Offset(c.x + cos(a) * base, c.y + sin(a) * base)
                val p2 = Offset(c.x + cos(a) * (base + len), c.y + sin(a) * (base + len))
                drawLine(tone.copy(alpha = 0.22f + 0.65f * amp), p1, p2, strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }

        // Segmented ring
        rotate(angle, c) {
            val rr = r * 0.71f
            val segs = 12
            val gap = 7f
            val sweep = 360f / segs - gap
            for (i in 0 until segs) {
                drawArc(
                    color = tone.copy(alpha = 0.45f + 0.45f * e),
                    startAngle = i * 360f / segs,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = Size(rr * 2, rr * 2),
                    style = Stroke(width = r * 0.04f),
                )
            }
        }

        // Thinking: a bright comet chasing round the segmented ring.
        if (thinking > 0.01f) {
            rotate(angle * 3f, c) {
                val rr = r * 0.665f
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.30f to Hud.White.copy(alpha = 0.95f * thinking),
                        0.305f to Color.Transparent,
                        1f to Color.Transparent,
                        center = c,
                    ),
                    startAngle = 0f,
                    sweepAngle = 109f,
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = Size(rr * 2, rr * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Counter-rotating thin arcs
        rotate(-angle * 1.6f, c) {
            val rr = r * 0.61f
            for (i in 0 until 3) {
                drawArc(
                    color = Hud.Ice.copy(alpha = 0.75f),
                    startAngle = i * 120f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = Size(rr * 2, rr * 2),
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
            }
        }

        // Coil ring (the classic reactor "windings")
        val coilR = r * 0.50f
        for (i in 0 until 10) {
            drawArc(
                color = tone.copy(alpha = 0.32f + 0.5f * e),
                startAngle = i * 36f + 4f,
                sweepAngle = 28f,
                useCenter = false,
                topLeft = Offset(c.x - coilR, c.y - coilR),
                size = Size(coilR * 2, coilR * 2),
                style = Stroke(width = r * 0.10f),
            )
        }
        // Housing: a dark well with a lit rim.
        drawCircle(
            Brush.radialGradient(
                0f to Hud.Abyss,
                0.75f to Hud.Background,
                1f to Hud.PanelHi,
                center = c, radius = r * 0.42f,
            ),
            radius = r * 0.42f, center = c,
        )
        drawCircle(tone.copy(alpha = 0.35f), radius = r * 0.42f, center = c, style = Stroke(hair))

        // Inner hairline ring, turning slowly (the triangle looked cheap).
        rotate(-angle * 0.4f, c) {
            drawArc(tone.copy(alpha = 0.45f + 0.3f * e), 0f, 300f, false, Offset(c.x - r * 0.32f, c.y - r * 0.32f), Size(r * 0.64f, r * 0.64f), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
        }

        // Core
        val coreR = r * (0.18f + 0.15f * e)
        drawCircle(
            Brush.radialGradient(
                0f to Hud.White,
                0.35f to Hud.Ice,
                0.7f to tone.copy(alpha = 0.8f),
                1f to Color.Transparent,
                center = c, radius = coreR * 1.6f,
            ),
            radius = coreR * 1.6f, center = c,
        )
        drawCircle(Hud.White.copy(alpha = 0.92f), radius = coreR * 0.45f, center = c)
    }
}

private fun DrawScope.gauge(
    c: Offset,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    fraction: Float,
    color: Color,
    fillFromEnd: Boolean,
    width: Float,
) {
    val topLeft = Offset(c.x - radius, c.y - radius)
    val sz = Size(radius * 2, radius * 2)
    drawArc(Hud.Faint, startDeg, sweepDeg, false, topLeft, sz, style = Stroke(width = width, cap = StrokeCap.Round))
    val f = fraction.coerceIn(0f, 1f)
    if (f > 0f) {
        val from = if (fillFromEnd) startDeg + sweepDeg * (1 - f) else startDeg
        drawArc(color, from, sweepDeg * f, false, topLeft, sz, style = Stroke(width = width, cap = StrokeCap.Round))
    }
}
