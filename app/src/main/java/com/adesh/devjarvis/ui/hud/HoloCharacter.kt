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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.lerp
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.rememberHoloClock
import com.adesh.devjarvis.ui.theme.rememberTilt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val CoreHot = Color(0xFFFF6A3D)
private val CoreRed = Color(0xFFFF3B4E)
private val Gold = Color(0xFFFFC46B)
private val Steel = Color(0xFF0B1B2B)
private val SteelHi = Color(0xFF2A4A66)

/** Camera distance for the perspective (in head units). */
private const val CAM = 5f

/**
 * The AI character: a holographic 3D head and shoulders made of light, rising from a projector pad (the reactor seen
 * from the side), drawn as a real 3D wireframe with perspective — the near side bright, the far side faint.
 * It works where Sir can see it:
 * - listening: turns to face him, eyes bright, sound waves rise at the ears with his voice;
 * - thinking: a scan sweeps the head and a data ring with nodes orbits it, eyes flicker;
 * - speaking: the mouth moves with JARVIS's voice and the projector pulses;
 * - always: breathes, sways, blinks, and turns with the phone's tilt.
 * All motion runs on the one capped hologram clock and is read only while drawing.
 */
@Composable
fun HoloCharacter(
    state: JarvisState,
    micLevel: Float,
    speechLevel: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
    tilt: State<Offset> = rememberTilt(),
    clock: State<Float> = rememberHoloClock(),
) {
    val energy by animateFloatAsState(
        when (state) {
            JarvisState.LISTENING -> 0.45f + 0.55f * micLevel
            JarvisState.SPEAKING -> 0.4f + 0.6f * speechLevel
            JarvisState.THINKING -> 0.7f
            JarvisState.IDLE -> 0.3f
        },
        spring(stiffness = Spring.StiffnessMediumLow), label = "energy",
    )
    val mic = rememberUpdatedState(if (state == JarvisState.LISTENING) micLevel else 0f)
    val speech = rememberUpdatedState(if (state == JarvisState.SPEAKING) speechLevel else 0f)
    val listening by animateFloatAsState(if (state == JarvisState.LISTENING) 1f else 0f, tween(400), label = "listening")
    val thinking by animateFloatAsState(if (state == JarvisState.THINKING) 1f else 0f, tween(450), label = "thinking")
    val tone by animateColorAsState(if (state == JarvisState.THINKING) lerp(accent, Hud.Ice, 0.35f) else accent, tween(600), label = "tone")

    val sparks = remember { val rnd = Random(5); List(22) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) } }
    val front = remember { Path() }
    val back = remember { Path() }
    val body = remember { Path() }
    val bodyBack = remember { Path() }
    val orbitFront = remember { Path() }
    val orbitBack = remember { Path() }
    val scan = remember { Path() }
    val feature = remember { Path() }

    Canvas(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        val time = clock.value
        val t = tilt.value
        // The figure is taller than wide: in a tall box it grows to use the height (centred).
        val s = minOf(size.width * 1.2f, size.height)
        val oy = (size.height - s) / 2f
        val cx = size.width / 2f
        val unit = s * 0.215f
        val headY = oy + s * 0.30f
        val padC = Offset(cx, oy + s * 0.865f)
        val padRx = minOf(s * 0.36f, size.width * 0.47f)
        val padRy = s * 0.075f
        // Hologram flicker: a short dip now and then.
        val flick = if ((time * 1000).toInt() % 6100 < 70) 0.6f else 1f
        val line = tone.copy(alpha = 0.85f * flick)

        // Head pose: sways when idle, faces Sir while listening, follows the phone's tilt.
        val sway = (1f - 0.75f * listening)
        val yaw = 0.32f * sin(time * 0.42f) * sway + 0.12f * sin(time * 0.17f) * sway + t.x * 0.5f
        val pitch = 0.06f + 0.05f * sin(time * 0.31f) * sway - t.y * 0.22f - 0.05f * listening
        val breathe = 1f + 0.012f * sin(time * 1.6f)
        val p = Proj(cx, headY, unit * breathe, yaw, pitch)

        // 0. glow behind the figure
        drawCircle(
            Brush.radialGradient(listOf(tone.copy(alpha = 0.16f + 0.14f * energy), Color.Transparent), Offset(cx, headY + unit * 0.4f), s * 0.5f),
            s * 0.5f, Offset(cx, headY + unit * 0.4f),
        )

        // 1. the light cone from the projector up to the head
        val cone = Path().apply {
            moveTo(padC.x - padRx * 0.55f, padC.y)
            lineTo(padC.x + padRx * 0.55f, padC.y)
            lineTo(cx + unit * 1.75f, headY - unit * 0.9f)
            lineTo(cx - unit * 1.75f, headY - unit * 0.9f)
            close()
        }
        drawPath(cone, Brush.verticalGradient(listOf(Color.Transparent, tone.copy(alpha = 0.05f + 0.05f * energy), tone.copy(alpha = 0.16f + 0.1f * energy)), headY - unit, padC.y))

        // 2. the projector pad (the reactor seen from the side)
        drawPad(padC, padRx, padRy, time, energy, speech.value, tone)

        // 3. the data ring's far half (thinking), behind the head
        orbitBack.reset(); orbitFront.reset()
        if (thinking > 0.01f) orbit(p, time, orbitFront, orbitBack)
        if (thinking > 0.01f) drawPath(orbitBack, tone.copy(alpha = 0.25f * thinking), style = Stroke(1.2f))

        // 4. the bust: neck and shoulders fading downward, then the head (far side faint, near side bright)
        body.reset(); bodyBack.reset()
        bust(p, body, bodyBack)
        val bodyTop = headY + unit * 0.9f
        val bodyBottom = headY + unit * 2.2f
        drawPath(bodyBack, Brush.verticalGradient(listOf(tone.copy(alpha = 0.18f), Color.Transparent), bodyTop, bodyBottom), style = Stroke(1f))
        drawPath(body, Brush.verticalGradient(listOf(tone.copy(alpha = 0.75f * flick), Color.Transparent), bodyTop, bodyBottom), style = Stroke(1.4f))

        front.reset(); back.reset()
        head(p, front, back)
        drawPath(back, tone.copy(alpha = 0.16f * flick), style = Stroke(1f))
        drawPath(front, line.copy(alpha = 0.22f * flick), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) // soft glow
        drawPath(front, line, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 5. face: brows, eyes (blinking), nose and the mouth that moves with the voice
        val blink = (time % 4.6f).let { ph -> if (ph < 0.16f) abs(ph - 0.08f) / 0.08f else 1f }
        val eyeGlow = when {
            thinking > 0.5f -> 0.55f + 0.35f * (0.5f + 0.5f * sin(time * 9f))
            else -> 0.75f + 0.25f * listening
        }
        val sp = speech.value
        val mouthOpen = if (sp > 0.02f) (0.025f + 0.12f * sp * (0.55f + 0.45f * abs(sin(time * 17f)))) else 0.006f
        face(p, feature, blink, mouthOpen)
        drawPath(feature, Hud.White.copy(alpha = 0.9f * flick), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        eyes(p, blink, eyeGlow * flick, tone)

        // 6. listening: sound waves rising at the ears with Sir's voice
        if (listening > 0.01f) {
            for (side in listOf(-1f, 1f)) {
                p.go(side * 0.78f, 0.02f, 0f)
                if (p.z < -0.2f) continue
                val ear = Offset(p.x, p.y)
                for (k in 0 until 3) {
                    val ph = (time * 0.9f + k / 3f) % 1f
                    val a = (1f - ph) * listening * (0.25f + 0.6f * mic.value)
                    drawArc(
                        tone.copy(alpha = a), if (side < 0) 120f else -60f, 120f, false,
                        Offset(ear.x - unit * (0.1f + 0.45f * ph), ear.y - unit * (0.1f + 0.45f * ph)),
                        Size(unit * (0.2f + 0.9f * ph), unit * (0.2f + 0.9f * ph)), style = Stroke(1.6f, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // 7. thinking: a scan sweeping the head, the data ring's near half with its nodes
        if (thinking > 0.01f) {
            val v = 0.92f * sin(time * 1.7f)
            scan.reset()
            ring(p, v, scan, null, frontOnly = true)
            drawPath(scan, Hud.White.copy(alpha = 0.85f * thinking), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(scan, tone.copy(alpha = 0.3f * thinking), style = Stroke(7f, cap = StrokeCap.Round))
            drawPath(orbitFront, tone.copy(alpha = 0.75f * thinking), style = Stroke(1.6f))
            for (k in 0 until 3) {
                val a = time * 1.3f + k * 2.094f
                orbitPoint(p, a)
                if (p.z > -0.1f) {
                    drawCircle(tone.copy(alpha = 0.35f * thinking), unit * 0.07f, Offset(p.x, p.y))
                    drawCircle(Hud.White.copy(alpha = thinking), unit * 0.025f, Offset(p.x, p.y))
                }
            }
        }

        // 8. gold sparks drifting up the light
        sparks.forEach { sk ->
            val up = (sk[1] + time * (0.05f + sk[2] * 0.08f) * (0.7f + energy)) % 1f
            val y = padC.y - up * (padC.y - headY + unit)
            val spread = 0.45f + 0.55f * up
            val x = cx + (sk[0] - 0.5f) * 2f * padRx * spread
            val a = (1f - up) * (0.35f + 0.5f * energy)
            drawCircle(Gold.copy(alpha = a * 0.3f), unit * 0.035f, Offset(x, y))
            drawCircle(Gold.copy(alpha = a), unit * 0.012f + 0.8f, Offset(x, y))
        }

        // 9. hologram texture: faint scan lines over the figure
        val step = 3.2f * density
        var yy = headY - unit * 1.05f
        while (yy < bodyBottom) {
            drawLine(tone.copy(alpha = 0.035f), Offset(cx - unit * 1.5f, yy), Offset(cx + unit * 1.5f, yy), 1f)
            yy += step
        }
    }
}

/** Perspective projection with a head pose (yaw, pitch); `go` leaves the screen point in x, y and the depth in z. */
private class Proj(private val cx: Float, private val cy: Float, private val scale: Float, yaw: Float, pitch: Float) {
    private val cyw = cos(yaw)
    private val syw = sin(yaw)
    private val cp = cos(pitch)
    private val sp = sin(pitch)
    var x = 0f
    var y = 0f
    var z = 0f

    fun go(px: Float, py: Float, pz: Float) {
        val x1 = px * cyw + pz * syw
        val z1 = -px * syw + pz * cyw
        val y2 = py * cp - z1 * sp
        val z2 = py * sp + z1 * cp
        val f = CAM / (CAM - z2)
        x = cx + x1 * f * scale
        y = cy + y2 * f * scale
        z = z2
    }
}

private fun smooth(e0: Float, e1: Float, v: Float): Float {
    val k = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return k * k * (3 - 2 * k)
}

/** Half-width and half-depth of the head at height v (-1 = top of the skull, 1 = chin): a skull narrowing to a jaw. */
private fun headW(v: Float) = 0.74f * sqrt(max(0f, 1 - v * v)) * (1 - 0.3f * smooth(0.05f, 1f, v))
private fun headD(v: Float) = 0.86f * sqrt(max(0f, 1 - v * v)) * (1 - 0.1f * smooth(0.2f, 1f, v))

/** Depth of the face's surface at (x, v), slightly in front of it. */
private fun surfaceZ(x: Float, v: Float): Float {
    val w = headW(v)
    if (w <= 0.001f) return 0f
    val r = (x / w).coerceIn(-1f, 1f)
    return headD(v) * cos(asin(r)) + 0.012f
}

/** One horizontal contour of the head; the near half goes to [front], the far half to [back]. */
private fun ring(p: Proj, v: Float, front: Path, back: Path?, frontOnly: Boolean = false) {
    val w = headW(v)
    val d = headD(v)
    val n = 44
    var prevX = 0f; var prevY = 0f; var prevZ = 0f
    for (i in 0..n) {
        val th = i * 2f * PI.toFloat() / n
        p.go(w * sin(th), v, d * cos(th))
        if (i > 0) {
            val z = (p.z + prevZ) / 2f
            val target = if (z > -0.04f) front else back
            if (target != null && !(frontOnly && target !== front)) {
                target.moveTo(prevX, prevY); target.lineTo(p.x, p.y)
            }
        }
        prevX = p.x; prevY = p.y; prevZ = p.z
    }
}

private fun head(p: Proj, front: Path, back: Path) {
    // Contours (latitude)
    var v = -0.9f
    while (v <= 0.95f) { ring(p, v, front, back); v += 0.15f }
    // Meridians
    val m = 14
    for (k in 0 until m) {
        val th = k * 2f * PI.toFloat() / m
        var prevX = 0f; var prevY = 0f; var prevZ = 0f
        for (j in 0..22) {
            val vv = -1f + j * 2f / 22
            p.go(headW(vv) * sin(th), vv, headD(vv) * cos(th))
            if (j > 0) {
                val target = if ((p.z + prevZ) / 2f > -0.04f) front else back
                target.moveTo(prevX, prevY); target.lineTo(p.x, p.y)
            }
            prevX = p.x; prevY = p.y; prevZ = p.z
        }
    }
}

/** Neck and shoulders: elliptical contours widening into the shoulders, plus a few verticals. */
private fun bust(p: Proj, front: Path, back: Path) {
    val levels = floatArrayOf(0.98f, 1.14f, 1.3f, 1.45f, 1.6f, 1.76f, 1.92f, 2.08f)
    val widths = floatArrayOf(0.3f, 0.3f, 0.32f, 0.62f, 1.05f, 1.27f, 1.36f, 1.4f)
    val depths = floatArrayOf(0.28f, 0.28f, 0.3f, 0.4f, 0.52f, 0.58f, 0.6f, 0.6f)
    val n = 40
    for (l in levels.indices) {
        var prevX = 0f; var prevY = 0f; var prevZ = 0f
        for (i in 0..n) {
            val th = i * 2f * PI.toFloat() / n
            p.go(widths[l] * sin(th), levels[l], depths[l] * cos(th))
            if (i > 0) {
                val target = if ((p.z + prevZ) / 2f > -0.04f) front else back
                target.moveTo(prevX, prevY); target.lineTo(p.x, p.y)
            }
            prevX = p.x; prevY = p.y; prevZ = p.z
        }
    }
    for (k in 0 until 10) {
        val th = k * 2f * PI.toFloat() / 10
        for (l in 1 until levels.size) {
            p.go(widths[l - 1] * sin(th), levels[l - 1], depths[l - 1] * cos(th))
            val ax = p.x; val ay = p.y; val az = p.z
            p.go(widths[l] * sin(th), levels[l], depths[l] * cos(th))
            val target = if ((p.z + az) / 2f > -0.04f) front else back
            target.moveTo(ax, ay); target.lineTo(p.x, p.y)
        }
    }
}

/** Brows, nose ridge and the mouth (open by [mouth] head units). */
private fun face(p: Proj, path: Path, blink: Float, mouth: Float) {
    path.reset()
    fun poly(points: List<Pair<Float, Float>>, lift: Float = 0f) {
        points.forEachIndexed { i, (x, v) ->
            p.go(x, v, surfaceZ(x, v) + lift)
            if (p.z < 0f) return
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
    }
    for (side in listOf(-1f, 1f)) {
        poly(List(7) { i -> val x = 0.17f + i * 0.045f; Pair(side * x, -0.2f - 0.035f * sin((i / 6f) * PI.toFloat()) + (1 - blink) * 0.01f) })
    }
    // nose: ridge and base
    poly(List(6) { i -> val v = -0.04f + i * 0.055f; Pair(0f, v) }, lift = 0.04f)
    poly(listOf(Pair(-0.07f, 0.27f), Pair(0f, 0.3f), Pair(0.07f, 0.27f)), lift = 0.05f)
    // mouth: upper and lower lip
    val half = 0.19f
    poly(List(9) { i -> val x = -half + i * half / 4f; Pair(x, 0.5f - mouth / 2f - 0.012f * (1 - (x / half) * (x / half))) })
    poly(List(9) { i -> val x = -half + i * half / 4f; Pair(x, 0.5f + mouth / 2f + 0.02f * (1 - (x / half) * (x / half)) * (0.4f + mouth * 6f)) })
}

/** Glowing almond eyes on the face's surface. */
private fun DrawScope.eyes(p: Proj, blink: Float, glow: Float, tone: Color) {
    for (side in listOf(-1f, 1f)) {
        val ex = side * 0.3f
        val ey = -0.07f
        p.go(ex, ey, surfaceZ(ex, ey))
        if (p.z < 0.05f) continue
        val c = Offset(p.x, p.y)
        val path = Path()
        for (i in 0..16) {
            val a = i / 16f * 2f * PI.toFloat()
            val x = ex + 0.12f * cos(a)
            val v = ey + 0.05f * blink * sin(a) * (1f - 0.35f * cos(a) * cos(a))
            p.go(x, v, surfaceZ(x, v))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        val r = size.minDimension * 0.06f
        drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.55f * glow), Color.Transparent), c, r), r, c)
        drawPath(path, tone.copy(alpha = 0.95f * glow))
        drawPath(path, Hud.White.copy(alpha = 0.9f * glow), style = Stroke(1.2f))
        if (blink > 0.5f) drawCircle(Hud.White.copy(alpha = glow), size.minDimension * 0.008f, c)
    }
}

/** A point on the tilted data ring around the head (thinking). */
private fun orbitPoint(p: Proj, a: Float) {
    val r = 1.32f
    val tiltA = 0.38f
    p.go(r * cos(a), -0.12f + r * sin(a) * sin(tiltA), r * sin(a) * cos(tiltA))
}

private fun orbit(p: Proj, time: Float, front: Path, back: Path) {
    val n = 60
    var prevX = 0f; var prevY = 0f; var prevZ = 0f
    for (i in 0..n) {
        orbitPoint(p, i * 2f * PI.toFloat() / n + time * 0.2f)
        if (i > 0) {
            val target = if ((p.z + prevZ) / 2f > 0f) front else back
            target.moveTo(prevX, prevY); target.lineTo(p.x, p.y)
        }
        prevX = p.x; prevY = p.y; prevZ = p.z
    }
}

/** The projector pad: the reactor seen from the side — thickness, armour ring, turning glow arcs, ticks and the hot core. */
private fun DrawScope.drawPad(c: Offset, rx: Float, ry: Float, time: Float, energy: Float, voice: Float, tone: Color) {
    val thick = ry * 0.32f
    drawOval(Steel, Offset(c.x - rx, c.y - ry + thick), Size(rx * 2, ry * 2))
    drawOval(SteelHi.copy(alpha = 0.6f), Offset(c.x - rx, c.y - ry + thick), Size(rx * 2, ry * 2), style = Stroke(1.2f))
    drawRect(Steel, Offset(c.x - rx, c.y), Size(rx * 2, thick))
    drawOval(Brush.radialGradient(listOf(Color(0xFF0E2236), Color(0xFF050B14)), c, rx), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    drawOval(SteelHi, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(2f))
    // ticks round the rim
    for (i in 0 until 72) {
        val a = i * 5f * PI.toFloat() / 180f + time * 0.1f
        val long = i % 6 == 0
        val k = if (long) 0.9f else 0.94f
        drawLine(
            tone.copy(alpha = if (long) 0.6f else 0.25f),
            Offset(c.x + rx * k * cos(a), c.y + ry * k * sin(a)), Offset(c.x + rx * 0.98f * cos(a), c.y + ry * 0.98f * sin(a)), 1.2f,
        )
    }
    // turning glow arcs (brighter with the voice)
    val glow = 0.5f + 0.5f * energy + 0.4f * voice
    for (q in 0 until 3) {
        val start = q * 120f + time * 40f
        val k = 0.78f
        drawArc(tone.copy(alpha = 0.25f * glow), start, 70f, false, Offset(c.x - rx * k, c.y - ry * k), Size(rx * 2 * k, ry * 2 * k), style = Stroke(ry * 0.32f, cap = StrokeCap.Round))
        drawArc(Color.White.copy(alpha = 0.7f * glow), start, 70f, false, Offset(c.x - rx * k, c.y - ry * k), Size(rx * 2 * k, ry * 2 * k), style = Stroke(1.6f, cap = StrokeCap.Round))
    }
    for (q in 0 until 24) if (q % 4 != 3) {
        val k = 0.56f
        drawArc(tone.copy(alpha = 0.6f), q * 15f - time * 60f, 9f, false, Offset(c.x - rx * k, c.y - ry * k), Size(rx * 2 * k, ry * 2 * k), style = Stroke(ry * 0.12f))
    }
    // the hot core, beaming up
    val pulse = 1f + 0.2f * energy + 0.25f * voice * sin(time * 18f)
    val cr = rx * 0.16f * pulse
    drawOval(Brush.radialGradient(listOf(CoreHot.copy(alpha = 0.6f), CoreRed.copy(alpha = 0.15f), Color.Transparent), c, cr * 2f), Offset(c.x - cr * 2f, c.y - cr * 0.9f), Size(cr * 4f, cr * 1.8f))
    drawOval(CoreRed.copy(alpha = 0.9f), Offset(c.x - cr, c.y - cr * 0.45f), Size(cr * 2, cr * 0.9f), style = Stroke(2f))
    drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFFFE2C2), CoreHot.copy(alpha = 0f)), c, cr * 0.7f), Offset(c.x - cr * 0.7f, c.y - cr * 0.3f), Size(cr * 1.4f, cr * 0.6f))
}
