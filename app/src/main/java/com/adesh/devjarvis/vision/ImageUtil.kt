package com.adesh.devjarvis.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Image ko chhota + JPEG — free tier aur speed dono ke liye (~100–200 KB). */
object ImageUtil {
    /**
     * A copy of [src] with a yellow box and its number drawn on every rectangle: the vision model can then say
     * "the search icon is number 14" and JARVIS taps number 14. Returns [src] itself when there is nothing to draw.
     */
    fun withMarks(src: Bitmap, marks: List<Pair<Int, android.graphics.Rect>>): Bitmap {
        if (marks.isEmpty()) return src
        val out = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull() ?: return src
        val canvas = android.graphics.Canvas(out)
        val size = max(22f, out.width / 34f)
        val box = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = max(2f, out.width / 400f)
            color = android.graphics.Color.YELLOW
        }
        val fill = android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL; color = android.graphics.Color.YELLOW }
        val text = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = size
            isFakeBoldText = true
            isAntiAlias = true
        }
        marks.take(60).forEach { (id, r) ->
            if (r.width() <= 0 || r.height() <= 0) return@forEach
            canvas.drawRect(r, box)
            val label = id.toString()
            val w = text.measureText(label) + 8f
            val left = r.left.toFloat().coerceIn(0f, max(0f, out.width - w))
            val top = r.top.toFloat().coerceIn(0f, max(0f, out.height - size - 6f))
            canvas.drawRect(left, top, left + w, top + size + 6f, fill)
            canvas.drawText(label, left + 4f, top + size, text)
        }
        return out
    }

    fun toJpeg(src: Bitmap, rotationDegrees: Int = 0, maxSide: Int = 1024, quality: Int = 80): ByteArray {
        val scale = (maxSide.toFloat() / max(src.width, src.height)).coerceAtMost(1f)
        val m = Matrix().apply {
            postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        return ByteArrayOutputStream().use { bos ->
            out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            if (out !== src) out.recycle()
            bos.toByteArray()
        }
    }
}
