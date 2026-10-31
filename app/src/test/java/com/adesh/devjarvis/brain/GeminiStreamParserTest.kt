package com.adesh.devjarvis.brain

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiStreamParserTest {

    @Test
    fun parsesTextChunk() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Good evening, "},{"text":"Sir."}]}}]}"""
        val c = GeminiStreamParser.parseLine(line)!!
        assertEquals("Good evening, Sir.", c.text)
        assertEquals(2, c.rawParts.size)
        assertTrue(c.calls.isEmpty())
    }

    @Test
    fun skipsThoughtPartsButKeepsThemRaw() {
        val line = """data: {"candidates":[{"content":{"parts":[{"text":"secret plan","thought":true},{"text":"Hello"}]}}]}"""
        val c = GeminiStreamParser.parseLine(line)!!
        assertEquals("Hello", c.text)
        assertEquals(2, c.rawParts.size)
    }

    @Test
    fun parsesFunctionCallWithSignature() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"set_alarm","args":{"hour":6,"minute":0},"id":"c1"},"thoughtSignature":"abc=="}]}}]}"""
        val c = GeminiStreamParser.parseLine(line)!!
        assertEquals("", c.text)
        assertEquals(1, c.calls.size)
        assertEquals("set_alarm", c.calls[0].name)
        assertEquals(6, c.calls[0].args["hour"]!!.jsonPrimitive.int)
        assertEquals("c1", c.calls[0].id)
        // Signature must survive for the next request.
        assertEquals("abc==", c.rawParts[0]["thoughtSignature"]!!.jsonPrimitive.content)
    }

    @Test
    fun parallelCalls() {
        val line = """data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"make_call","args":{"contact":"Mummy"}}},{"functionCall":{"name":"set_alarm","args":{"hour":6}}}]}}]}"""
        val c = GeminiStreamParser.parseLine(line)!!
        assertEquals(listOf("make_call", "set_alarm"), c.calls.map { it.name })
        assertNull(c.calls[0].id)
    }

    @Test
    fun signatureOnlyPartIsKept() {
        val line = """data: {"candidates":[{"content":{"parts":[{"text":"","thoughtSignature":"zz"}]},"finishReason":"STOP"}]}"""
        val c = GeminiStreamParser.parseLine(line)!!
        assertEquals("", c.text)
        assertEquals(1, c.rawParts.size)
    }

    @Test
    fun ignoresNonDataAndEmpty() {
        assertNull(GeminiStreamParser.parseLine(""))
        assertNull(GeminiStreamParser.parseLine(": keep-alive"))
        assertNull(GeminiStreamParser.parseLine("data: "))
        assertNull(GeminiStreamParser.parseLine("data: not json"))
    }

    @Test
    fun finalChunkWithOnlyUsageIsNull() {
        val line = """data: {"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}],"usageMetadata":{"totalTokenCount":12}}"""
        assertNull(GeminiStreamParser.parseLine(line))
    }

    @Test
    fun detectsPromptBlock() {
        val line = """data: {"promptFeedback":{"blockReason":"SAFETY"}}"""
        assertEquals("SAFETY", GeminiStreamParser.parseLine(line)!!.blockedReason)
    }

    @Test
    fun detectsSafetyFinish() {
        val line = """data: {"candidates":[{"finishReason":"SAFETY"}]}"""
        assertEquals("SAFETY", GeminiStreamParser.parseLine(line)!!.blockedReason)
    }

    @Test
    fun extractsErrorMessage() {
        val body = """{"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED"}}"""
        assertEquals("Resource has been exhausted", GeminiStreamParser.errorMessage(body))
    }
}
