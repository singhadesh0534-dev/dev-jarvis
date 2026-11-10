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

    data class Heard(
        val text: String,
        val confidence: Float?,
        val isFinal: Boolean,
        /** Lowest confidence among the real words (a phrase is only as sure as its weakest word). */
        val weakest: Float? = null,
        /** "[unk]" tokens before the first real word: other speech / music was already going on. */
        val leadingUnknown: Int = 0,
        /** Seconds from the first real word's start to the last one's end; null if unknown. */
        val duration: Float? = null,
    )

    /** With other speech / music going on, the phrase must be this much louder than what came before it. */
    const val BACKGROUND_RATIO = 1.5f
    /** A real "Jarvis" / "Hello Dev" takes about this long; clicks, coughs and long drones don't. */
    const val MIN_DURATION = 0.25f
    const val MAX_DURATION = 2.2f

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
        val real = (obj["result"] as? JsonArray)
            ?.mapNotNull { w -> runCatching { w.jsonObject }.getOrNull() }
            ?.filter { o -> o["word"]?.jsonPrimitive?.contentOrNull.let { it != null && it != "[unk]" } }
            .orEmpty()
        val confs = real.mapNotNull { it["conf"]?.jsonPrimitive?.floatOrNull }
        val start = real.firstOrNull()?.get("start")?.jsonPrimitive?.floatOrNull
        val end = real.lastOrNull()?.get("end")?.jsonPrimitive?.floatOrNull
        return Heard(
            text = text,
            confidence = confs.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            isFinal = isFinal,
            weakest = confs.minOrNull(),
            leadingUnknown = spaces.split(text).takeWhile { it == "[unk]" }.size,
            duration = if (start != null && end != null && end > start) end - start else null,
        )
    }

    /**
     * How much louder the phrase was than what came just before it. [levels] = RMS of the latest ~100 ms chunks,
     * newest last; the phrase is the last [phraseChunks] of them. Quiet before = a big number.
     */
    fun loudnessOverBackground(levels: List<Float>, phraseChunks: Int): Float {
        val p = phraseChunks.coerceIn(2, 20)
        if (levels.size < p + 3) return Float.MAX_VALUE // nothing to compare with: don't block
        val phrase = levels.takeLast(p).average()
        val before = levels.dropLast(p).takeLast(15).average()
        return if (before <= 1.0) Float.MAX_VALUE else (phrase / before).toFloat()
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

    /**
     * @param loudness [loudnessOverBackground] for this utterance, if the caller measured it.
     * Below [PARTIAL_TRIGGER_SENSITIVITY] three extra checks stop JARVIS waking by himself: a sane length,
     * every word confident, and — when other speech or music was already going on — clearly louder than it.
     */
    fun shouldTrigger(
        heard: Heard, phrases: List<String>, sensitivity: Float, loudness: Float? = null,
        /** The phone itself is playing a video / song through its speaker: the phrase must stand out from it. */
        overPhoneMedia: Boolean = false,
    ): String? {
        val phrase = matchedPhrase(heard.text, phrases) ?: return null
        if (overPhoneMedia) {
            if (!heard.isFinal || loudness == null || loudness < BACKGROUND_RATIO) return null
        }
        if (!heard.isFinal) return if (sensitivity >= PARTIAL_TRIGGER_SENSITIVITY) phrase else null
        if (sensitivity < PARTIAL_TRIGGER_SENSITIVITY) {
            heard.duration?.let { if (it < MIN_DURATION || it > MAX_DURATION) return null }
            if (heard.leadingUnknown > 1 && loudness != null && loudness < BACKGROUND_RATIO) return null
        }
        val conf = heard.weakest ?: heard.confidence ?: return phrase
        return if (conf >= minConfidence(sensitivity)) phrase else null
    }

    /** Vosk grammar JSON: the phrases plus "[unk]" so random speech doesn't force-match. */
    fun grammarJson(phrases: List<String>): String =
        (phrases.map { it.lowercase() } + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }
}
