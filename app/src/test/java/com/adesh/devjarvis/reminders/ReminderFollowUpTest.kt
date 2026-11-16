package com.adesh.devjarvis.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderFollowUpTest {
    @Test fun tasksGetFollowUpsEventsDont() {
        assertTrue(ReminderFollowUp.needsFollowUp("dawai le lo"))
        assertTrue(!ReminderFollowUp.needsFollowUp("meeting 5 baje"))
        assertEquals(15 * 60_000L, ReminderFollowUp.delayMs(1))
        assertNull(ReminderFollowUp.delayMs(3))
        assertTrue(ReminderFollowUp.saysDone("haan dawai le li"))
        assertTrue(!ReminderFollowUp.saysDone("abhi nahi"))
    }
}
