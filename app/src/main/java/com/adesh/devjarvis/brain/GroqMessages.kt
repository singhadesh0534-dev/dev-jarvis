package com.adesh.devjarvis.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAI-format message helpers for Groq. Pure, unit-tested. */
object GroqMessages {
    fun text(role: String, content: String): JsonObject = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    fun assistantWithTools(text: String, calls: List<GroqStreamParser.ToolCallAssembler.Call>): JsonObject = buildJsonObject {
        put("role", "assistant")
        if (text.isBlank()) put("content", JsonNull) else put("content", text)
        put("tool_calls", JsonArray(calls.map { c ->
            buildJsonObject {
                put("id", c.id)
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", c.name)
                    put("arguments", c.argumentsJson.ifBlank { "{}" })
                })
            }
        }))
    }

    fun toolResult(callId: String, ok: Boolean, result: String): JsonObject = buildJsonObject {
        put("role", "tool")
        put("tool_call_id", callId)
        put("content", buildJsonObject { put("ok", ok); put("result", result) }.toString())
    }

    /** Gemini function declarations -> OpenAI `tools`. The schemas are compatible. */
    fun toolsFromGemini(declarations: List<JsonObject>): JsonArray = JsonArray(declarations.map { d ->
        buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", d["name"]!!)
                put("description", d["description"]!!)
                put("parameters", d["parameters"] ?: buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                })
            })
        }
    })
}
