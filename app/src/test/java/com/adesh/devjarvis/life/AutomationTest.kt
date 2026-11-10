package com.adesh.devjarvis.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AutomationTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---- triggers
    @Test fun times() {
        assertEquals(22 * 60 + 30, TriggerLogic.parseTime("22:30"))
        assertEquals(7 * 60 + 5, TriggerLogic.parseTime(" 7.5 "))
        assertNull(TriggerLogic.parseTime("25:00"))
        assertNull(TriggerLogic.parseTime("das baje"))
        assertEquals("07:05", TriggerLogic.normalizeParam("time", "7:5"))
        assertNull(TriggerLogic.normalizeParam("time", "soon"))
        assertEquals("", TriggerLogic.normalizeParam("charger_on", "whatever"))
    }

    @Test fun timeTriggerFiresOnceForItsMinute() {
        val t = Trigger(1, "time", "22:30", "wifi band karo")
        val now = at(10, 22, 30)
        assertEquals(listOf(t), TriggerLogic.due(listOf(t), "time", "${22 * 60 + 30}", now))
        assertEquals(listOf(t), TriggerLogic.due(listOf(t), "time", "${22 * 60 + 32}", now))          // tick came late
        assertTrue(TriggerLogic.due(listOf(t), "time", "${22 * 60 + 29}", now).isEmpty())
        assertTrue(TriggerLogic.due(listOf(t.copy(lastFired = now - 61_000)), "time", "${22 * 60 + 31}", now).isEmpty()) // already ran
        assertEquals(1, TriggerLogic.due(listOf(t.copy(lastFired = at(9, 22, 30))), "time", "${22 * 60 + 30}", now).size) // yesterday's run doesn't block
    }

    @Test fun eventAndMessageTriggers() {
        val charger = Trigger(1, "charger_on", "", "battery saver band karo")
        val papa = Trigger(2, "message_from", "Papa", "message padh ke sunao")
        val all = listOf(charger, papa)
        assertEquals(listOf(charger), TriggerLogic.due(all, "charger_on", "", 1_000_000))
        assertTrue(TriggerLogic.due(listOf(charger.copy(lastFired = 990_000)), "charger_on", "", 1_000_000).isEmpty()) // bounce
        assertEquals(listOf(papa), TriggerLogic.due(all, "message_from", "Papa Ji|WhatsApp", 1_000_000))
        assertTrue(TriggerLogic.due(all, "message_from", "Rahul|WhatsApp", 1_000_000).isEmpty())
        assertEquals("roz 22:30 baje -> wifi band karo", TriggerLogic.describe(Trigger(3, "time", "22:30", "wifi band karo")))
    }

    // ---- suggestions
    @Test fun suggestsWhatSirDoesAtThisTimeMostDays() {
        val history = listOf(4, 5, 7, 9).map { Used(at(it, 21, 10 + it), "open_app", "name=YouTube Studio") } +
            listOf(Used(at(9, 9, 0), "open_app", "name=Gmail"), Used(at(8, 21, 0), "make_call", "contact=Papa"))
        val s = SuggestionLogic.find(history, at(10, 21, 20), emptySet(), zone)!!
        assertEquals("open_app", s.tool)
        assertEquals("YouTube Studio", s.args["name"])
        assertEquals(4, s.days)
        assertEquals("Sir, aap aksar is waqt ye karte hain — YouTube Studio khol doon?", SuggestionLogic.phrase(s))
    }

    @Test fun noSuggestionWhenDoneTodayAlreadyAskedWrongTimeOrTooRare() {
        val base = listOf(4, 5, 7).map { Used(at(it, 21, 15), "open_app", "name=YouTube Studio") }
        assertNull(SuggestionLogic.find(base + Used(at(10, 20, 0), "open_app", "name=YouTube Studio"), at(10, 21, 20), emptySet(), zone))
        assertNull(SuggestionLogic.find(base, at(10, 21, 20), setOf("open_app|name=YouTube Studio"), zone))
        assertNull(SuggestionLogic.find(base, at(10, 14, 0), emptySet(), zone))
        assertNull(SuggestionLogic.find(base.take(2), at(10, 21, 20), emptySet(), zone))
        // never suggests calls / messages, however regular
        assertNull(SuggestionLogic.find(listOf(4, 5, 7, 9).map { Used(at(it, 21, 15), "make_call", "contact=Papa") }, at(10, 21, 20), emptySet(), zone))
    }

    @Test fun aroundMidnightStillCounts() {
        val history = listOf(Used(at(5, 23, 50), "play_music", "query=lofi"), Used(at(6, 23, 55), "play_music", "query=lofi"), Used(at(8, 0, 10), "play_music", "query=lofi"))
        assertEquals(3, SuggestionLogic.find(history, at(10, 0, 5), emptySet(), zone)!!.days)
    }

    // ---- diary
    @Test fun diaryDates() {
        val today = LocalDate.of(2026, 11, 10) // a Tuesday
        assertEquals(today, DiaryLogic.resolve("aaj", today))
        assertEquals(today.minusDays(1), DiaryLogic.resolve("Kal", today))
        assertEquals(LocalDate.of(2026, 11, 3), DiaryLogic.resolve("pichhle mangal", today))   // the Tuesday before today
        assertEquals(LocalDate.of(2026, 11, 8), DiaryLogic.resolve("sunday", today))
        assertEquals(LocalDate.of(2026, 10, 2), DiaryLogic.resolve("2026-10-02", today))
        assertNull(DiaryLogic.resolve("kabhi", today))
    }

    @Test fun diaryTextAndTrimming() {
        val actions = listOf(Used(at(10, 9, 5), "make_call", "contact=Papa"), Used(at(10, 9, 6), "tap", "id=4"), Used(at(10, 18, 0), "add_expense", "amount=250", ok = false))
        assertEquals(listOf("09:05 make_call(contact=Papa)"), DiaryLogic.lines(actions, zone))
        val plain = DiaryLogic.plain(actions, listOf("Sir ne trip plan kiya"), "total 250 rupees", zone)!!
        assertTrue("make_call" in plain && "trip plan" in plain && "250" in plain)
        assertNull(DiaryLogic.plain(emptyList(), emptyList(), null, zone))
        assertTrue("do not invent" in DiaryLogic.prompt("Tuesday, 10 November", actions, emptyList(), null, zone))
        var diary = emptyMap<String, String>()
        repeat(DiaryLogic.KEEP_DAYS + 5) { diary = DiaryLogic.put(diary, LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString(), "day $it") }
        assertEquals(DiaryLogic.KEEP_DAYS, diary.size)
        assertEquals("2026-01-06", diary.keys.first())
        assertEquals("2026-11-10", DiaryLogic.dayKey(at(10, 23, 59), zone))
    }

    @Test fun triggersAndDiarySurviveSaving() {
        val data = LifeData(triggers = listOf(Trigger(5, "time", "07:05", "mausam batao", lastFired = 99)), diary = mapOf("2026-11-10" to "Sir ne kaam kiya."))
        val back = LifeCodec.decode(LifeCodec.encode(data))
        assertEquals(data.triggers, back.triggers)
        assertEquals(data.diary, back.diary)
    }
}
