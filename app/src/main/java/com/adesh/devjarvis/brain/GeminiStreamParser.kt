package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Gemini `streamGenerateContent?alt=sse` ki ek-ek line ko parse karta hai. Pure Kotlin, unit-tested. */
object GeminiStreamParser {

    sealed interface Chunk {
        data class Text(val text: String) : Chunk
        data class Blocked(val reason: String) : Chunk
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null for lines that carry no payload (blank lines, comments, `event:` lines). */
    fun parseLine(line: String): Chunk? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null

        (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            return Chunk.Blocked(it)
        }

        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val parts = (candidate["content"] as? JsonObject)?.get("parts") as? JsonArray
        val text = (parts ?: JsonArray(emptyList()))
            .map { it.jsonObject }
            // "thought" parts are the model's private reasoning — never speak them.
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        if (text.isNotEmpty()) return Chunk.Text(text)

        val finish = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
        if (finish == "SAFETY" || finish == "PROHIBITED_CONTENT" || finish == "BLOCKLIST") {
            return Chunk.Blocked(finish)
        }
        return null
    }

    /** Pulls the human-readable message out of a Gemini error body, if any. */
    fun errorMessage(body: String): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrDefault(body.take(200))
}
