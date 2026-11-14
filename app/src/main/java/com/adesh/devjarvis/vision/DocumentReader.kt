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

/**
 * Sir ki di hui file: photo, camera shot, PDF (pehle 2 panne), ya text / Word / CSV. Use samjhao, ya Sir ka sawaal
 * uske baare mein jawab do. Aakhri file yaad rehti hai, taaki "isme total kitna hai?" jaisa agla sawaal bhi chal jaaye.
 * File sirf Gemini ko jaati hai, kahin save nahi hoti.
 */
@Singleton
class DocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brain: JarvisBrain,
) {
    /** The last file Sir gave (null after a restart). */
    @Volatile var last: Uri? = null
        private set
    @Volatile var lastName: String = ""
        private set

    /** Spoken explanation, or null when the file could not be opened or the model did not answer. */
    suspend fun explain(uri: Uri): String? = ask(uri, "")

    /** Sir's question about this file (blank = explain it). */
    suspend fun ask(uri: Uri, question: String): String? =
        kotlinx.coroutines.withTimeoutOrNull(90_000) { askNow(uri, question) }

    private suspend fun askNow(uri: Uri, question: String): String? {
        last = uri
        lastName = displayName(uri)
        val q = question.trim()
        textOf(uri)?.let { text ->
            val user = "File: $lastName\n-----\n${text.take(MAX_TEXT)}\n-----\n" +
                (if (q.isEmpty()) TEXT_QUESTION else "Sir ka sawaal: $q\nSirf file se jawab do, Hinglish mein, chhota aur saaf.")
            return brain.complete(TEXT_SYSTEM, user, json = false)
        }
        val jpeg = withContext(Dispatchers.IO) { runCatching { toJpeg(uri) }.getOrNull() }
        com.adesh.devjarvis.core.SessionTrace.add("FILE image ${jpeg?.size?.div(1024) ?: -1} KB")
        if (jpeg == null) return null
        return brain.see(jpeg, if (q.isEmpty()) QUESTION else "$q\n\n$RULES")
    }

    /** A follow-up about the last file. Null when there is none. */
    suspend fun askLast(question: String): String? = last?.let { ask(it, question) }

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    /** Plain text of a text / CSV / JSON / Word file; null for photos and PDFs. */
    private suspend fun textOf(uri: Uri): String? = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).lowercase()
        runCatching {
            when {
                type.startsWith("text/") || type.contains("json") || TEXT_EXT.any { name.endsWith(it) } ->
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(MAX_BYTES).toByteArray().toString(Charsets.UTF_8) }
                type.contains("wordprocessingml") || name.endsWith(".docx") -> docxText(uri)
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** The words of a .docx (it is a zip; the text sits in word/document.xml). */
    private fun docxText(uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
        java.util.zip.ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return@use xml.replace("</w:p>", "\n").replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                }
                entry = zip.nextEntry
            }
            null
        }
    }

    private fun toJpeg(uri: Uri): ByteArray? {
        val type = context.contentResolver.getType(uri).orEmpty()
        val bitmap = if (type.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")) pdfPages(uri) else image(uri)
        return bitmap?.let { b -> ImageUtil.toJpeg(b, maxSide = 1600, quality = 80).also { b.recycle() } }
    }

    /**
     * Phone cameras take 50-200 MP photos: read the size first and decode only as big as needed (~2000 px), so it is
     * fast and never runs out of memory; then turn it upright from the camera's EXIF note.
     */
    private fun image(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= 2000) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bmp
        val upright = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(degrees) }, true)
        if (upright !== bmp) bmp.recycle()
        return upright
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
        const val MAX_TEXT = 24_000
        const val MAX_BYTES = 200_000
        val TEXT_EXT = listOf(".txt", ".csv", ".md", ".json", ".log", ".xml", ".html", ".kt", ".java", ".py", ".js")
        const val RULES = "Jawab Hinglish mein, chhota aur saaf. Jo saaf na dikhe use andaaze se mat bharo. " +
            "Password, OTP, card ya account number mat padho."
        const val TEXT_SYSTEM = "You are JARVIS, Sir's (Adesh Singh's) assistant. Read the file and answer in Hinglish, short and " +
            "clear, from the file only. Never read out passwords, OTPs, card or account numbers."
        const val TEXT_QUESTION = "Ye file kya hai, 3-5 chhote vaakyon mein: sabse zaroori baatein (naam, tareekh, rakam), " +
            "aur Sir ko iske baare mein kya karna chahiye."
        const val QUESTION = "Sir ne ye photo / document diya hai. Ise dhyan se padho aur dekho, phir Hinglish mein 3-5 chhote " +
            "vaakyon mein batao: ye kya hai, sabse zaroori baatein (naam, tareekh, rakam jo saaf dikhe), aur Sir ko iske baare mein " +
            "kya karna chahiye. Jo saaf na dikhe use andaaze se mat bharo. Password, OTP, card ya account number mat padho."
    }
}
