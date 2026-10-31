package com.adesh.devjarvis.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Baat-cheet ke baad LLM se "kya yaad rakhna hai" nikalwana: prompt + JSON parser. Pure, unit-tested. */
object MemoryExtraction {

    val CATEGORIES = listOf("person", "preference", "routine", "project", "other")

    data class Result(val facts: List<Pair<String, String>>, val summary: String)

    fun systemPrompt(existing: List<String>): String = buildString {
        appendLine("You maintain the long-term memory of a personal voice assistant whose user is called Sir.")
        appendLine("From the conversation, extract only DURABLE facts worth remembering for weeks:")
        appendLine("people and relationships (names, nicknames, how they're saved in contacts), preferences, routines, ongoing projects.")
        appendLine("Ignore one-off requests (\"set a timer\"), small talk, and anything about OTPs, passwords, PINs, banking or card numbers.")
        appendLine("Write each fact as one short third-person sentence, e.g. \"Sir's mother is saved as 'Mummy Ji' in contacts.\"")
        appendLine("Do not repeat facts already known. Return at most 5 facts; an empty list is fine.")
        appendLine("Reply with JSON only: {\"facts\":[{\"text\":\"...\",\"category\":\"person|preference|routine|project|other\"}],\"summary\":\"one line about the conversation\"}")
        if (existing.isNotEmpty()) {
            appendLine("Already known:")
            existing.take(60).forEach { appendLine("- $it") }
        }
    }

    fun userPrompt(exchanges: List<Pair<String, String>>): String = buildString {
        appendLine("Conversation:")
        exchanges.forEach { (u, j) -> appendLine("Sir: $u"); appendLine("Assistant: $j") }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    /** OTP / password / PIN / card or any long number — never stored. */
    fun looksSecret(text: String): Boolean = sensitive.containsMatchIn(text)

    private val sensitive = Regex("""(?i)\b(otp|password|passcode|pin|cvv|card number|account number|upi pin)\b|\d{6,}""")

    fun parse(raw: String?): Result? {
        if (raw.isNullOrBlank()) return null
        // Models sometimes wrap JSON in ```json fences or add a sentence — take the outermost object.
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = raw.substring(start, end + 1)
        val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return null
        val facts = (root["facts"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val text = o["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.length in 6..240 } ?: return@mapNotNull null
            if (sensitive.containsMatchIn(text)) return@mapNotNull null // never store secrets or long numbers
            val cat = o["category"]?.jsonPrimitive?.contentOrNull?.lowercase()?.takeIf { it in CATEGORIES } ?: "other"
            text to cat
        }.take(5)
        val summary = root["summary"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().take(200)
        return Result(facts, summary)
    }

    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}
