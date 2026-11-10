package com.adesh.devjarvis.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoGateTest {
    @Test fun passesEverythingWhenJarvisIsQuiet() {
        val g = EchoGate()
        repeat(10) { assertTrue(g.pass(playing = false, headphones = false)) }
    }

    @Test fun neverPassesOwnVoiceOnLoudspeaker() {
        val g = EchoGate()
        repeat(200) { assertFalse(g.pass(playing = true, headphones = false)) } // however loud the echo is
    }

    @Test fun echoTailStaysBlockedThenReopens() {
        val g = EchoGate(tailChunks = 3)
        g.pass(true, false)
        assertFalse(g.pass(false, false))
        assertFalse(g.pass(false, false))
        assertFalse(g.pass(false, false))
        assertTrue(g.pass(false, false))
    }

    @Test fun headphonesKeepTheMicOpenForBargeIn() {
        val g = EchoGate()
        repeat(20) { assertTrue(g.pass(playing = true, headphones = true)) }
    }
}
