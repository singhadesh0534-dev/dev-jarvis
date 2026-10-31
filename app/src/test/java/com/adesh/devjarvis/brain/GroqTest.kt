package com.adesh.devjarvis.brain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqTest {

    @Test fun parsesTextDelta() {
        val d = GroqStreamParser.parseLine("""data: {"choices":[{"index":0,"delta":{"content":"Hello Sir"},"finish_reason":null}]}""")!!
        assertEquals("Hello Sir", d.text)
        assertTrue(d.toolFragments.isEmpty())
    }

    @Test fun ignoresDoneAndRoleOnly() {
        assertNull(GroqStreamParser.parseLine("data: [DONE]"))
        assertNull(GroqStreamParser.parseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertNull(GroqStreamParser.parseLine(""))
    }

    @Test fun assemblesFragmentedToolCalls() {
        val lines = listOf(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","type":"function","function":{"name":"set_alarm","arguments":""}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"hour\":"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"6}"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"c2","function":{"name":"toggle_flashlight","arguments":"{}"}}]}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
        )
        val asm = GroqStreamParser.ToolCallAssembler()
        var finish: String? = null
        lines.mapNotNull { GroqStreamParser.parseLine(it) }.forEach { d -> d.toolFragments.forEach(asm::add); d.finishReason?.let { finish = it } }
        val calls = asm.calls()
        assertEquals("tool_calls", finish)
        assertEquals(listOf("set_alarm", "toggle_flashlight"), calls.map { it.name })
        assertEquals(6, calls[0].args()["hour"]!!.jsonPrimitive.int)
        assertEquals("c2", calls[1].id)
    }

    @Test fun brokenArgumentsBecomeEmptyObject() {
        val c = GroqStreamParser.ToolCallAssembler.Call("x", "y", "{not json")
        assertTrue(c.args().isEmpty())
    }

    @Test fun messagesShape() {
        val call = GroqStreamParser.ToolCallAssembler.Call("c1", "make_call", """{"contact":"Mummy"}""")
        val a = GroqMessages.assistantWithTools("", listOf(call))
        assertEquals(JsonNull, a["content"])
        assertEquals("make_call", a["tool_calls"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val t = GroqMessages.toolResult("c1", true, "calling")
        assertEquals("tool", t["role"]!!.jsonPrimitive.content)
        assertEquals("c1", t["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test fun toolsFromGeminiAddsEmptySchema() {
        val decl = Json.parseToJsonElement("""{"name":"lock_screen","description":"Lock"}""").jsonObject
        val t = GroqMessages.toolsFromGemini(listOf(decl))[0].jsonObject
        assertEquals("function", t["type"]!!.jsonPrimitive.content)
        assertEquals("object", t["function"]!!.jsonObject["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun errorMessage() {
        assertEquals("Rate limit reached", GroqStreamParser.errorMessage("""{"error":{"message":"Rate limit reached","type":"tokens"}}"""))
    }
}
