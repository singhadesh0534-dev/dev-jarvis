package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudCardsTest {
    @Test fun knownToolGetsAFriendlyTitle() {
        val c = HudCards.forTool(1, "get_weather", true, "Lucknow: clear sky,\n 27°C")!!
        assertEquals("WEATHER", c.title)
        assertEquals("Lucknow: clear sky, 27°C", c.body)
        assertTrue(c.ok)
    }

    @Test fun unknownToolUsesItsName() {
        assertEquals("OPEN APP", HudCards.forTool(1, "open_app", false, "YouTube not installed")!!.title)
    }

    @Test fun smallStepsAndEmptyResultsGetNoCard() {
        assertNull(HudCards.forTool(1, "tap", true, "tapped"))
        assertNull(HudCards.forTool(1, "get_time", true, "9:40 PM"))
        assertNull(HudCards.forTool(1, "get_weather", true, "   "))
    }

    @Test fun longResultIsCut() {
        val c = HudCards.forTool(1, "web_answer", true, "x".repeat(900))!!
        assertEquals(HudCards.MAX_BODY, c.body.length)
        assertTrue(c.body.endsWith("…"))
    }
}
