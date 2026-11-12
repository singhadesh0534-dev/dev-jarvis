package com.adesh.devjarvis.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sir sirf ElevenLabs key daalte hain; awaaz aur model JARVIS khud chunta hai, us account mein jo maujood hai usme se.
 * Awaaz: gehri, shaant, mard, British lehja pehle. Model: jo Hindi bol sake aur sabse tez ho. Pure, unit-tested.
 */
object ElevenPicker {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    /** Fast first: a voice assistant must answer quickly, and these also use the fewest free characters. */
    val MODEL_ORDER = listOf("eleven_flash_v2_5", "eleven_turbo_v2_5", "eleven_multilingual_v2", "eleven_v3")
    private val NAMES = listOf("daniel" to 5, "george" to 5, "brian" to 4, "bill" to 3, "adam" to 3, "eric" to 2, "chris" to 2, "roger" to 2)
    private val HER_NAMES = listOf("sarah" to 5, "jessica" to 4, "lily" to 4, "matilda" to 4, "laura" to 3, "aria" to 3, "alice" to 3, "charlotte" to 3, "rachel" to 3, "bella" to 3)
    private val SOFT = setOf("soft", "warm", "gentle", "calm", "sweet", "pleasant", "friendly", "expressive", "playful", "cute", "husky", "whispery", "relaxed")
    private val DEEP = setOf("deep", "mature", "calm", "formal", "classy", "confident", "wise", "serious", "professional")

    private fun JsonObject.str(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().lowercase()

    /** [female] = the girlfriend role: a young, soft woman's voice (an Indian accent first, it reads Hinglish best). */
    fun scoreVoice(name: String, labels: Map<String, String>, category: String, female: Boolean = false): Int {
        var s = 0
        if (female) {
            when (labels["gender"]) { "female" -> s += 6; "male" -> s -= 20 }
            if ("indian" in labels["accent"].orEmpty()) s += 4
            if (labels["age"].orEmpty() == "young") s += 1
            if (labels["descriptive"].orEmpty() in SOFT || labels["description"].orEmpty() in SOFT) s += 2
            HER_NAMES.firstOrNull { name.lowercase().startsWith(it.first) }?.let { s += it.second }
            if (category == "premade") s += 1
            return s
        }
        when (labels["gender"]) { "male" -> s += 6; "female" -> s -= 20 }
        if ("british" in labels["accent"].orEmpty()) s += 4
        if (labels["age"].orEmpty().replace('-', '_') in setOf("middle_aged", "old")) s += 1
        if (labels["descriptive"].orEmpty() in DEEP || labels["description"].orEmpty() in DEEP) s += 2
        NAMES.firstOrNull { name.lowercase().startsWith(it.first) }?.let { s += it.second }
        if (category == "premade") s += 1 // always usable on a free account
        return s
    }

    /** Voice id from the body of GET /v1/voices, or null if there is none to choose. */
    fun pickVoice(body: String?, female: Boolean = false): String? {
        val root = runCatching { json.parseToJsonElement(body.orEmpty()) as JsonObject }.getOrNull() ?: return null
        val voices = (root["voices"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return voices.mapNotNull { v ->
            val id = (v["voice_id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val labels = (v["labels"] as? JsonObject)?.mapValues { (it.value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().lowercase() }.orEmpty()
            id to scoreVoice(v.str("name"), labels, v.str("category"), female)
        }.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first
    }

    /** Model id from the body of GET /v1/models: the first of [MODEL_ORDER] that can speak, Hindi-capable ones first. */
    fun pickModel(body: String?): String? {
        val list = runCatching { json.parseToJsonElement(body.orEmpty()) as JsonArray }.getOrNull() ?: return null
        val speaking = list.mapNotNull { it as? JsonObject }.filter { it["can_do_text_to_speech"]?.jsonPrimitive?.booleanOrNull != false }
        fun hindi(m: JsonObject) = (m["languages"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.str("language_id") == "hi" }
        val ids = speaking.associateBy { it.str("model_id") }
        return MODEL_ORDER.firstOrNull { ids[it]?.let(::hindi) == true } ?: MODEL_ORDER.firstOrNull { it in ids }
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
