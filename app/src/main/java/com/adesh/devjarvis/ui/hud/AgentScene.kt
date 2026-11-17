package com.adesh.devjarvis.ui.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adesh.devjarvis.R
import com.adesh.devjarvis.core.AgentMood
import com.adesh.devjarvis.core.AgentMoods
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.HudTag
import com.adesh.devjarvis.ui.theme.Orbitron
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Where things are in the picture (jarvis_agent.webp, 1134 x 1000 px): the robot at his desk in profile, the
 * holographic head with its brain on the right, the laptop he types on.
 */
private val TEMPLE = Offset(309f, 112f)
private val EYE = Offset(469f, 228f)
private val MOUTH = Offset(472f, 330f)
private val FACE = Offset(462f, 262f)
private val SHOULDER = Offset(94f, 442f)
private val CHEST = Offset(374f, 593f)
private val BRAIN = Offset(800f, 128f)
private val BRAIN_RING = Offset(900f, 165f)
private val BRAIN_RING2 = Offset(1040f, 125f)
private val KEYS = Offset(735f, 838f)
private val CUP_TOP = Offset(1082f, 812f)
private val LAPTOP = listOf(Offset(1000f, 505f), Offset(1100f, 480f), Offset(965f, 935f), Offset(868f, 848f)) // A B C D

private fun moodColor(mood: AgentMood, accent: Color): Color = when (mood) {
    AgentMood.NEUTRAL -> accent
    AgentMood.HAPPY -> Color(0xFF7DFFD8)
    AgentMood.SAD -> Color(0xFF4A7BFF)
    AgentMood.ANGRY -> Color(0xFFFF3B3B)
    AgentMood.SURPRISED -> Color(0xFFFFF2A8)
    AgentMood.LOVE -> Color(0xFFFF6FB5)
}

/**
 * JARVIS as a character: Sir's picture of the robot at his desk, brought to life. Its lights are live —
 * listening: the eye and the ear ring brighten and sound waves gather at the ear with Sir's voice;
 * thinking: the holographic brain lights up and is scanned, the network flickers, code scrolls on the laptop and
 * the keys spark under its fingers; speaking: waves go out from the mouth with JARVIS's voice.
 * Its mood follows its own last reply (happy, sad, angry, surprised, love): the eye and rings take the mood's colour,
 * the light on the face changes, hearts / sparkles / a red glow / a flash appear. It breathes, blinks and moves a
 * little with the phone's tilt (3D parallax). All motion is on the one capped hologram clock, read only while drawing.
 */
@Composable
fun AgentScene(
    state: JarvisState,
    micLevel: Float,
    speechLevel: Float,
    mood: AgentMood,
    accent: Color,
    tilt: State<Offset>,
    clock: State<Float>,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** A real-movement clip of the robot for this moment (see AgentClips), played over the picture. */
    clip: AgentClips.Source? = null,
    clipLoop: Boolean = true,
    onClipEnd: () -> Unit = {},
) {
    val image = ImageBitmap.imageResource(R.drawable.jarvis_agent)
    val tone by animateColorAsState(moodColor(mood, accent), tween(700), label = "moodTone")
    val moodAmt by animateFloatAsState(if (mood == AgentMood.NEUTRAL) 0f else 1f, tween(700), label = "moodAmt")
    val listening by animateFloatAsState(if (state == JarvisState.LISTENING) 1f else 0f, tween(350), label = "listen")
    val thinking by animateFloatAsState(if (state == JarvisState.THINKING) 1f else 0f, tween(350), label = "think")
    val speaking by animateFloatAsState(if (state == JarvisState.SPEAKING) 1f else 0f, tween(350), label = "speak")
    val mic = rememberUpdatedState(micLevel)
    val speech = rememberUpdatedState(speechLevel)
    val moodNow = rememberUpdatedState(mood)
    // A flash when surprise starts.
    val surpriseAt = remember(mood) { floatArrayOf(-1f) }
    val nodes = remember {
        val rnd = Random(21)
        List(34) { floatArrayOf(640f + rnd.nextFloat() * 360f, 20f + rnd.nextFloat() * 460f, rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 1.8f) }
    }
    val codeRows = remember { val rnd = Random(4); List(22) { floatArrayOf(0.08f + rnd.nextFloat() * 0.2f, 0.35f + rnd.nextFloat() * 0.55f) } }

    Box(modifier.clip(HudShapes.Panel)) {
        Canvas(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        ) {
            val time = clock.value
            val t = tilt.value
            val m = moodNow.value
            if (m == AgentMood.SURPRISED && surpriseAt[0] < 0f) surpriseAt[0] = time

            // The picture: covers the panel, keeps the head and the hologram in view, breathes, shifts with the tilt.
            val iw = image.width.toFloat()
            val ih = image.height.toFloat()
            val cover = max(size.width / iw, size.height / ih) * 1.08f
            val sc = cover * (1f + 0.006f * sin(time * 1.3f))
            val wide = size.height / size.width < 0.42f
            val fx = 600f
            val fy = if (wide) 250f else 360f
            val shake = if (m == AgentMood.ANGRY) sin(time * 47f) * 1.2f * density * moodAmt else 0f
            val ox = (size.width / 2f - fx * sc + t.x * 7f * density + shake).coerceIn(size.width - iw * sc, 0f)
            val oy = (size.height / 2f - fy * sc + t.y * 5f * density).coerceIn(size.height - ih * sc, 0f)
            drawImage(
                image,
                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                dstSize = IntSize((iw * sc).roundToInt(), (ih * sc).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            fun at(p: Offset) = Offset(ox + p.x * sc, oy + p.y * sc)
            val u = sc // one picture pixel on screen

            // Mood light over the whole scene (sad = cold and dark, angry = red, love = pink, happy = bright).
            when (m) {
                AgentMood.SAD -> drawRect(Color(0xFF00102E).copy(alpha = 0.32f * moodAmt))
                AgentMood.ANGRY -> drawRect(Color(0xFFFF1A1A).copy(alpha = 0.10f * moodAmt), blendMode = BlendMode.Plus)
                AgentMood.LOVE -> drawRect(Color(0xFFFF4FA0).copy(alpha = 0.08f * moodAmt), blendMode = BlendMode.Plus)
                AgentMood.HAPPY -> glow(at(FACE), 170f * u, Color(0xFFFFF4D6), 0.16f * moodAmt)
                else -> Unit
            }
            if (m == AgentMood.SURPRISED && surpriseAt[0] >= 0f) {
                val k = (1f - (time - surpriseAt[0]) / 0.6f).coerceIn(0f, 1f)
                if (k > 0f) drawRect(Color.White.copy(alpha = 0.28f * k), blendMode = BlendMode.Plus)
            }

            // Eye: mood colour, brighter while listening / surprised, dim when sad, blinks.
            val blink = (time % 4.6f).let { ph -> if (ph < 0.18f) abs(ph - 0.09f) / 0.09f else 1f }
            val eyeBase = when (m) {
                AgentMood.SAD -> 0.35f
                AgentMood.SURPRISED -> 1f
                else -> 0.6f + 0.4f * listening
            }
            val eye = eyeBase * blink * (0.85f + 0.15f * sin(time * 3f))
            val eyeR = (if (m == AgentMood.SURPRISED) 34f else 22f) * u
            glow(at(EYE), eyeR, tone, 0.9f * eye)
            drawCircle(Color.White.copy(alpha = 0.85f * eye), 4.5f * u, at(EYE), blendMode = BlendMode.Plus)
            if (m == AgentMood.HAPPY || m == AgentMood.LOVE) twinkle(at(EYE + Offset(24f, -22f)), 10f * u * moodAmt, tone, time)

            // Ear / temple ring: the "ear" — pulses with Sir's voice, with JARVIS's voice, or calmly.
            val ear = when {
                listening > 0.5f -> 0.55f + 0.45f * mic.value
                speaking > 0.5f -> 0.45f + 0.5f * speech.value
                thinking > 0.5f -> 0.5f + 0.4f * (0.5f + 0.5f * sin(time * 8f))
                else -> 0.35f + 0.15f * sin(time * 1.4f)
            }
            glow(at(TEMPLE), 70f * u, tone, 0.55f * ear)
            if (listening > 0.01f) {
                for (k in 0 until 3) {
                    val ph = (time * 0.8f + k / 3f) % 1f
                    drawCircle(
                        tone.copy(alpha = (1f - ph) * listening * (0.3f + 0.6f * mic.value)),
                        (60f + 150f * ph) * u, at(TEMPLE), style = Stroke(2.2f * density),
                    )
                }
            }

            // Shoulder and chest rings breathe.
            glow(at(SHOULDER), 62f * u, tone, 0.35f + 0.15f * sin(time * 1.1f) + 0.2f * thinking)
            glow(at(CHEST), 44f * u, tone, 0.4f + 0.2f * sin(time * 1.1f + 1f) + 0.3f * speaking * speech.value)

            // Speaking: waves go out from the mouth towards the hologram, with JARVIS's voice.
            if (speaking > 0.01f) {
                val c = at(MOUTH)
                for (k in 0 until 3) {
                    val ph = (time * 1.1f + k / 3f) % 1f
                    val r = (24f + 120f * ph) * u
                    drawArc(
                        tone.copy(alpha = (1f - ph) * speaking * (0.35f + 0.65f * speech.value)), -35f, 70f, false,
                        Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(2.4f * density, cap = StrokeCap.Round),
                    )
                }
            }

            // The holographic head: brain glows (much more while thinking), a scan sweeps it, the network twinkles.
            val brain = 0.25f + 0.55f * thinking + 0.15f * speaking + 0.15f * sin(time * 1.7f)
            glow(at(BRAIN), 210f * u, tone, 0.25f * brain)
            glow(at(BRAIN_RING), 40f * u, Color.White, 0.5f * brain)
            glow(at(BRAIN_RING2), 46f * u, tone, 0.3f + 0.3f * thinking)
            if (thinking > 0.01f) {
                val x = 640f + ((time * 0.55f) % 1f) * 360f
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f * thinking), Color.Transparent), at(Offset(x, 10f)).y, at(Offset(x, 290f)).y),
                    at(Offset(x, 10f)), at(Offset(x, 290f)), 2.5f * density, blendMode = BlendMode.Plus,
                )
            }
            val speed = 1f + 2.5f * thinking
            nodes.forEachIndexed { i, n ->
                val tw = 0.5f + 0.5f * sin(time * n[3] * speed + n[2])
                val p = at(Offset(n[0], n[1]))
                drawCircle(Color.White.copy(alpha = (0.25f + 0.6f * brain) * tw), (2.2f + 1.5f * tw) * u, p, blendMode = BlendMode.Plus)
                if (thinking > 0.3f && i % 3 == 0) {
                    val q = nodes[(i + 7) % nodes.size]
                    drawLine(tone.copy(alpha = 0.35f * thinking * tw), p, at(Offset(q[0], q[1])), 1f, blendMode = BlendMode.Plus)
                }
            }

            // The laptop: its screen glows and code scrolls on it (fast while working).
            val (a, b, c2, d) = LAPTOP.map { at(it) }
            val screen = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c2.x, c2.y); lineTo(d.x, d.y); close() }
            val busy = 0.25f + 0.75f * thinking + 0.3f * speaking
            clipPath(screen) {
                drawRect(tone.copy(alpha = 0.08f + 0.1f * busy), blendMode = BlendMode.Plus)
                val scroll = (time * (0.05f + 0.25f * busy)) % 1f
                codeRows.forEachIndexed { i, r ->
                    val s = ((i / codeRows.size.toFloat()) + scroll) % 1f
                    val left = lerpO(a, d, s)
                    val right = lerpO(b, c2, s)
                    drawLine(
                        Color.White.copy(alpha = 0.25f + 0.45f * busy), lerpO(left, right, r[0]), lerpO(left, right, r[1]),
                        2.2f * u, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
                    )
                }
            }
            // Keys spark under its fingers while it works.
            if (thinking > 0.01f || speaking > 0.01f) {
                for (k in 0 until 5) {
                    val ph = (time * 3.1f + k * 0.37f) % 1f
                    if (ph < 0.35f) {
                        val p = at(KEYS + Offset((k - 2) * 26f, (k % 2) * 10f))
                        glow(p, 16f * u, tone, (0.35f - ph) * 2.2f * max(thinking, speaking))
                    }
                }
            }

            // Steam from the coffee — it's been working a while.
            for (k in 0 until 3) {
                val ph = (time * 0.25f + k / 3f) % 1f
                val p = at(CUP_TOP + Offset(sin(time * 1.3f + k) * 10f, -ph * 90f))
                drawCircle(Color.White.copy(alpha = 0.07f * (1f - ph)), (10f + 14f * ph) * u, p)
            }

            // Love: hearts float up from the head.
            if (m == AgentMood.LOVE && moodAmt > 0.01f) {
                for (k in 0 until 4) {
                    val ph = (time * 0.35f + k / 4f) % 1f
                    val p = at(Offset(520f + sin(time + k * 1.7f) * 40f + k * 30f, 200f - ph * 200f))
                    heart(p, (14f + 6f * (k % 3)) * u, Color(0xFFFF6FB5).copy(alpha = (1f - ph) * 0.9f * moodAmt))
                }
            }

            // Hologram texture over the scene: faint scan lines and a slow sweep.
            val step = 3.5f * density
            var y = 0f
            while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val sy = size.height * ((time / 6f) % 1f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, tone.copy(alpha = 0.07f), Color.Transparent), sy - 30f * density, sy + 30f * density),
                Offset(0f, sy - 30f * density), Size(size.width, 60f * density), blendMode = BlendMode.Plus,
            )
            // Edges fade into the HUD.
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to Hud.Abyss.copy(alpha = 0.55f)))
        }

        // The robot really moving: its clip for this moment, over the picture (which shows until the first frame).
        if (clip != null) {
            AgentVideo(clip, clipLoop, onClipEnd, Modifier.fillMaxSize(), focusX = 0.53f, focusY = 0.32f)
            Canvas(Modifier.fillMaxSize()) {
                val time = clock.value
                when (moodNow.value) {
                    AgentMood.SAD -> drawRect(Color(0xFF00102E).copy(alpha = 0.22f * moodAmt))
                    AgentMood.ANGRY -> drawRect(Color(0xFFFF1A1A).copy(alpha = 0.08f * moodAmt), blendMode = BlendMode.Plus)
                    AgentMood.LOVE -> drawRect(Color(0xFFFF4FA0).copy(alpha = 0.07f * moodAmt), blendMode = BlendMode.Plus)
                    else -> Unit
                }
                val step = 3.5f * density
                var y = 0f
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                val sy = size.height * ((time / 6f) % 1f)
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, tone.copy(alpha = 0.06f), Color.Transparent), sy - 30f * density, sy + 30f * density),
                    Offset(0f, sy - 30f * density), Size(size.width, 60f * density), blendMode = BlendMode.Plus,
                )
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to Hud.Abyss.copy(alpha = 0.55f)))
            }
        }

        // Labels: who it is, and what it's doing / feeling.
        Row(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "J.A.R.V.I.S · AI CORE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Orbitron, fontSize = 8.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold),
                color = Hud.Ice.copy(alpha = 0.9f),
            )
        }
        val chip = when {
            mood != AgentMood.NEUTRAL -> AgentMoods.label(mood)
            state == JarvisState.LISTENING -> "SUN RAHA"
            state == JarvisState.THINKING -> "KAAM CHALU"
            state == JarvisState.SPEAKING -> "BOL RAHA"
            else -> "ONLINE"
        }
        HudTag(chip, tone, Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 8.dp))
    }
}

private fun lerpO(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

/** A soft additive light. */
private fun DrawScope.glow(c: Offset, r: Float, color: Color, a: Float) {
    if (a <= 0.01f || r <= 0f) return
    val k = a.coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = k), color.copy(alpha = 0.3f * k), Color.Transparent), c, r),
        r, c, blendMode = BlendMode.Plus,
    )
}

/** A four-point sparkle. */
private fun DrawScope.twinkle(c: Offset, r: Float, color: Color, time: Float) {
    if (r <= 0.5f) return
    val k = 0.6f + 0.4f * sin(time * 6f)
    val w = r * 0.18f
    drawLine(lerp(color, Color.White, 0.6f).copy(alpha = k), Offset(c.x - r * k, c.y), Offset(c.x + r * k, c.y), w, StrokeCap.Round, blendMode = BlendMode.Plus)
    drawLine(lerp(color, Color.White, 0.6f).copy(alpha = k), Offset(c.x, c.y - r * k), Offset(c.x, c.y + r * k), w, StrokeCap.Round, blendMode = BlendMode.Plus)
}

/** A small heart. */
private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.35f)
        cubicTo(c.x - s, c.y - s * 0.2f, c.x - s * 0.45f, c.y - s * 0.95f, c.x, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.45f, c.y - s * 0.95f, c.x + s, c.y - s * 0.2f, c.x, c.y + s * 0.35f)
        close()
    }
    drawPath(p, color)
}
