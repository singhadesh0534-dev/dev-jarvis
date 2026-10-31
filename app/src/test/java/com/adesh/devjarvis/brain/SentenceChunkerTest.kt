package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceChunkerTest {

    private fun streamAll(chunker: SentenceChunker, vararg deltas: String): List<String> {
        val out = mutableListOf<String>()
        deltas.forEach { out += chunker.feed(it) }
        chunker.flush()?.let { out += it }
        return out
    }

    @Test
    fun splitsSentencesAcrossDeltas() {
        val out = streamAll(SentenceChunker(), "Done, Sir", ". Alarm 6 baje ka ", "laga diya. Aur", " kuch?")
        assertEquals(listOf("Done, Sir.", "Alarm 6 baje ka laga diya.", "Aur kuch?"), out)
    }

    @Test
    fun waitsForNextCharBeforeCuttingOnPeriod() {
        val c = SentenceChunker()
        assertTrue(c.feed("Version 3.").isEmpty())
        assertTrue(c.feed("5 is out").isEmpty())
        assertEquals("Version 3.5 is out", c.flush())
    }

    @Test
    fun doesNotSplitOnAbbreviation() {
        val out = streamAll(SentenceChunker(), "Dr. Banner is here. Shall I let him in?")
        assertEquals(listOf("Dr. Banner is here.", "Shall I let him in?"), out)
    }

    @Test
    fun hindiDandaEndsSentence() {
        val out = streamAll(SentenceChunker(), "Ho gaya Sir। Ab aaram kijiye।")
        assertEquals(listOf("Ho gaya Sir।", "Ab aaram kijiye।"), out)
    }

    @Test
    fun longFirstClauseBreaksOnCommaForFastStart() {
        val text = "Sir, the weather in Mumbai today is rather dramatic and humid as usual, so carry an umbrella. Fine."
        val out = streamAll(SentenceChunker(firstChunkSoftLimit = 40), text)
        assertEquals("Sir, the weather in Mumbai today is rather dramatic and humid as usual,", out[0])
        assertEquals("so carry an umbrella.", out[1])
        assertEquals("Fine.", out[2])
    }

    @Test
    fun keepsClosingQuoteWithSentence() {
        val out = streamAll(SentenceChunker(), "He said \"never.\" Then left.")
        assertEquals(listOf("He said \"never.\"", "Then left."), out)
    }

    @Test
    fun newlineIsABoundary() {
        val out = streamAll(SentenceChunker(), "Line one\nLine two")
        assertEquals(listOf("Line one", "Line two"), out)
    }

    @Test
    fun hardLimitPreventsUnboundedBuffer() {
        val c = SentenceChunker(hardLimit = 30)
        val out = c.feed("word ".repeat(20))
        assertTrue(out.isNotEmpty())
        assertTrue(out.all { it.length <= 30 })
    }

    @Test
    fun flushOnEmptyIsNull() {
        assertNull(SentenceChunker().flush())
    }
}
