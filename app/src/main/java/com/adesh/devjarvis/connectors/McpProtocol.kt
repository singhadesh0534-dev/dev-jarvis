package com.adesh.devjarvis.connectors

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Claude / ChatGPT jaise connectors: Model Context Protocol (MCP) over HTTP. Koi bhi MCP server URL (Gmail, Calendar,
 * Notion, Zapier, Composio…) jodo, JARVIS uske tools dekh ke chala sakta hai. Ye file sirf messages banati / padhti hai.
 * Pure, unit-tested.
 */
object McpProtocol {
    const val VERSION = "2025-06-18"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Tool(val name: String, val description: String, val params: List<String>, val schema: JsonObject?)

    fun request(id: Int, method: String, params: JsonObject? = null): String = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("method", method)
        if (params != null) put("params", params)
    }.toString()

    fun notification(method: String): String = buildJsonObject { put("jsonrpc", "2.0"); put("method", method) }.toString()

    fun initialize(id: Int): String = request(id, "initialize", buildJsonObject {
        put("protocolVersion", VERSION)
        putJsonObject("capabilities") {}
        putJsonObject("clientInfo") { put("name", "dev-jarvis"); put("version", "1") }
    })

    fun callTool(id: Int, name: String, arguments: JsonObject): String = request(id, "tools/call", buildJsonObject {
        put("name", name); put("arguments", arguments)
    })

    /** The JSON-RPC response with this id, from a plain JSON body or a Server-Sent-Events stream. */
    fun response(body: String, id: Int): JsonObject? {
        val candidates = if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) listOf(body)
        else body.lines().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() }
        for (c in candidates) {
            val el = runCatching { json.parseToJsonElement(c) }.getOrNull() ?: continue
            val objs = if (el is JsonArray) el.mapNotNull { it as? JsonObject } else listOfNotNull(el as? JsonObject)
            objs.firstOrNull { (it["id"] as? JsonPrimitive)?.intOrNull == id }?.let { return it }
        }
        return null
    }

    /** "error" of a response as one line, or null when it succeeded. */
    fun error(resp: JsonObject): String? = (resp["error"] as? JsonObject)?.let { e ->
        ((e["message"] as? JsonPrimitive)?.contentOrNull ?: "error") + ((e["code"] as? JsonPrimitive)?.contentOrNull?.let { " ($it)" } ?: "")
    }

    fun tools(resp: JsonObject): List<Tool> {
        val list = ((resp["result"] as? JsonObject)?.get("tools") as? JsonArray) ?: return emptyList()
        return list.mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val schema = o["inputSchema"] as? JsonObject
            val params = (schema?.get("properties") as? JsonObject)?.keys?.toList().orEmpty()
            Tool(name, (o["description"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(300), params, schema)
        }
    }

    /** The readable text of a tools/call result. */
    fun resultText(resp: JsonObject): String {
        val r = resp["result"] as? JsonObject ?: return error(resp) ?: "no result"
        val text = (r["content"] as? JsonArray).orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            (o["text"] as? JsonPrimitive)?.contentOrNull ?: o["resource"]?.let { res -> (res as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull } }
        }.joinToString("\n").ifBlank { r["structuredContent"]?.toString().orEmpty() }
        val failed = (r["isError"] as? JsonPrimitive)?.booleanOrNull == true
        return (if (failed) "TOOL ERROR: " else "") + text.ifBlank { "(empty result)" }
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    /** Anything that sends, posts, changes or deletes: Sir confirms first (his own rule). */
    private val RISKY = Regex("""(?i)(send|post|publish|reply|forward|delete|remove|trash|archive|create|update|edit|write|insert|move|share|invite|upload|cancel|book|order|buy|pay|transfer|purchase|commit|merge|deploy)""")
    fun risky(toolName: String): Boolean = RISKY.containsMatchIn(toolName)

    /** Arguments the model passed as text -> a JSON object (empty when blank or broken). */
    fun arguments(text: String?): JsonObject =
        runCatching { json.parseToJsonElement(text.orEmpty().ifBlank { "{}" }).jsonObject }.getOrDefault(JsonObject(emptyMap()))
}
