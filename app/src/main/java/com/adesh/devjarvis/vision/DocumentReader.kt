package com.adesh.devjarvis.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.adesh.devjarvis.brain.JarvisBrain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Sir ki chuni hui photo ya PDF (pehle 2 panne) ko dekh ke samjhana. File sirf Gemini vision ko jaati hai. */
@Singleton
class DocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brain: JarvisBrain,
) {
    /** Spoken explanation, or null when the file could not be opened or the vision model did not answer. */
    suspend fun explain(uri: Uri): String? {
        val jpeg = withContext(Dispatchers.IO) { runCatching { toJpeg(uri) }.getOrNull() } ?: return null
        return brain.see(jpeg, QUESTION)
    }

    private fun toJpeg(uri: Uri): ByteArray? {
        val type = context.contentResolver.getType(uri).orEmpty()
        val bitmap = if (type.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) pdfPages(uri) else image(uri)
        return bitmap?.let { b -> ImageUtil.toJpeg(b, maxSide = 1600, quality = 80).also { b.recycle() } }
    }

    private fun image(uri: Uri): Bitmap? = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 2 })
    }

    /** First two pages, one under the other, on white. */
    private fun pdfPages(uri: Uri): Bitmap? = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { pdf ->
            val pages = (0 until minOf(pdf.pageCount, 2)).map { index ->
                pdf.openPage(index).use { page ->
                    val width = 1000
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(200, 2200)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            if (pages.isEmpty()) return@use null
            val out = Bitmap.createBitmap(1000, pages.sumOf { it.height }, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            var y = 0f
            pages.forEach { p -> canvas.drawBitmap(p, 0f, y, null); y += p.height; p.recycle() }
            out
        }
    }

    private companion object {
        const val QUESTION = "Sir ne ye photo / document diya hai. Ise dhyan se padho aur dekho, phir Hinglish mein 3-5 chhote " +
            "vaakyon mein batao: ye kya hai, sabse zaroori baatein (naam, tareekh, rakam jo saaf dikhe), aur Sir ko iske baare mein " +
            "kya karna chahiye. Jo saaf na dikhe use andaaze se mat bharo. Password, OTP, card ya account number mat padho."
    }
}
