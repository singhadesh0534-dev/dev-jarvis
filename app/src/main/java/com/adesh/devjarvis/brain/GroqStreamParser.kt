package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Groq (OpenAI-compatible) streaming parser. Tool calls arrive in pieces (name first, arguments as
 * string fragments) — [ToolCallAssembler] stitches them together. Pure, unit-tested.
 */
object GroqStreamParser {

    data class Delta(
        val text: String = "",
        val toolFragments: List<ToolFragment> = emptyList(),
        val finishReason: String? = null,
    )

    data class ToolFragment(val index: Int, val id: String?, val name: String?, val argumentsPart: String?)

    private val json = Json { ignoreUnknownKeys = true }

    fun parseLine(line: String): Delta? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val delta = choice["delta"] as? JsonObject
        val text = delta?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        val fragments = (delta?.get("tool_calls") as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val fn = o["function"] as? JsonObject
            ToolFragment(
                index = o["index"]?.jsonPrimitive?.intOrNull ?: 0,
                id = o["id"]?.jsonPrimitive?.contentOrNull,
                name = fn?.get("name")?.jsonPrimitive?.contentOrNull,
                argumentsPart = fn?.get("arguments")?.jsonPrimitive?.contentOrNull,
            )
        }.orEmpty()
        val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        if (text.isEmpty() && fragments.isEmpty() && finish == null) return null
        return Delta(text, fragments, finish)
    }

    /** Pulls `error.message` out of an OpenAI-style error body. */
    fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
    }.getOrDefault(body.take(200))

    class ToolCallAssembler {
        private data class Building(var id: String? = null, var name: String? = null, val args: StringBuilder = StringBuilder())
        private val byIndex = sortedMapOf<Int, Building>()

        fun add(f: ToolFragment) {
            val b = byIndex.getOrPut(f.index) { Building() }
            f.id?.let { b.id = it }
            f.name?.let { b.name = it }
            f.argumentsPart?.let { b.args.append(it) }
        }

        data class Call(val id: String, val name: String, val argumentsJson: String) {
            /** Parsed arguments; empty object if the model sent nothing or broken JSON. */
            fun args(): JsonObject =
                runCatching { json.parseToJsonElement(argumentsJson.ifBlank { "{}" }).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        }

        fun calls(): List<Call> = byIndex.entries.mapNotNull { (i, b) ->
            val name = b.name ?: return@mapNotNull null
            Call(b.id ?: "call_$i", name, b.args.toString())
        }
    }
}
