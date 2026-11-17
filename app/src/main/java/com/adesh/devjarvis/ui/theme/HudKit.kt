package com.adesh.devjarvis.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Shared HUD building blocks: glass panels, the backdrop, headers and the "built by DEV" signature.
 * Spacing follows an 8dp grid (4dp for hairline details).
 */

/**
 * Standard shapes. Panels, cards and buttons have sci-fi cut corners (top-left and bottom-right), chips and tags have
 * hexagon ends; [Pill] stays round for dots and thin bars.
 */
object HudShapes {
    val Panel = CutCornerShape(topStart = 16.dp, topEnd = 3.dp, bottomEnd = 16.dp, bottomStart = 3.dp)
    val Card = CutCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomEnd = 12.dp, bottomStart = 2.dp)
    val Chip = CutCornerShape(topStart = 9.dp, topEnd = 2.dp, bottomEnd = 9.dp, bottomStart = 2.dp)
    val Pill = RoundedCornerShape(50)
    val Hex = CutCornerShape(50)
}

/**
 * Holo glass panel: dark translucent fill lit from the top, a faint accent tint, an edge that burns bright at the two
 * cut corners and fades along the sides, and a small notch on the top edge — the reference HUD dashboards' panel.
 */
fun Modifier.glass(
    accent: Color = Hud.Cyan,
    shape: Shape = HudShapes.Card,
    tint: Float = 1f,
    edge: Float = 1f,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Hud.PanelHi.copy(alpha = 0.80f), Hud.Panel.copy(alpha = 0.66f))), shape)
    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.085f * tint), Color.Transparent)), shape)
    .border(
        1.dp,
        Brush.linearGradient(
            0f to accent.copy(alpha = 0.95f * edge),
            0.2f to accent.copy(alpha = 0.26f * edge),
            0.8f to accent.copy(alpha = 0.14f * edge),
            1f to accent.copy(alpha = 0.8f * edge),
        ),
        shape,
    )
    .drawBehind {
        if (size.width < 140.dp.toPx() || size.height < 36.dp.toPx()) return@drawBehind
        val bar = 22.dp.toPx()
        val x = size.width - 18.dp.toPx() - bar
        drawRect(accent.copy(alpha = 0.75f * edge), Offset(x, 0f), Size(bar, 2.dp.toPx()))
        drawRect(accent.copy(alpha = 0.4f * edge), Offset(x - 8.dp.toPx(), 0f), Size(4.dp.toPx(), 2.dp.toPx()))
    }

/** Short bright corner brackets — the classic HUD frame. Use on hero panels only. */
fun Modifier.cornerTicks(color: Color, length: Float = 12f, inset: Float = 0f): Modifier = drawBehind {
    val c = length.dp.toPx()
    val w = 1.5.dp.toPx()
    val i = inset.dp.toPx()
    listOf(
        Offset(i, i) to Offset(1f, 1f),
        Offset(size.width - i, i) to Offset(-1f, 1f),
        Offset(i, size.height - i) to Offset(1f, -1f),
        Offset(size.width - i, size.height - i) to Offset(-1f, -1f),
    ).forEach { (p, d) ->
        drawLine(color, p, Offset(p.x + d.x * c, p.y), strokeWidth = w)
        drawLine(color, p, Offset(p.x, p.y + d.y * c), strokeWidth = w)
    }
}

/**
 * Screen backdrop, live and in 3D: deep-blue blueprint light, a drifting grid with major lines, rotating technical
 * rings, floating dust and a slow scan line. Each layer sits at its own depth and shifts with the phone's tilt.
 * Everything is read only while drawing, and the screen's content sits in its own layer, so the moving backdrop
 * never recomposes or redraws the content. [animated] = false draws one still frame.
 */
@Composable
fun Modifier.hudBackdrop(animated: Boolean = true, glowY: Float = 0.32f): Modifier {
    val clock: State<Float>? = if (animated) rememberHoloClock() else null
    val tilt: State<Offset>? = if (animated) rememberTilt() else null
    return this
        .background(Hud.Abyss)
        .drawBehind { blueprint(clock?.value ?: 0f, tilt?.value ?: Offset.Zero, glowY) }
        .graphicsLayer()
}

private fun DrawScope.blueprint(time: Float, t: Offset, glowY: Float) {
    val w = size.width
    val h = size.height
    val glow = Offset(w / 2f, h * glowY)
    drawRect(Brush.radialGradient(listOf(Color(0xFF0C2A45), Color(0xFF061322), Hud.Abyss), glow, size.maxDimension * 0.8f))
    // Grid: the deepest layer, drifting slowly upward.
    val step = 28.dp.toPx()
    translate(-t.x * 5.dp.toPx(), -t.y * 5.dp.toPx()) {
        val drift = (time * 4.dp.toPx()) % (step * 4)
        var i = 0
        var x = -step
        while (x < w + step) {
            drawLine(Hud.Cyan.copy(alpha = if (i % 4 == 0) 0.075f else 0.032f), Offset(x, -step), Offset(x, h + step), 1f)
            x += step; i++
        }
        i = 0
        var y = -drift
        while (y < h + step) {
            drawLine(Hud.Cyan.copy(alpha = if (i % 4 == 0) 0.075f else 0.032f), Offset(-step, y), Offset(w + step, y), 1f)
            y += step; i++
        }
    }
    // Technical rings: one large cluster (top right on inner screens, around the glow on the home screens) and a small
    // one bottom left, turning slowly in opposite directions.
    translate(-t.x * 11.dp.toPx(), -t.y * 11.dp.toPx()) {
        val big = if (glowY < 0.15f) Offset(w * 0.9f, h * 0.1f) else glow
        rings(big, w * 0.44f, time, 1f)
        rings(Offset(w * 0.04f, h * 0.88f), w * 0.3f, time, -1.4f)
    }
    // Dust: the nearest layer.
    translate(-t.x * 20.dp.toPx(), -t.y * 20.dp.toPx()) {
        for (k in 0 until 36) {
            val px = ((k * 0.618f) + (k * 0.137f) % 1f) % 1f
            val py = (k * 0.381f + time * (0.006f + (k % 5) * 0.003f)) % 1f
            val a = 0.12f + 0.38f * (0.5f + 0.5f * sin(time * 1.3f + k * 1.7f))
            drawCircle(Hud.Ice.copy(alpha = a), (0.7f + (k % 3) * 0.45f).dp.toPx(), Offset(px * w, (1f - py) * h))
        }
    }
    // One slow scan line.
    val sy = h * ((time / 9f) % 1f)
    val band = 72.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Hud.Cyan.copy(alpha = 0.05f), Color.Transparent), startY = sy - band, endY = sy + band),
        topLeft = Offset(0f, sy - band),
        size = Size(w, band * 2),
    )
    // Soft vignette so the edges fall away into the dark.
    drawRect(
        Brush.verticalGradient(
            0f to Hud.Abyss.copy(alpha = 0.5f),
            0.16f to Color.Transparent,
            0.84f to Color.Transparent,
            1f to Hud.Abyss.copy(alpha = 0.6f),
        ),
    )
}

/** A blueprint ring set: two circles, a rotating tick dial and three counter-rotating arc segments. */
private fun DrawScope.rings(c: Offset, r: Float, time: Float, speed: Float) {
    val line = Hud.Cyan
    drawCircle(line.copy(alpha = 0.07f), r, c, style = Stroke(1f))
    drawCircle(line.copy(alpha = 0.05f), r * 0.72f, c, style = Stroke(1f))
    rotate(time * 4f * speed, c) {
        val rr = r * 1.06f
        for (i in 0 until 90) {
            val ang = i * 4.0 * PI / 180
            val len = if (i % 9 == 0) 12.dp.toPx() else 4.dp.toPx()
            drawLine(
                line.copy(alpha = if (i % 9 == 0) 0.16f else 0.09f),
                Offset(c.x + rr * cos(ang).toFloat(), c.y + rr * sin(ang).toFloat()),
                Offset(c.x + (rr + len) * cos(ang).toFloat(), c.y + (rr + len) * sin(ang).toFloat()),
                1f,
            )
        }
    }
    rotate(-time * 7f * speed, c) {
        val d = r * 0.86f
        for (k in 0 until 3) {
            drawArc(
                line.copy(alpha = 0.13f), k * 120f, 38f, false,
                Offset(c.x - d, c.y - d), Size(d * 2, d * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** A 1dp line that fades out at both ends. */
@Composable
fun HudDivider(modifier: Modifier = Modifier, color: Color = Hud.Cyan) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.35f), Color.Transparent))),
    )
}

/** "DEV JARVIS" wordmark: Orbitron, ice-to-cyan gradient, a whisper of glow. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: Int = 17) {
    Text(
        "DEV JARVIS",
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = fontSize.sp,
            letterSpacing = (fontSize * 0.26f).sp,
            brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, Hud.Cyan)),
            shadow = Shadow(color = Hud.Cyan.copy(alpha = 0.45f), blurRadius = 14f),
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

/** The quiet signature: hairline, small caps "built by", the name a touch brighter. No glow. */
@Composable
fun BuiltByDev(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(16.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Hud.Ice.copy(alpha = 0.45f)))),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "BUILT BY",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Normal),
            color = Hud.Dim,
            maxLines = 1,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "DEV",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
                brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, Hud.Cyan)),
            ),
            maxLines = 1,
        )
    }
}

/** Small uppercase tag, e.g. "CONFIRM", "UPDATE". */
@Composable
fun HudTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.8.sp),
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), HudShapes.Hex)
            .border(1.dp, color.copy(alpha = 0.45f), HudShapes.Hex)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * Header for the inner screens: back in a hex frame, a glowing title with a one-line caption, actions on the right,
 * and a technical rule underneath (a bright segment under the title and a run of ticks on the right).
 */
@Composable
fun ScreenHeader(
    title: String,
    caption: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(HudShapes.Hex)
                    .background(Hud.Cyan.copy(alpha = 0.08f))
                    .border(1.dp, Hud.Cyan.copy(alpha = 0.55f), HudShapes.Hex)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hud.Cyan, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, Hud.Cyan)),
                        shadow = Shadow(color = Hud.Cyan.copy(alpha = 0.55f), blurRadius = 14f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Hud.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
        HudRule()
    }
}

/** A technical rule: a hairline that fades to the right, a bright segment on the left and a run of ticks at the end. */
@Composable
fun HudRule(modifier: Modifier = Modifier, color: Color = Hud.Cyan) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2f
        drawLine(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f), Color.Transparent)),
            Offset(0f, y), Offset(size.width, y), 1f,
        )
        drawLine(color.copy(alpha = 0.9f), Offset(16.dp.toPx(), y), Offset(96.dp.toPx(), y), 2.dp.toPx())
        drawLine(color.copy(alpha = 0.5f), Offset(102.dp.toPx(), y), Offset(108.dp.toPx(), y), 2.dp.toPx())
        for (k in 0 until 6) {
            val x = size.width - 16.dp.toPx() - k * 6.dp.toPx()
            drawLine(color.copy(alpha = 0.45f - k * 0.06f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
    }
}

/** A coloured status dot with a soft halo. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Int = 6) {
    Box(
        modifier
            .size((size * 2).dp)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size.dp).background(color, CircleShape))
    }
}
