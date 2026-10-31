package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiStreamParserTest {

    @Test
    fun parsesTextChunk() {
        val line = """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Good evening, "},{"text":"Sir."}]}}]}"""
        assertEquals(GeminiStreamParser.Chunk.Text("Good evening, Sir."), GeminiStreamParser.parseLine(line))
    }

    @Test
    fun skipsThoughtParts() {
        val line = """data: {"candidates":[{"content":{"parts":[{"text":"secret plan","thought":true},{"text":"Hello"}]}}]}"""
        assertEquals(GeminiStreamParser.Chunk.Text("Hello"), GeminiStreamParser.parseLine(line))
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
        assertEquals(GeminiStreamParser.Chunk.Blocked("SAFETY"), GeminiStreamParser.parseLine(line))
    }

    @Test
    fun detectsSafetyFinish() {
        val line = """data: {"candidates":[{"finishReason":"SAFETY"}]}"""
        assertEquals(GeminiStreamParser.Chunk.Blocked("SAFETY"), GeminiStreamParser.parseLine(line))
    }

    @Test
    fun extractsErrorMessage() {
        val body = """{"error":{"code":429,"message":"Resource has been exhausted","status":"RESOURCE_EXHAUSTED"}}"""
        assertEquals("Resource has been exhausted", GeminiStreamParser.errorMessage(body))
    }
}
