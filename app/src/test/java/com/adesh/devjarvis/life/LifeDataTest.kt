package com.adesh.devjarvis.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LifeDataTest {
    @Test fun roundTrip() {
        val d = LifeData(
            routines = mapOf("good night" to "volume 20; alarm batao"),
            notes = listOf(Note(5, "gaadi ki service 15 ko")),
            lists = mapOf("shopping" to listOf("doodh", "bread")),
            expenses = listOf(Expense(7, 200.0, "petrol", "")),
            places = listOf(Place("ghar", 26.85, 80.95)),
            placeReminders = listOf(PlaceReminder(1, "ghar", "charger lagana")),
        )
        assertEquals(d, LifeCodec.decode(LifeCodec.encode(d)))
    }

    @Test fun badJsonGivesEmpty() {
        assertEquals(LifeData(), LifeCodec.decode("oops"))
        assertEquals(LifeData(), LifeCodec.decode(null))
    }

    @Test fun keyNormalises() {
        assertEquals("good night", LifeMath.key("  Good   Night! "))
    }

    @Test fun expenseSummaryGroupsByCategory() {
        val e = listOf(Expense(10, 200.0, "petrol", ""), Expense(20, 450.0, "khana", ""), Expense(30, 600.0, "petrol", ""), Expense(999, 50.0, "x", ""))
        val s = LifeMath.expenseSummary(e, 0, 100)
        assertTrue(s, s.startsWith("total 1250 rupees (petrol 800, khana 450) over 3 entries"))
        assertEquals("no expenses recorded", LifeMath.expenseSummary(e, 2000, 3000))
    }

    @Test fun periodStarts() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = java.time.ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun d(p: String) = java.time.Instant.ofEpochMilli(LifeMath.periodStart(p, now, zone)).atZone(zone).toLocalDate().toString()
        assertEquals("2026-11-10", d("today"))
        assertEquals("2026-11-04", d("week"))
        assertEquals("2026-11-01", d("month"))
    }

    @Test fun distance() {
        assertTrue(LifeMath.distanceMeters(26.85, 80.95, 26.85, 80.95) < 1.0)
        val oneKm = LifeMath.distanceMeters(26.85, 80.95, 26.859, 80.95)
        assertTrue(oneKm.toString(), oneKm in 950.0..1050.0)
    }
}
