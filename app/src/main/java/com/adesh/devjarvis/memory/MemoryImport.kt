package com.adesh.devjarvis.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bahar se yaaddasht laana (jaise Claude ka data export): file ka text saaf karna, tukdon mein baantna aur
 * LLM se pakki baatein nikalwane ka prompt. Pure, unit-tested.
 */
object MemoryImport {
    const val CHUNK_CHARS = 9_000
    const val MAX_CHUNKS = 12
    const val MAX_FACTS_PER_CHUNK = 25

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val noise = Regex("""^(?:[0-9a-fA-F-]{16,}|\d{4}-\d{2}-\d{2}[T ][\d:.+Z-]*|https?://\S+|[\w.+-]+@[\w.-]+)$""")

    /**
     * Export files are JSON full of ids, timestamps and structure. Keep only the human-readable strings, one per
     * line, so the model sees what was said rather than braces. Non-JSON text is returned as it is.
     */
    fun flatten(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || (trimmed[0] != '{' && trimmed[0] != '[')) return trimmed
        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return trimmed
        val out = ArrayList<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.values.forEach(::walk)
                is JsonArray -> e.forEach(::walk)
                is JsonPrimitive -> if (e.isString) {
                    val s = e.content.trim()
                    if (s.length >= 12 && !noise.matches(s) && out.lastOrNull() != s) out += s
                }
            }
        }
        walk(root)
        return out.joinToString("\n")
    }

    /** Split on line breaks into pieces of at most [size] characters; at most [max] pieces (the rest is dropped). */
    fun chunks(text: String, size: Int = CHUNK_CHARS, max: Int = MAX_CHUNKS): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) result += current.toString().trim()
            current.clear()
        }
        for (line in text.lineSequence()) {
            var rest = line
            while (rest.length > size) { // one enormous line
                flush()
                result += rest.take(size)
                rest = rest.drop(size)
            }
            if (current.length + rest.length + 1 > size) flush()
            current.append(rest).append('\n')
        }
        flush()
        return result.take(max)
    }

    fun systemPrompt(existing: List<String>): String = buildString {
        appendLine("You are filling the long-term memory of a personal voice assistant whose user is called Sir.")
        appendLine("Below is text exported from Sir's earlier chats or notes with another assistant. Everything the human")
        appendLine("wrote was written by Sir. Extract DURABLE facts about Sir that will still matter in a month:")
        appendLine("his name and how he likes to be addressed, family and friends (names, relations), work, business and")
        appendLine("projects, goals, skills, devices, apps and tools he uses, places, habits and routines, likes and dislikes,")
        appendLine("how he wants answers (language, tone, length).")
        appendLine("Ignore one-off questions, the assistant's own explanations, code, and anything about OTPs, passwords,")
        appendLine("PINs, bank or card details.")
        appendLine("Write each fact as one short third-person sentence starting with \"Sir\", e.g. \"Sir runs a YouTube channel about tech.\"")
        appendLine("Only facts the text clearly supports; never guess. No duplicates. At most $MAX_FACTS_PER_CHUNK facts; an empty list is fine.")
        appendLine("Reply with JSON only: {\"facts\":[{\"text\":\"...\",\"category\":\"person|preference|routine|project|other\"}],\"summary\":\"\"}")
        if (existing.isNotEmpty()) {
            appendLine("Already known (do not repeat):")
            existing.takeLast(80).forEach { appendLine("- $it") }
        }
    }
}
