package com.adesh.devjarvis.brain

import com.adesh.devjarvis.tools.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Gemini `contents` JSON banane ke helpers. Pure, unit-tested. */
object GeminiContents {

    fun text(role: Role, text: String): JsonObject = buildJsonObject {
        put("role", role.geminiRole)
        putJsonArray("parts") { addJsonObject { put("text", text) } }
    }

    fun fromTurns(turns: List<Turn>): JsonArray = JsonArray(turns.map { text(it.role, it.text) })

    /** The model's turn, with every raw part (and thought signature) kept exactly as received. */
    fun modelTurn(rawParts: List<JsonObject>): JsonObject = buildJsonObject {
        put("role", Role.JARVIS.geminiRole)
        put("parts", JsonArray(rawParts))
    }

    /** Results of the tools the model asked for, in the same order. */
    fun functionResponses(results: List<Pair<GeminiStreamParser.FunctionCall, ToolResult>>): JsonObject = buildJsonObject {
        put("role", "user")
        put("parts", buildJsonArray {
            results.forEach { (call, result) ->
                addJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", call.name)
                        call.id?.let { put("id", it) }
                        putJsonObject("response") {
                            put("ok", result.ok)
                            put("result", result.message)
                        }
                    }
                }
            }
        })
    }
}
