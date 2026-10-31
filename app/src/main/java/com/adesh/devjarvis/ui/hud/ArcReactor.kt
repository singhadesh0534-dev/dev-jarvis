package com.adesh.devjarvis.ui.hud

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
 * listening = mic level, speaking = TTS level, thinking = fast spin, idle = slow saans jaisa pulse.
 * Bahar ke HUD arcs: left = battery, right = API fuel.
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
) {
    val targetEnergy = when (state) {
        JarvisState.LISTENING -> 0.35f + 0.65f * micLevel
        JarvisState.SPEAKING -> 0.3f + 0.7f * speechLevel
        JarvisState.THINKING -> 0.55f
        JarvisState.IDLE -> 0.3f
    }
    val energy by animateFloatAsState(targetEnergy, spring(stiffness = Spring.StiffnessMediumLow), label = "energy")
    val targetSpeed = when (state) {
        JarvisState.THINKING -> 4.5f
        JarvisState.LISTENING -> 1.8f
        JarvisState.SPEAKING -> 1.4f
        JarvisState.IDLE -> 0.6f
    }
    val speed by animateFloatAsState(targetSpeed, label = "speed")
    val currentSpeed by rememberUpdatedState(speed)

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
                    time += dt
                }
                last = now
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Orbitron, fontSize = 9.sp, letterSpacing = 1.sp)
    val batteryColor = if (batteryPercent in 0..19 && !charging) Hud.Amber else Hud.Cyan
    val fuelColor = if (fuelPercent < 15) Hud.Amber else Hud.Cyan

    Canvas(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onTap,
        )
    ) {
        val r = min(size.width, size.height) / 2f
        val c = center
        val pulse = 0.5f + 0.5f * sin(time * 2f * PI.toFloat() / if (state == JarvisState.THINKING) 0.9f else 3.2f)
        val e = (energy + if (state == JarvisState.IDLE || state == JarvisState.THINKING) 0.12f * pulse else 0f)
            .coerceIn(0f, 1f)

        // Ambient glow
        drawCircle(
            Brush.radialGradient(
                listOf(Hud.Cyan.copy(alpha = 0.10f + 0.18f * e), Color.Transparent),
                center = c, radius = r,
            ),
            radius = r, center = c,
        )

        // Outer HUD gauges
        // Both gauges fill upward from the bottom.
        gauge(c, r * 0.97f, startDeg = 125f, sweepDeg = 110f, fraction = batteryPercent / 100f, color = batteryColor, fillFromEnd = false)
        gauge(c, r * 0.97f, startDeg = -55f, sweepDeg = 110f, fraction = fuelPercent / 100f, color = fuelColor, fillFromEnd = true)
        val battText = if (batteryPercent < 0) "PWR --" else "PWR $batteryPercent%" + if (charging) "+" else ""
        val fuelText = "FUEL $fuelPercent%"
        val bt = textMeasurer.measure(battText, labelStyle.copy(color = batteryColor))
        val ft = textMeasurer.measure(fuelText, labelStyle.copy(color = fuelColor))
        drawText(bt, topLeft = Offset(c.x - r * 0.97f + 4f, c.y + r * 0.62f))
        drawText(ft, topLeft = Offset(c.x + r * 0.97f - ft.size.width - 4f, c.y + r * 0.62f))

        // Tick ring
        rotate(angle * 0.25f, c) {
            val outer = r * 0.86f
            for (i in 0 until 72) {
                val a = i * 5f * PI.toFloat() / 180f
                val len = if (i % 6 == 0) r * 0.05f else r * 0.022f
                val p1 = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer)
                val p2 = Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len))
                drawLine(Hud.Cyan.copy(alpha = if (i % 6 == 0) 0.75f else 0.35f), p1, p2, strokeWidth = 2f)
            }
        }

        // Segmented ring
        rotate(angle, c) {
            val rr = r * 0.74f
            val segs = 12
            val gap = 7f
            val sweep = 360f / segs - gap
            for (i in 0 until segs) {
                drawArc(
                    color = Hud.Cyan.copy(alpha = 0.55f + 0.4f * e),
                    startAngle = i * 360f / segs,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = Size(rr * 2, rr * 2),
                    style = Stroke(width = r * 0.045f),
                )
            }
        }

        // Counter-rotating thin arcs
        rotate(-angle * 1.6f, c) {
            val rr = r * 0.63f
            for (i in 0 until 3) {
                drawArc(
                    color = Hud.Ice.copy(alpha = 0.8f),
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
                color = Hud.Cyan.copy(alpha = 0.35f + 0.5f * e),
                startAngle = i * 36f + 4f,
                sweepAngle = 28f,
                useCenter = false,
                topLeft = Offset(c.x - coilR, c.y - coilR),
                size = Size(coilR * 2, coilR * 2),
                style = Stroke(width = r * 0.10f),
            )
        }
        drawCircle(Hud.Background, radius = r * 0.42f, center = c)

        // Core
        val coreR = r * (0.20f + 0.16f * e)
        drawCircle(
            Brush.radialGradient(
                0f to Hud.White,
                0.35f to Hud.Ice,
                0.7f to Hud.Cyan.copy(alpha = 0.8f),
                1f to Color.Transparent,
                center = c, radius = coreR * 1.6f,
            ),
            radius = coreR * 1.6f, center = c,
        )
        drawCircle(Hud.White.copy(alpha = 0.9f), radius = coreR * 0.45f, center = c)
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
) {
    val topLeft = Offset(c.x - radius, c.y - radius)
    val sz = Size(radius * 2, radius * 2)
    drawArc(Hud.Faint, startDeg, sweepDeg, false, topLeft, sz, style = Stroke(width = 6f, cap = StrokeCap.Round))
    val f = fraction.coerceIn(0f, 1f)
    if (f > 0f) {
        val from = if (fillFromEnd) startDeg + sweepDeg * (1 - f) else startDeg
        drawArc(color, from, sweepDeg * f, false, topLeft, sz, style = Stroke(width = 6f, cap = StrokeCap.Round))
    }
}
