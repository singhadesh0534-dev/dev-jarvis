package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Backup dimaag ke liye sabse achhe models chunna, us account ki apni list (GET /models) se.
 * Groq pe har model ki free limit alag hoti hai: ek ki limit khatam, to agla achha model — jawab rukta nahi.
 * Pure, unit-tested.
 */
object BackupModels {
    /** Strongest first. Matched as substrings so newer versions of the same family still count. */
    val PREFERRED = listOf(
        "gpt-oss-120b", "kimi-k2", "deepseek-v3", "deepseek-r1", "qwen3-235b", "llama-4-maverick", "qwen3-32b",
        "llama-3.3-70b", "gemma-3-27b", "mistral-small", "gpt-oss-20b", "llama-4-scout", "llama-3.1-8b",
    )
    /** Not chat models, or not useful for an assistant that calls tools. */
    private val SKIP = listOf("whisper", "guard", "tts", "playai", "compound", "embed", "vision-preview", "distil", "orpheus", "allam")
    const val MAX = 4

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun rank(id: String): Int = PREFERRED.indexOfFirst { it in id.lowercase() }.let { if (it < 0) PREFERRED.size else it }

    private fun entries(body: String?): List<JsonObject> {
        val root = runCatching { json.parseToJsonElement(body.orEmpty()) }.getOrNull() ?: return emptyList()
        val arr = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /**
     * Groq / Cerebras / any OpenAI-style list: the best chat models available, at most [MAX]. [current] (the
     * model set in Settings) stays first when it is in the list.
     */
    fun pick(body: String?, current: String = ""): List<String> {
        val ids = entries(body)
            .filter { (it["active"] as? JsonPrimitive)?.contentOrNull != "false" }
            .map { it.str("id") }
            .filter { id -> id.isNotBlank() && SKIP.none { it in id.lowercase() } }
            .distinct()
        val ranked = ids.filter { rank(it) < PREFERRED.size }.sortedBy { rank(it) }
        val chosen = (listOf(current).filter { it in ids } + ranked).distinct()
        return chosen.take(MAX)
    }

    /**
     * OpenRouter: free models (price 0) that can call tools, best first. Models without a known family come last,
     * larger context first.
     */
    fun pickOpenRouterFree(body: String?): List<String> {
        val free = entries(body).filter { m ->
            val pricing = m["pricing"] as? JsonObject
            val p = pricing?.str("prompt").orEmpty()
            val c = pricing?.str("completion").orEmpty()
            val freeById = m.str("id").endsWith(":free")
            val freeByPrice = p.toDoubleOrNull() == 0.0 && c.toDoubleOrNull() == 0.0
            val tools = (m["supported_parameters"] as? JsonArray)?.any { (it as? JsonPrimitive)?.contentOrNull == "tools" } ?: false
            (freeById || freeByPrice) && tools
        }
        return free.sortedWith(
            compareBy<JsonObject> { rank(it.str("id")) }
                .thenByDescending { (it["context_length"] as? JsonPrimitive)?.longOrNull ?: 0L },
        ).map { it.str("id") }.filter { it.isNotBlank() }.take(MAX)
    }
}
