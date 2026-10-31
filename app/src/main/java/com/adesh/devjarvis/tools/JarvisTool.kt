package com.adesh.devjarvis.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Har phone action ek tool hai. Gemini function calling se LLM inhe chunta hai. */
interface JarvisTool {
    val name: String
    val description: String
    /** OpenAPI-style schema for the arguments; null = no arguments. */
    val parameters: JsonObject?

    suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult

    fun declaration(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        parameters?.let { put("parameters", it) }
    }
}

/** Short result the LLM turns into speech. */
data class ToolResult(
    val ok: Boolean,
    val message: String,
    /** E.g. a phone call started: don't open the follow-up mic afterwards. */
    val endsConversation: Boolean = false,
) {
    companion object {
        fun ok(message: String, endsConversation: Boolean = false) = ToolResult(true, message, endsConversation)
        fun fail(message: String) = ToolResult(false, message)
    }
}

/** What a tool can ask of the session while it runs. */
interface ToolContext {
    /** Asks Sir out loud (and on screen); true only on a clear "haan / yes" or a tap on YES. */
    suspend fun confirm(question: String): Boolean
}

/** Tiny DSL for Gemini parameter schemas. */
class ParamsBuilder {
    private val props = mutableListOf<Pair<String, JsonObject>>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
        add(name, required, buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enum != null) putJsonArray("enum") { enum.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        })

    fun integer(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject {
            put("type", "integer")
            put("description", description)
        })

    fun boolean(name: String, description: String, required: Boolean = false) =
        add(name, required, buildJsonObject {
            put("type", "boolean")
            put("description", description)
        })

    private fun add(name: String, isRequired: Boolean, schema: JsonObject) {
        props += name to schema
        if (isRequired) required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (n, s) -> put(n, s) } }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    }
}

fun params(block: ParamsBuilder.() -> Unit): JsonObject = ParamsBuilder().apply(block).build()

/** Convenience base so each tool is a few lines. */
abstract class SimpleTool(
    override val name: String,
    override val description: String,
    override val parameters: JsonObject? = null,
) : JarvisTool

