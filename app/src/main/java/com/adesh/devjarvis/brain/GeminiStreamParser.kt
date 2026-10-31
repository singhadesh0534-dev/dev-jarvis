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

    data class FunctionCall(val name: String, val args: JsonObject, val id: String?)

    data class Chunk(
        /** Speakable text in this chunk (thought parts excluded). */
        val text: String = "",
        val calls: List<FunctionCall> = emptyList(),
        /**
         * Every part exactly as received, incl. `thoughtSignature`. Gemini 3 needs these sent back unchanged
         * in the next request when tools are used.
         */
        val rawParts: List<JsonObject> = emptyList(),
        val blockedReason: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null for lines that carry no payload (blank lines, comments, `event:` lines). */
    fun parseLine(line: String): Chunk? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null

        (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            return Chunk(blockedReason = it)
        }

        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        val text = StringBuilder()
        val calls = mutableListOf<FunctionCall>()
        for (p in parts) {
            // "thought" parts are the model's private reasoning — never speak them.
            if (p["thought"]?.jsonPrimitive?.booleanOrNull == true) continue
            p["text"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
            (p["functionCall"] as? JsonObject)?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let
                calls += FunctionCall(
                    name = name,
                    args = fc["args"] as? JsonObject ?: JsonObject(emptyMap()),
                    id = fc["id"]?.jsonPrimitive?.contentOrNull,
                )
            }
        }

        val finish = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
        val blocked = finish?.takeIf { it == "SAFETY" || it == "PROHIBITED_CONTENT" || it == "BLOCKLIST" }
        if (parts.isEmpty() && blocked == null) return null
        return Chunk(text.toString(), calls, parts, blocked)
    }

    /** Pulls the human-readable message out of a Gemini error body, if any. */
    fun errorMessage(body: String): String =
        runCatching {
            json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrDefault(body.take(200))
}
