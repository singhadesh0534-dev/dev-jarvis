package com.adesh.devjarvis.ui.hud

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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
import kotlin.random.Random

/** Warm core (the heart of the reactor) and gold sparks — from Sir's reference pictures. */
private val CoreHot = Color(0xFFFF6A3D)
private val CoreRed = Color(0xFFFF3B4E)
private val Gold = Color(0xFFFFC46B)
private val Steel = Color(0xFF0B1B2B)
private val SteelHi = Color(0xFF2A4A66)

/** Seconds since the screen opened, ticking every frame (read it in draw / layer lambdas: no recomposition). */
@Composable
fun rememberHoloClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = ((it - start) / 1_000_000_000.0 % 3600.0).toFloat() }
    }
    return t
}

/**
 * How the phone is tilted, -1..1 on each axis, smoothed and slowly re-centred (holding it at any angle is "straight").
 * Drives the 3D parallax: layers at different depths move differently, like a real hologram.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt: MutableState<Offset> = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseX = Float.NaN
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                if (baseX.isNaN()) { baseX = x; baseY = y }
                baseX = baseX * 0.994f + x * 0.006f
                baseY = baseY * 0.994f + y * 0.006f
                val tx = ((x - baseX) / 3.5f).coerceIn(-1f, 1f)
                val ty = ((y - baseY) / 3.5f).coerceIn(-1f, 1f)
                val o = tilt.value
                tilt.value = Offset(o.x + (tx - o.x) * 0.18f, o.y + (ty - o.y) * 0.18f)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}

/**
 * The holographic 3D reactor: an armoured outer ring with four clamps and a glowing groove, a tick dial with live
 * read-outs, counter-rotating dashed rings, a dark mechanism with spokes and bolts, a hot red-orange core and drifting
 * gold sparks. Every layer sits at its own depth and moves with the phone's tilt (parallax), and the whole disc is
 * seen in perspective. Listening / speaking make it glow and ripple with the voice; thinking spins a comet arc.
 */
@Composable
fun HoloReactor(
    state: JarvisState,
    micLevel: Float,
    speechLevel: Float,
    batteryPercent: Int,
    charging: Boolean,
    fuelPercent: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
    tilt: State<Offset> = rememberTilt(),
    clock: State<Float> = rememberHoloClock(),
) {
    val targetEnergy = when (state) {
        JarvisState.LISTENING -> 0.35f + 0.65f * micLevel
        JarvisState.SPEAKING -> 0.3f + 0.7f * speechLevel
        JarvisState.THINKING -> 0.6f
        JarvisState.IDLE -> 0.28f
    }
    val energy by animateFloatAsState(targetEnergy, spring(stiffness = Spring.StiffnessMediumLow), label = "energy")
    val voice by animateFloatAsState(
        when (state) { JarvisState.LISTENING -> micLevel; JarvisState.SPEAKING -> speechLevel; else -> 0f },
        spring(stiffness = Spring.StiffnessMedium), label = "voice",
    )
    val speed by animateFloatAsState(
        when (state) { JarvisState.THINKING -> 4.2f; JarvisState.LISTENING -> 1.8f; JarvisState.SPEAKING -> 1.4f; JarvisState.IDLE -> 0.55f },
        label = "speed",
    )
    val currentSpeed by rememberUpdatedState(speed)
    val thinking by animateFloatAsState(if (state == JarvisState.THINKING) 1f else 0f, tween(450), label = "thinking")
    val tone by animateColorAsState(if (state == JarvisState.THINKING) lerp(accent, Hud.Ice, 0.4f) else accent, tween(600), label = "tone")

    // Spin angle integrates speed, so a speed change never makes the rings jump.
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L) angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * 30f * currentSpeed) % 360f
            last = now
        }
    }

    val sparks = remember { val rnd = Random(11); List(46) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    val textMeasurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Orbitron, fontSize = 8.sp, letterSpacing = 1.5.sp)
    val bat = remember(batteryPercent, charging, tone) {
        textMeasurer.measure("PWR ${batteryPercent.coerceIn(0, 100)}%${if (charging) "+" else ""}", label.copy(color = tone.copy(alpha = 0.8f)))
    }
    val fuel = remember(fuelPercent, tone) { textMeasurer.measure("FUEL ${fuelPercent.coerceIn(0, 100)}%", label.copy(color = tone.copy(alpha = 0.8f))) }
    val name = remember(tone) { textMeasurer.measure("J.A.R.V.I.S", label.copy(color = tone.copy(alpha = 0.9f))) }
    val mode = remember(state, tone) {
        val m = when (state) { JarvisState.LISTENING -> "LISTENING"; JarvisState.SPEAKING -> "SPEAKING"; JarvisState.THINKING -> "PROCESSING"; JarvisState.IDLE -> "STANDBY" }
        textMeasurer.measure(m, label.copy(color = Gold.copy(alpha = 0.85f)))
    }

    Canvas(
        modifier
            .graphicsLayer {
                // The disc seen in 3D: a gentle perspective plus the phone's tilt.
                val t = tilt.value
                rotationX = 8f - t.y * 12f
                rotationY = t.x * 14f
                cameraDistance = 14f * density
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        val r = min(size.width, size.height) / 2f
        val c = center
        val time = clock.value
        val a = angle.floatValue
        val t = tilt.value
        fun depth(d: Float, block: DrawScope.() -> Unit) = translate(t.x * d * r * 0.05f, t.y * d * r * 0.05f, block)

        // 0. glow behind everything
        drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.22f + 0.2f * energy), tone.copy(alpha = 0.05f), Color.Transparent), c, r * 1.05f), r * 1.05f, c)

        // 1. outer tick dial with live read-outs (back layer)
        depth(-1f) {
            rotate(a * 0.15f, c) {
                for (i in 0 until 120) {
                    val ang = i * 3.0 * PI / 180
                    val long = i % 10 == 0
                    val r1 = r * (if (long) 0.90f else 0.935f)
                    drawLine(tone.copy(alpha = if (long) 0.55f else 0.2f),
                        Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                        Offset(c.x + r * 0.965f * cos(ang).toFloat(), c.y + r * 0.965f * sin(ang).toFloat()),
                        if (long) 2.2f else 1f)
                }
            }
            drawText(name, topLeft = Offset(c.x - name.size.width / 2f, c.y - r * 0.995f - name.size.height * 0.1f))
            drawText(mode, topLeft = Offset(c.x - mode.size.width / 2f, c.y + r * 0.995f - mode.size.height * 0.9f))
            drawText(bat, topLeft = Offset(c.x - r * 0.99f, c.y - bat.size.height / 2f - r * 0.62f))
            drawText(fuel, topLeft = Offset(c.x + r * 0.99f - fuel.size.width, c.y - fuel.size.height / 2f - r * 0.62f))
        }

        // 2. armoured main ring with a glowing groove and four clamps
        depth(0f) {
            val rr = r * 0.78f
            val w = r * 0.12f
            drawCircle(Steel, rr, c, style = Stroke(w))
            drawCircle(SteelHi.copy(alpha = 0.7f), rr + w / 2f, c, style = Stroke(1.6f))
            drawCircle(SteelHi.copy(alpha = 0.5f), rr - w / 2f, c, style = Stroke(1.2f))
            val glow = 0.55f + 0.45f * energy
            for (q in 0 until 4) {
                val start = q * 90f + 14f + a * 0.05f
                drawArc(tone.copy(alpha = 0.18f * glow), start, 62f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(w * 0.55f, cap = StrokeCap.Round))
                drawArc(tone.copy(alpha = 0.45f * glow), start, 62f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(w * 0.22f, cap = StrokeCap.Round))
                drawArc(Color.White.copy(alpha = 0.75f * glow), start, 62f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(w * 0.06f, cap = StrokeCap.Round))
            }
            for (q in 0 until 4) rotate(q * 90f + a * 0.05f, c) {
                val cw = w * 1.25f
                val ch = w * 0.9f
                drawRoundRect(Steel, Offset(c.x + rr - cw / 2f, c.y - ch / 2f), Size(cw, ch), CornerRadius(ch * 0.25f))
                drawRoundRect(tone.copy(alpha = 0.85f), Offset(c.x + rr - cw / 2f, c.y - ch / 2f), Size(cw, ch), CornerRadius(ch * 0.25f), style = Stroke(1.8f))
                drawLine(tone.copy(alpha = 0.9f), Offset(c.x + rr - cw * 0.25f, c.y), Offset(c.x + rr + cw * 0.25f, c.y), 2.4f, StrokeCap.Round)
            }
        }

        // 3. counter-rotating dashed rings
        depth(0.6f) {
            rotate(-a * 0.8f, c) {
                val rr = r * 0.62f
                for (i in 0 until 48) if (i % 4 != 3) {
                    drawArc(tone.copy(alpha = 0.55f), i * 7.5f, 4.5f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.025f))
                }
            }
            rotate(a * 1.3f, c) {
                val rr = r * 0.56f
                drawArc(tone.copy(alpha = 0.8f), 0f, 110f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(2.2f, cap = StrokeCap.Round))
                drawArc(Gold.copy(alpha = 0.7f), 180f, 60f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(2.2f, cap = StrokeCap.Round))
            }
            // voice ripple: the ring breathes with Sir's / JARVIS's voice
            if (voice > 0.02f) {
                val base = r * 0.69f
                var prev: Offset? = null
                for (i in 0..120) {
                    val ang = i / 120.0 * 2 * PI
                    val wob = sin(ang * 9 + time * 7) * 0.5 + sin(ang * 4 - time * 5) * 0.5
                    val rad = base + (r * 0.07f * voice * wob).toFloat()
                    val p = Offset(c.x + rad * cos(ang).toFloat(), c.y + rad * sin(ang).toFloat())
                    prev?.let { drawLine(tone.copy(alpha = 0.85f), it, p, 2f) }
                    prev = p
                }
            }
        }

        // 4. dark mechanism: disc, spokes, bolts
        depth(1f) {
            val rr = r * 0.46f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0E2236), Color(0xFF050B14)), c, rr), rr, c)
            drawCircle(tone.copy(alpha = 0.35f), rr, c, style = Stroke(1.5f))
            rotate(a * 0.4f, c) {
                for (i in 0 until 12) {
                    val ang = i * 30.0 * PI / 180
                    drawLine(SteelHi.copy(alpha = 0.8f), Offset(c.x + rr * 0.42f * cos(ang).toFloat(), c.y + rr * 0.42f * sin(ang).toFloat()),
                        Offset(c.x + rr * 0.92f * cos(ang).toFloat(), c.y + rr * 0.92f * sin(ang).toFloat()), 2.2f)
                    if (i % 3 == 0) drawCircle(tone.copy(alpha = 0.7f), r * 0.018f, Offset(c.x + rr * 0.8f * cos(ang).toFloat(), c.y + rr * 0.8f * sin(ang).toFloat()))
                }
            }
        }

        // 5. the hot core (red-orange, pulsing with the voice)
        depth(1.5f) {
            val pulse = 1f + 0.18f * energy + 0.12f * voice * sin(time * 18f)
            val cr = r * 0.2f * pulse
            drawCircle(Brush.radialGradient(listOf(CoreHot.copy(alpha = 0.55f), CoreRed.copy(alpha = 0.15f), Color.Transparent), c, cr * 1.8f), cr * 1.8f, c)
            drawCircle(CoreRed.copy(alpha = 0.9f), cr, c, style = Stroke(r * 0.022f))
            rotate(-a * 2f, c) {
                drawArc(CoreHot, 20f, 120f, false, Offset(c.x - cr * 0.72f, c.y - cr * 0.72f), Size(cr * 1.44f, cr * 1.44f), style = Stroke(r * 0.018f, cap = StrokeCap.Round))
                drawArc(CoreHot.copy(alpha = 0.6f), 200f, 70f, false, Offset(c.x - cr * 0.72f, c.y - cr * 0.72f), Size(cr * 1.44f, cr * 1.44f), style = Stroke(r * 0.012f, cap = StrokeCap.Round))
            }
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE2C2), CoreHot.copy(alpha = 0f)), c, cr * 0.55f), cr * 0.55f, c)
        }

        // 6. thinking: a comet arc racing round
        if (thinking > 0.01f) depth(0.2f) {
            val rr = r * 0.86f
            rotate(a * 3f, c) {
                drawArc(Brush.sweepGradient(listOf(Color.Transparent, tone.copy(alpha = thinking), Color.Transparent), c), 0f, 80f, false,
                    Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
            }
        }

        // 7. gold sparks drifting round (faster and brighter with energy)
        depth(2f) {
            sparks.forEach { s ->
                val orbit = r * (0.5f + s[0] * 0.48f)
                val ang = (s[1] * 2 * PI + time * (0.15 + s[2] * 0.35) * (0.6 + energy)).toFloat()
                val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * (2f + s[3] * 4f) + s[1] * 10f))
                val p = Offset(c.x + orbit * cos(ang), c.y + orbit * sin(ang) * 0.92f)
                drawCircle(Gold.copy(alpha = 0.18f * tw), r * 0.022f, p)
                drawCircle(Gold.copy(alpha = 0.9f * tw * (0.5f + energy)), r * 0.006f + 1f, p)
            }
        }

        // 8. a hologram scan line sweeping down the disc
        val sy = c.y - r + ((time * 0.35f) % 1f) * 2f * r
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, tone.copy(alpha = 0.18f), Color.Transparent), c.x - r, c.x + r),
            Offset(c.x - r, sy), Offset(c.x + r, sy), 2f, blendMode = BlendMode.Plus)
    }
}

/**
 * Blueprint backdrop for the HUD: a fine grid, large technical circles with tick marks and drifting dust, moving with
 * the phone's tilt behind the reactor (the deepest layer, so it moves the least).
 */
fun Modifier.holoBackdrop(clock: State<Float>, tilt: State<Offset>, accent: Color): Modifier = drawBehind {
    drawRect(Brush.radialGradient(listOf(Color(0xFF0C2A45), Color(0xFF061322), Color(0xFF020509)), Offset(size.width / 2f, size.height * 0.36f), size.maxDimension * 0.75f))
    val time = clock.value
    val t = tilt.value
    translate(-t.x * 10f, -t.y * 10f) {
        val step = 28f * density
        var x = (time * 2f) % step
        while (x < size.width) { drawLine(accent.copy(alpha = 0.045f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(accent.copy(alpha = 0.045f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
        val cc = Offset(size.width / 2f, size.height * 0.36f)
        for (k in 1..3) drawCircle(accent.copy(alpha = 0.05f), size.width * (0.42f + k * 0.12f), cc, style = Stroke(1f))
        rotate(time * 3f, cc) {
            val rr = size.width * 0.66f
            for (i in 0 until 72) {
                val ang = i * 5.0 * PI / 180
                val len = if (i % 6 == 0) 10f else 4f
                drawLine(accent.copy(alpha = 0.09f), Offset(cc.x + rr * cos(ang).toFloat(), cc.y + rr * sin(ang).toFloat()),
                    Offset(cc.x + (rr + len) * cos(ang).toFloat(), cc.y + (rr + len) * sin(ang).toFloat()), 1f)
            }
        }
    }
    translate(-t.x * 22f, -t.y * 22f) {
        for (i in 0 until 40) {
            val px = ((i * 97.13f) % 1f + (i * 0.618f) % 1f) % 1f
            val py = ((i * 0.381f + time * (0.004f + (i % 5) * 0.002f)) % 1f)
            val a = 0.15f + 0.35f * (0.5f + 0.5f * sin(time * 1.4f + i))
            drawCircle(Hud.Ice.copy(alpha = a), 1.3f * density / 2f + 0.6f, Offset(px * size.width, (1f - py) * size.height))
        }
    }
}

/**
 * A sci-fi HUD frame (like the reference dashboards): a dark translucent panel with a thin edge, bright corner brackets
 * and a small notch on top. Use on status, messages and the input dock so they all speak one design language.
 */
fun Modifier.holoFrame(accent: Color, fill: Float = 0.42f, bracket: Float = 12f): Modifier = this
    .drawWithContent {
        val radius = 7.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF0E2438).copy(alpha = fill + 0.12f), Color(0xFF06111D).copy(alpha = fill))),
            cornerRadius = CornerRadius(radius),
        )
        drawRoundRect(accent.copy(alpha = 0.16f), cornerRadius = CornerRadius(radius), style = Stroke(1.dp.toPx()))
        drawContent()
        val b = bracket.dp.toPx()
        val w = 2.dp.toPx()
        val col = accent.copy(alpha = 0.9f)
        val (x0, y0, x1, y1) = listOf(w / 2, w / 2, size.width - w / 2, size.height - w / 2)
        drawLine(col, Offset(x0, y0), Offset(x0 + b, y0), w, StrokeCap.Round); drawLine(col, Offset(x0, y0), Offset(x0, y0 + b), w, StrokeCap.Round)
        drawLine(col, Offset(x1, y0), Offset(x1 - b, y0), w, StrokeCap.Round); drawLine(col, Offset(x1, y0), Offset(x1, y0 + b), w, StrokeCap.Round)
        drawLine(col, Offset(x0, y1), Offset(x0 + b, y1), w, StrokeCap.Round); drawLine(col, Offset(x0, y1), Offset(x0, y1 - b), w, StrokeCap.Round)
        drawLine(col, Offset(x1, y1), Offset(x1 - b, y1), w, StrokeCap.Round); drawLine(col, Offset(x1, y1), Offset(x1, y1 - b), w, StrokeCap.Round)
        drawLine(col.copy(alpha = 0.6f), Offset(size.width / 2f - b, y0), Offset(size.width / 2f + b, y0), w)
    }

/** A microphone drawn in lines (the core icon set has none). */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val s = Stroke(w * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(w * 0.36f, w * 0.08f), Size(w * 0.28f, w * 0.5f), CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.22f, w * 0.26f), Size(w * 0.56f, w * 0.48f), style = s)
        drawLine(color, Offset(w * 0.5f, w * 0.74f), Offset(w * 0.5f, w * 0.9f), w * 0.1f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, w * 0.92f), Offset(w * 0.66f, w * 0.92f), w * 0.1f, StrokeCap.Round)
    }
}
