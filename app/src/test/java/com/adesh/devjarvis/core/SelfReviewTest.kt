package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfReviewTest {
    @Test fun recentActionsAreShortAndOrdered() {
        val now = 10 * 60 * 60 * 1000L
        val list = listOf(
            SelfReview.Done(now - 60_000, "make_call", "contact=Mummy", true, "calling Mummy\nmore"),
            SelfReview.Done(now - 120_000, "set_alarm", "time=06:00", false, "no permission"),
            SelfReview.Done(now - 20 * 60 * 60 * 1000L, "old", "", true, "too old"),
        )
        val text = SelfReview.recentActions(list, now)
        val lines = text.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("set_alarm(time=06:00) FAILED: no permission"))
        assertTrue(lines[1].contains("make_call(contact=Mummy) done: calling Mummy"))
    }

    @Test fun lessonsKeepMistakesOnceNewestLast() {
        val journal = listOf(
            "16 Nov 09:17:57  [TOOL_FAILED] jarvis_settings(value=Laura) -> not valid",
            "16 Nov 09:18:00  [LIVE_FAILED] close 1007",
            "16 Nov 09:18:07  [SIR_UNHAPPY] Sir: 'aaj hi bekar hai'",
            "16 Nov 09:19:57  [TOOL_FAILED] jarvis_settings(value=Laura) -> not valid",
        )
        val l = SelfReview.lessons(journal).lines()
        assertEquals(2, l.size)
        assertTrue(l[0].startsWith("- [SIR_UNHAPPY]"))
        assertTrue(l[1].startsWith("- [TOOL_FAILED] jarvis_settings"))
    }
}
