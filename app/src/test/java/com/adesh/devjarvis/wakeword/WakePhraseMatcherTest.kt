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

class WakeFalseAlarmTest {
    private val wake = JarvisConfig.WAKE_PHRASES
    private fun final(raw: String) = WakePhraseMatcher.parse(raw, isFinal = true)!!

    @Test fun parsesTimingWeakestWordAndLeadingNoise() {
        val h = final("""{"result":[{"conf":1.0,"start":0.1,"end":0.4,"word":"[unk]"},{"conf":1.0,"start":0.5,"end":0.8,"word":"[unk]"},{"conf":0.9,"start":1.0,"end":1.3,"word":"hello"},{"conf":0.6,"start":1.3,"end":1.6,"word":"dev"}],"text":"[unk] [unk] hello dev"}""")
        assertEquals(2, h.leadingUnknown)
        assertEquals(0.6f, h.weakest!!, 0.001f)
        assertEquals(0.6f, h.duration!!, 0.001f)
    }

    @Test fun oneWeakWordBlocksThePhrase() {
        val h = final("""{"result":[{"conf":1.0,"start":0.0,"end":0.3,"word":"hello"},{"conf":0.4,"start":0.3,"end":0.6,"word":"dev"}],"text":"hello dev"}""")
        assertNull(WakePhraseMatcher.shouldTrigger(h, wake, 0.6f)) // average 0.7 would have passed before
    }

    @Test fun clicksAndDronesDoNotWake() {
        val click = final("""{"result":[{"conf":1.0,"start":0.0,"end":0.12,"word":"jarvis"}],"text":"jarvis"}""")
        val drone = final("""{"result":[{"conf":1.0,"start":0.0,"end":3.4,"word":"jarvis"}],"text":"jarvis"}""")
        val real = final("""{"result":[{"conf":1.0,"start":0.0,"end":0.55,"word":"jarvis"}],"text":"jarvis"}""")
        assertNull(WakePhraseMatcher.shouldTrigger(click, wake, 0.6f))
        assertNull(WakePhraseMatcher.shouldTrigger(drone, wake, 0.6f))
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(real, wake, 0.6f))
    }

    @Test fun insideOtherSpeechItMustBeLouder() {
        val h = final("""{"result":[{"conf":1.0,"start":0.0,"end":0.3,"word":"[unk]"},{"conf":1.0,"start":0.3,"end":0.6,"word":"[unk]"},{"conf":1.0,"start":0.6,"end":1.1,"word":"jarvis"}],"text":"[unk] [unk] jarvis"}""")
        assertNull(WakePhraseMatcher.shouldTrigger(h, wake, 0.6f, loudness = 1.0f))      // TV / song at the same level
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(h, wake, 0.6f, loudness = 2.4f)) // Sir, close to the phone
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(h, wake, 0.9f, loudness = 1.0f)) // max sensitivity: old behaviour
    }

    @Test fun loudnessComparesPhraseWithWhatCameBefore() {
        val quietThenLoud = List(20) { 100f } + List(6) { 900f }
        assertTrue(WakePhraseMatcher.loudnessOverBackground(quietThenLoud, 6) > 5f)
        val sameLevel = List(26) { 800f }
        assertEquals(1.0f, WakePhraseMatcher.loudnessOverBackground(sameLevel, 6), 0.01f)
        assertTrue(WakePhraseMatcher.loudnessOverBackground(listOf(500f, 500f), 6) > 100f) // too little history: don't block
    }
}

class WakeOverPhoneMediaTest {
    private val wake = JarvisConfig.WAKE_PHRASES
    private val clean = WakePhraseMatcher.parse(
        """{"result":[{"conf":1.0,"start":0.0,"end":0.55,"word":"jarvis"}],"text":"jarvis"}""", isFinal = true,
    )!!

    @Test fun videoSayingJarvisAtItsOwnLevelDoesNotWake() {
        assertNull(WakePhraseMatcher.shouldTrigger(clean, wake, 0.6f, loudness = 1.0f, overPhoneMedia = true))
        assertNull(WakePhraseMatcher.shouldTrigger(clean, wake, 0.6f, loudness = null, overPhoneMedia = true))
        assertNull(WakePhraseMatcher.shouldTrigger(clean, wake, 1.0f, loudness = 1.1f, overPhoneMedia = true)) // even at max sensitivity
    }

    @Test fun sirSpeakingOverTheVideoWakes() {
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(clean, wake, 0.6f, loudness = 2.0f, overPhoneMedia = true))
    }

    @Test fun withoutMediaNothingChanges() {
        assertEquals("jarvis", WakePhraseMatcher.shouldTrigger(clean, wake, 0.6f, loudness = 1.0f))
    }
}
