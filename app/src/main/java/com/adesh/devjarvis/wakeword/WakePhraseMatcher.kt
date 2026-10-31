package com.adesh.devjarvis.wakeword

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Vosk ke JSON results padh ke decide karta hai ki wake word bola gaya ya nahi.
 * Pure Kotlin — unit tests mein check hota hai.
 */
object WakePhraseMatcher {

    data class Heard(val text: String, val confidence: Float?, val isFinal: Boolean)

    /** Above this sensitivity we trigger on partial results (fastest, more false alarms). */
    const val PARTIAL_TRIGGER_SENSITIVITY = 0.8f

    private val json = Json { ignoreUnknownKeys = true }
    private val spaces = Regex("""\s+""")
    private val punctuation = Regex("""[^\p{L}\p{M}\p{N}'\[\] ]""")

    /** Parses either `{"partial": "..."}` or a final `{"text": "...", "result": [{"conf":..}]}`. */
    fun parse(raw: String, isFinal: Boolean): Heard? {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val text = obj[if (isFinal) "text" else "partial"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (text.isEmpty()) return null
        val confs = (obj["result"] as? JsonArray)
            ?.mapNotNull { w ->
                val o = w.jsonObject
                val word = o["word"]?.jsonPrimitive?.contentOrNull
                if (word == null || word == "[unk]") null else o["conf"]?.jsonPrimitive?.floatOrNull
            }
            .orEmpty()
        return Heard(text, confs.takeIf { it.isNotEmpty() }?.average()?.toFloat(), isFinal)
    }

    /** Returns the phrase if [text] contains it as whole words (ignores `[unk]` tokens). */
    fun matchedPhrase(text: String, phrases: List<String>): String? {
        val words = spaces.split(punctuation.replace(text.lowercase(), " ").trim()).filter { it.isNotEmpty() && it != "[unk]" }
        if (words.isEmpty()) return null
        // Longest phrase first, so "stop jarvis" wins over "jarvis".
        return phrases.sortedByDescending { it.length }.firstOrNull { phrase ->
            val p = spaces.split(phrase.lowercase().trim())
            p.isNotEmpty() && words.windowed(p.size).any { it == p }
        }
    }

    /** Minimum average word confidence for a final result at this sensitivity. */
    fun minConfidence(sensitivity: Float): Float = 0.95f - 0.5f * sensitivity.coerceIn(0f, 1f)

    fun shouldTrigger(heard: Heard, phrases: List<String>, sensitivity: Float): String? {
        val phrase = matchedPhrase(heard.text, phrases) ?: return null
        if (!heard.isFinal) return if (sensitivity >= PARTIAL_TRIGGER_SENSITIVITY) phrase else null
        val conf = heard.confidence ?: return phrase
        return if (conf >= minConfidence(sensitivity)) phrase else null
    }

    /** Vosk grammar JSON: the phrases plus "[unk]" so random speech doesn't force-match. */
    fun grammarJson(phrases: List<String>): String =
        (phrases.map { it.lowercase() } + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }
}
