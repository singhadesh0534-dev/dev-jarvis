package com.adesh.devjarvis.wakeword

import com.adesh.devjarvis.core.JarvisConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    private val wake = JarvisConfig.WAKE_PHRASES

    @Test
    fun parsesPartial() {
        val h = WakePhraseMatcher.parse("""{"partial" : "hello dev"}""", isFinal = false)!!
        assertEquals("hello dev", h.text)
        assertNull(h.confidence)
    }

    @Test
    fun parsesFinalWithConfidenceIgnoringUnk() {
        val raw = """{"result":[{"conf":0.9,"word":"hello"},{"conf":0.7,"word":"dev"},{"conf":0.1,"word":"[unk]"}],"text":"hello dev [unk]"}"""
        val h = WakePhraseMatcher.parse(raw, isFinal = true)!!
        assertEquals(0.8f, h.confidence!!, 0.001f)
    }

    @Test
    fun emptyTextIsNull() {
        assertNull(WakePhraseMatcher.parse("""{"partial" : ""}""", isFinal = false))
        assertNull(WakePhraseMatcher.parse("""{"text" : ""}""", isFinal = true))
        assertNull(WakePhraseMatcher.parse("garbage", isFinal = true))
    }

    @Test
    fun matchesWholeWordsOnly() {
        assertEquals("jarvis", WakePhraseMatcher.matchedPhrase("[unk] jarvis", wake))
        assertEquals("hello dev", WakePhraseMatcher.matchedPhrase("hello dev", wake))
        assertNull(WakePhraseMatcher.matchedPhrase("hello", wake))
        assertNull(WakePhraseMatcher.matchedPhrase("[unk] [unk]", wake))
        assertNull(WakePhraseMatcher.matchedPhrase("jarvisness", wake))
    }

    @Test
    fun prefersLongestPhrase() {
        assertEquals("stop jarvis", WakePhraseMatcher.matchedPhrase("stop jarvis", JarvisConfig.BARGE_IN_PHRASES))
    }

    @Test
    fun selfEchoCheckFindsPhraseInSentence() {
        val spoken = "Main JARVIS hoon, Sir, aapki seva mein."
        assertEquals("jarvis", WakePhraseMatcher.matchedPhrase(spoken, listOf("jarvis")))
    }

    @Test
    fun partialTriggersOnlyAtHighSensitivity() {
        val partial = WakePhraseMatcher.Heard("hello dev", null, isFinal = false)
        assertNull(WakePhraseMatcher.shouldTrigger(partial, wake, 0.6f))
        assertEquals("hello dev", WakePhraseMatcher.shouldTrigger(partial, wake, 0.9f))
    }

    @Test
    fun finalNeedsConfidenceForSensitivity() {
        val weak = WakePhraseMatcher.Heard("jarvis", 0.5f, isFinal = true)
        val strong = WakePhraseMatcher.Heard("jarvis", 0.9f, isFinal = true)
        assertNull(WakePhraseMatcher.shouldTrigger(weak, wake, 0.2f))
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(strong, wake, 0.2f))
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(weak, wake, 1.0f))
    }

    @Test
    fun grammarIncludesUnk() {
        val g = WakePhraseMatcher.grammarJson(listOf("Hello Dev", "jarvis"))
        assertEquals("""["hello dev","jarvis","[unk]"]""", g)
        assertTrue(g.endsWith("\"[unk]\"]"))
    }
}
