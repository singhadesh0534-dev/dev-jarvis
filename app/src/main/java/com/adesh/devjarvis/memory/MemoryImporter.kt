package com.adesh.devjarvis.memory

import android.content.Context
import android.net.Uri
import com.adesh.devjarvis.brain.JarvisBrain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ek file (txt / json / zip, jaise Claude ka "memories" export) padh ke usme se Sir ke baare mein pakki baatein
 * JARVIS ki memory mein daalna. File phone se bahar sirf Sir ke apne Gemini / Groq key se jaati hai (facts nikalne ke liye).
 */
@Singleton
class MemoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brain: JarvisBrain,
    private val memory: MemoryRepository,
) {
    sealed interface Outcome {
        data class Done(val added: Int, val truncated: Boolean) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun importFile(uri: Uri, onProgress: (done: Int, total: Int) -> Unit): Outcome {
        val text = withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrNull() }
            ?: return Outcome.Failed("File padh nahi paya.")
        return importText(text, onProgress)
    }

    suspend fun importText(raw: String, onProgress: (done: Int, total: Int) -> Unit): Outcome {
        val flat = MemoryImport.flatten(raw)
        if (flat.length < 20) return Outcome.Failed("File mein padhne layak text nahi mila.")
        val all = MemoryImport.chunks(flat, max = Int.MAX_VALUE)
        val parts = all.take(MemoryImport.MAX_CHUNKS)
        var added = 0
        var answered = 0
        parts.forEachIndexed { i, part ->
            onProgress(i, parts.size)
            val before = memory.all()
            val reply = brain.complete(MemoryImport.systemPrompt(before.map { it.text }), part, json = true)
            val result = MemoryExtraction.parse(reply, max = MemoryImport.MAX_FACTS_PER_CHUNK)
            if (result != null) {
                answered++
                result.facts.forEach { (fact, category) -> memory.add(fact, category) }
                added += (memory.all().size - before.size).coerceAtLeast(0)
            }
            if (i < parts.lastIndex) delay(4_000) // free-tier requests per minute
        }
        onProgress(parts.size, parts.size)
        if (answered == 0) return Outcome.Failed("AI se jawab nahi mila — internet / API key / aaj ka free quota check kijiye.")
        return Outcome.Done(added, truncated = all.size > parts.size)
    }

    private fun read(uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
        val head = input.readLimited(MAX_BYTES)
        if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            // A zip (e.g. memories-000.zip): join the text files inside.
            val out = StringBuilder()
            ZipInputStream(head.inputStream()).use { zip ->
                while (out.length < MAX_BYTES) {
                    val entry = runCatching { zip.nextEntry }.getOrNull() ?: break
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && TEXT_EXTENSIONS.any { name.endsWith(it) }) {
                        out.append(String(zip.readLimited(MAX_BYTES - out.length), Charsets.UTF_8)).append('\n')
                    }
                }
            }
            out.toString()
        } else {
            String(head, Charsets.UTF_8)
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        /** Enough for a memories export or a long notes file; a huge conversations export is cut here. */
        const val MAX_BYTES = 3 * 1024 * 1024
        val TEXT_EXTENSIONS = listOf(".json", ".txt", ".md", ".csv")
    }
}
