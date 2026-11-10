package com.adesh.devjarvis.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoGateTest {
    @Test fun passesEverythingWhenJarvisIsQuiet() {
        val g = EchoGate()
        repeat(10) { assertTrue(g.pass(playing = false, level = 0.05f)) }
    }

    @Test fun blocksOwnEchoWhilePlaying() {
        val g = EchoGate()
        repeat(40) { assertFalse(g.pass(playing = true, level = 0.15f)) }
    }

    @Test fun opensWhenSirSpeaksOverJarvis() {
        val g = EchoGate(loudChunksToOpen = 3)
        assertFalse(g.pass(true, 0.6f))
        assertFalse(g.pass(true, 0.7f))
        assertTrue(g.pass(true, 0.8f))          // third loud chunk in a row
        assertTrue(g.pass(true, 0.1f))          // stays open for the rest of this playback
    }

    @Test fun shortClickDoesNotOpen() {
        val g = EchoGate(loudChunksToOpen = 3)
        assertFalse(g.pass(true, 0.9f))
        assertFalse(g.pass(true, 0.1f))
        assertFalse(g.pass(true, 0.9f))
        assertFalse(g.pass(true, 0.9f))
    }

    @Test fun echoTailStaysBlockedThenReopens() {
        val g = EchoGate(tailChunks = 3)
        g.pass(true, 0.1f)
        assertFalse(g.pass(false, 0.2f))
        assertFalse(g.pass(false, 0.2f))
        assertFalse(g.pass(false, 0.2f))
        assertTrue(g.pass(false, 0.2f))
    }
}
