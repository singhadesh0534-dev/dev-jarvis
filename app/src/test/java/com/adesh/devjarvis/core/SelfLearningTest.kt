package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfLearningTest {
    @Test fun picksTodaysMistakesOnly() {
        val j = listOf(
            "16 Nov 09:17:57  [TOOL_FAILED] jarvis_settings(value=Laura) -> not valid",
            "16 Nov 09:18:00  [LIVE_FAILED] close 1007",
            "15 Nov 10:00:00  [SIR_UNHAPPY] old",
            "16 Nov 16:15:30  [SIR_UNHAPPY] Sir: 'galat sunte ho'",
        )
        assertEquals(listOf("[TOOL_FAILED] jarvis_settings(value=Laura) -> not valid", "[SIR_UNHAPPY] Sir: 'galat sunte ho'"),
            SelfLearning.todaysProblems(j, "16 Nov"))
    }

    @Test fun keepsOnlyNewConcreteLessons() {
        val raw = """{"lessons":["Lesson: when a voice name is not in the list, offer the 3 closest real voices instead of retrying",
            "be careful", "Lesson: search inside the app Sir named, not on YouTube ever again please"]}"""
        val got = SelfLearning.parse(raw, listOf("Lesson: search inside the app Sir named, not on YouTube"))
        assertEquals(1, got.size)
        assertEquals(true, got[0].startsWith("Lesson: when a voice name"))
    }
}
