package com.adesh.devjarvis.brain

import com.adesh.devjarvis.tools.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiContentsTest {

    @Test
    fun modelTurnKeepsRawPartsVerbatim() {
        val part = Json.parseToJsonElement("""{"functionCall":{"name":"x","args":{}},"thoughtSignature":"sig"}""").jsonObject
        val turn = GeminiContents.modelTurn(listOf(part))
        assertEquals("model", turn["role"]!!.jsonPrimitive.content)
        assertEquals(part, turn["parts"]!!.jsonArray[0])
    }

    @Test
    fun functionResponsesInOrderWithIds() {
        val c1 = GeminiStreamParser.FunctionCall("make_call", JsonObject(emptyMap()), "id1")
        val c2 = GeminiStreamParser.FunctionCall("set_alarm", JsonObject(emptyMap()), null)
        val turn = GeminiContents.functionResponses(listOf(c1 to ToolResult.ok("calling"), c2 to ToolResult.fail("nope")))
        assertEquals("user", turn["role"]!!.jsonPrimitive.content)
        val parts = turn["parts"]!!.jsonArray.map { it.jsonObject["functionResponse"]!!.jsonObject }
        assertEquals("make_call", parts[0]["name"]!!.jsonPrimitive.content)
        assertEquals("id1", parts[0]["id"]!!.jsonPrimitive.content)
        assertEquals(true, parts[0]["response"]!!.jsonObject["ok"]!!.jsonPrimitive.boolean)
        assertEquals(null, parts[1]["id"])
        assertEquals("nope", parts[1]["response"]!!.jsonObject["result"]!!.jsonPrimitive.content)
    }

    @Test
    fun textTurns() {
        val arr = GeminiContents.fromTurns(listOf(Turn(Role.USER, "hi"), Turn(Role.JARVIS, "hello")))
        assertEquals("model", arr[1].jsonObject["role"]!!.jsonPrimitive.content)
    }
}
