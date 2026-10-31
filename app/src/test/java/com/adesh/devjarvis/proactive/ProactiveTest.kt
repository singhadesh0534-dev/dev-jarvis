package com.adesh.devjarvis.proactive

import com.adesh.devjarvis.proactive.ProactivePolicy.Decision
import com.adesh.devjarvis.proactive.ProactivePolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveTest {
    private val base = ProactivePolicy.Situation(
        minuteOfDay = 10 * 60, quietStartMinute = 23 * 60, quietEndMinute = 7 * 60,
        dnd = false, ringerSilent = false, headphones = false, inCall = false, busy = false, alwaysOnRunning = true,
    )

    @Test fun normalDaySpeaks() = assertEquals(Decision.SPEAK, ProactivePolicy.decide(Kind.NOTIFICATION, base))

    @Test fun quietHoursWrapMidnight() {
        assertTrue(ProactivePolicy.inQuietHours(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(ProactivePolicy.inQuietHours(2 * 60, 23 * 60, 7 * 60))
        assertFalse(ProactivePolicy.inQuietHours(7 * 60, 23 * 60, 7 * 60))
        assertFalse(ProactivePolicy.inQuietHours(12 * 60, 12 * 60, 12 * 60))
    }

    @Test fun nightIsSilentExceptReminders() {
        val night = base.copy(minuteOfDay = 1 * 60)
        assertEquals(Decision.NOTIFY, ProactivePolicy.decide(Kind.BATTERY, night))
        assertEquals(Decision.NOTIFY, ProactivePolicy.decide(Kind.NOTIFICATION, night))
        assertEquals(Decision.SPEAK, ProactivePolicy.decide(Kind.REMINDER, night))
    }

    @Test fun dndCallBusyNeverSpeak() {
        Kind.entries.forEach { k ->
            assertEquals(Decision.NOTIFY, ProactivePolicy.decide(k, base.copy(dnd = true)))
            assertEquals(Decision.NOTIFY, ProactivePolicy.decide(k, base.copy(inCall = true)))
            assertEquals(Decision.NOTIFY, ProactivePolicy.decide(k, base.copy(busy = true)))
            assertEquals(Decision.NOTIFY, ProactivePolicy.decide(k, base.copy(alwaysOnRunning = false)))
        }
    }

    @Test fun silentModeNeedsHeadphones() {
        assertEquals(Decision.NOTIFY, ProactivePolicy.decide(Kind.REMINDER, base.copy(ringerSilent = true)))
        assertEquals(Decision.SPEAK, ProactivePolicy.decide(Kind.REMINDER, base.copy(ringerSilent = true, headphones = true)))
    }

    @Test fun briefingFallbackText() {
        val t = BriefingTemplate.fallback(
            BriefingTemplate.Inputs(7, "Saturday, 31 October", "Mumbai: clear sky, 27°C", listOf("Dawai 9 baje"), emptyList(), 25, false, emptyList()),
        )
        assertTrue(t.startsWith("Good morning, Sir."))
        assertTrue("Mumbai" in t)
        assertTrue("Dawai 9 baje" in t)
        assertTrue("Battery 25%" in t)
        val empty = BriefingTemplate.fallback(BriefingTemplate.Inputs(19, "x", null, emptyList(), emptyList(), 80, false, emptyList()))
        assertTrue(empty.startsWith("Good evening"))
        assertTrue("khaali" in empty)
    }
}
