package com.adesh.devjarvis.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Image ko chhota + JPEG — free tier aur speed dono ke liye (~100–200 KB). */
object ImageUtil {
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
