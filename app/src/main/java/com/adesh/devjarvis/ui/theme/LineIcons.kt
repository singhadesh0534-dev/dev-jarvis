package com.adesh.devjarvis.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin line icons in the HUD style (the core icon set has no camera, photo or file). */
enum class LineIcon { CAMERA, PHOTO, FILE, SKILLS, CONNECTORS, RESEARCH, MEMORY }

@Composable
fun HudLineIcon(icon: LineIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            LineIcon.CAMERA -> camera(color, stroke, w)
            LineIcon.PHOTO -> photo(color, stroke, w)
            LineIcon.FILE -> file(color, stroke, w)
            LineIcon.SKILLS -> skills(color, stroke, w)
            LineIcon.CONNECTORS -> connectors(color, stroke, w)
            LineIcon.RESEARCH -> research(color, stroke, w)
            LineIcon.MEMORY -> memory(color, stroke, w)
        }
    }
}

private fun DrawScope.camera(c: Color, s: Stroke, w: Float) {
    val body = Path().apply {
        moveTo(w * 0.12f, w * 0.32f)
        lineTo(w * 0.33f, w * 0.32f)
        lineTo(w * 0.40f, w * 0.20f)
        lineTo(w * 0.60f, w * 0.20f)
        lineTo(w * 0.67f, w * 0.32f)
        lineTo(w * 0.88f, w * 0.32f)
        lineTo(w * 0.88f, w * 0.80f)
        lineTo(w * 0.12f, w * 0.80f)
        close()
    }
    drawPath(body, c, style = s)
    drawCircle(c, radius = w * 0.15f, center = Offset(w * 0.5f, w * 0.55f), style = s)
}

private fun DrawScope.photo(c: Color, s: Stroke, w: Float) {
    drawRoundRect(c, Offset(w * 0.12f, w * 0.16f), Size(w * 0.76f, w * 0.68f), CornerRadius(w * 0.08f), style = s)
    drawCircle(c, radius = w * 0.07f, center = Offset(w * 0.34f, w * 0.38f), style = s)
    val hills = Path().apply {
        moveTo(w * 0.14f, w * 0.74f)
        lineTo(w * 0.40f, w * 0.52f)
        lineTo(w * 0.56f, w * 0.66f)
        lineTo(w * 0.68f, w * 0.56f)
        lineTo(w * 0.86f, w * 0.72f)
    }
    drawPath(hills, c, style = s)
}

private fun DrawScope.file(c: Color, s: Stroke, w: Float) {
    val page = Path().apply {
        moveTo(w * 0.22f, w * 0.10f)
        lineTo(w * 0.58f, w * 0.10f)
        lineTo(w * 0.78f, w * 0.30f)
        lineTo(w * 0.78f, w * 0.90f)
        lineTo(w * 0.22f, w * 0.90f)
        close()
    }
    drawPath(page, c, style = s)
    drawPath(Path().apply { moveTo(w * 0.58f, w * 0.10f); lineTo(w * 0.58f, w * 0.30f); lineTo(w * 0.78f, w * 0.30f) }, c, style = s)
    // an arrow up: "give it to JARVIS"
    drawLine(c, Offset(w * 0.5f, w * 0.76f), Offset(w * 0.5f, w * 0.46f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.38f, w * 0.57f), Offset(w * 0.5f, w * 0.45f), s.width, StrokeCap.Round)
    drawLine(c, Offset(w * 0.62f, w * 0.57f), Offset(w * 0.5f, w * 0.45f), s.width, StrokeCap.Round)
}

private fun DrawScope.skills(c: Color, s: Stroke, w: Float) {
    val r = CornerRadius(w * 0.05f)
    val box = Size(w * 0.28f, w * 0.28f)
    drawRoundRect(c, Offset(w * 0.14f, w * 0.14f), box, r, style = s)
    drawRoundRect(c, Offset(w * 0.14f, w * 0.58f), box, r, style = s)
    drawRoundRect(c, Offset(w * 0.58f, w * 0.58f), box, r, style = s)
    // the fourth one is a spark: skills he learns himself
    val cx = w * 0.72f
    val cy = w * 0.28f
    val star = Path().apply {
        moveTo(cx, cy - w * 0.17f)
        lineTo(cx + w * 0.045f, cy - w * 0.045f)
        lineTo(cx + w * 0.17f, cy)
        lineTo(cx + w * 0.045f, cy + w * 0.045f)
        lineTo(cx, cy + w * 0.17f)
        lineTo(cx - w * 0.045f, cy + w * 0.045f)
        lineTo(cx - w * 0.17f, cy)
        lineTo(cx - w * 0.045f, cy - w * 0.045f)
        close()
    }
    drawPath(star, c, style = s)
}

private fun DrawScope.connectors(c: Color, s: Stroke, w: Float) {
    val a = Offset(w * 0.24f, w * 0.30f)
    val b = Offset(w * 0.76f, w * 0.30f)
    val d = Offset(w * 0.50f, w * 0.78f)
    drawLine(c, a, b, s.width * 0.8f, StrokeCap.Round)
    drawLine(c, a, d, s.width * 0.8f, StrokeCap.Round)
    drawLine(c, b, d, s.width * 0.8f, StrokeCap.Round)
    listOf(a, b, d).forEach { p ->
        drawCircle(Hud.Background, radius = w * 0.12f, center = p)
        drawCircle(c, radius = w * 0.12f, center = p, style = s)
    }
}

private fun DrawScope.research(c: Color, s: Stroke, w: Float) {
    drawCircle(c, radius = w * 0.25f, center = Offset(w * 0.43f, w * 0.43f), style = s)
    drawLine(c, Offset(w * 0.62f, w * 0.62f), Offset(w * 0.86f, w * 0.86f), s.width * 1.2f, StrokeCap.Round)
    val pulse = Path().apply {
        moveTo(w * 0.27f, w * 0.47f)
        lineTo(w * 0.36f, w * 0.47f)
        lineTo(w * 0.41f, w * 0.34f)
        lineTo(w * 0.47f, w * 0.54f)
        lineTo(w * 0.51f, w * 0.43f)
        lineTo(w * 0.59f, w * 0.43f)
    }
    drawPath(pulse, c, style = Stroke(width = s.width * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.memory(c: Color, s: Stroke, w: Float) {
    val center = Offset(w * 0.5f, w * 0.5f)
    drawCircle(c, radius = w * 0.07f, center = center)
    drawArc(c, 200f, 280f, false, Offset(w * 0.28f, w * 0.28f), Size(w * 0.44f, w * 0.44f), style = s)
    drawArc(c, 20f, 280f, false, Offset(w * 0.12f, w * 0.12f), Size(w * 0.76f, w * 0.76f), style = s)
}
