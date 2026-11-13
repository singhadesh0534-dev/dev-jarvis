package com.adesh.devjarvis.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Shared HUD building blocks: glass panels, the backdrop, headers and the "built by DEV" signature.
 * Spacing follows an 8dp grid (4dp for hairline details).
 */

/** Standard corner radii. */
object HudShapes {
    val Panel = RoundedCornerShape(18.dp)
    val Card = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * Glass panel: dark translucent fill lit slightly from the top, a faint accent tint and a hairline
 * border that is brighter on top — reads as depth without any heavy glow.
 */
fun Modifier.glass(
    accent: Color = Hud.Cyan,
    shape: Shape = HudShapes.Card,
    tint: Float = 1f,
    edge: Float = 1f,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Hud.PanelHi.copy(alpha = 0.78f), Hud.Panel.copy(alpha = 0.62f))), shape)
    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.075f * tint), Color.Transparent)), shape)
    .border(
        1.dp,
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.50f * edge), accent.copy(alpha = 0.12f * edge))),
        shape,
    )

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
 * Screen backdrop: deep radial glow, a faint grid and (when [animated]) one slow scan line.
 * The animation is read only while drawing, so it never recomposes the screen.
 */
@Composable
fun Modifier.hudBackdrop(animated: Boolean = false, glowY: Float = 0.32f): Modifier {
    // Only animated screens run a clock; static ones draw once.
    val scan: State<Float>? = if (animated) {
        rememberInfiniteTransition(label = "backdrop").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
            label = "scan",
        )
    } else {
        null
    }
    return this
        .background(Hud.Abyss)
        .drawBehind {
            val center = Offset(size.width / 2f, size.height * glowY)
            drawRect(
                Brush.radialGradient(
                    listOf(Hud.BackgroundGlow, Hud.Background, Hud.Abyss),
                    center = center,
                    radius = size.maxDimension * 0.75f,
                ),
            )
            val step = 32.dp.toPx()
            val line = Hud.Cyan.copy(alpha = 0.028f)
            var x = (size.width % step) / 2f
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y)); y += step }
            if (scan != null) {
                val sy = size.height * scan.value
                val band = 72.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Hud.Cyan.copy(alpha = 0.045f), Color.Transparent),
                        startY = sy - band, endY = sy + band,
                    ),
                    topLeft = Offset(0f, sy - band),
                    size = Size(size.width, band * 2),
                )
            }
            // Soft vignette so the edges fall away into the dark.
            drawRect(
                Brush.verticalGradient(
                    0f to Hud.Abyss.copy(alpha = 0.55f),
                    0.18f to Color.Transparent,
                    0.82f to Color.Transparent,
                    1f to Hud.Abyss.copy(alpha = 0.65f),
                ),
            )
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
            .background(color.copy(alpha = 0.12f), HudShapes.Pill)
            .border(1.dp, color.copy(alpha = 0.35f), HudShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Header for the inner screens: back, title with a one-line caption, actions on the right,
 * and a fading hairline underneath.
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
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hud.Cyan)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, Hud.Cyan)),
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
        HudDivider()
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
        Box(Modifier.size(size.dp).background(color, HudShapes.Pill))
    }
}
