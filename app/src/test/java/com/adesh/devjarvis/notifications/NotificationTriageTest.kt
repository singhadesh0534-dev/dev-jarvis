package com.adesh.devjarvis.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTriageTest {
    private fun i(app: String, title: String, text: String, t: Long = 1_000, reply: Boolean = true) =
        NotificationTriage.Item("$app$title$text$t", app, title, text, t, reply)

    @Test fun levels() {
        val c = listOf("Mummy"); val a = listOf("WhatsApp")
        assertEquals(NotificationTriage.Level.IMPORTANT, NotificationTriage.level(i("WhatsApp", "Mummy", "khana kha liya?"), c, a))
        assertEquals(NotificationTriage.Level.IMPORTANT, NotificationTriage.level(i("WhatsApp", "Rahul", "urgent call karo"), c, a))
        assertEquals(NotificationTriage.Level.LOW, NotificationTriage.level(i("Amazon", "Big sale", "50% off today", reply = false), c, a))
        assertEquals(NotificationTriage.Level.NORMAL, NotificationTriage.level(i("WhatsApp", "Rahul", "kal milte hain"), c, a))
    }

    @Test fun burstAndSummary() {
        val now = 10 * 60_000L
        val list = listOf(i("WhatsApp", "Rahul", "hi", now - 60_000), i("WhatsApp", "Rahul", "suno", now - 30_000), i("WhatsApp", "Rahul", "?", now - 1_000))
        assertTrue(NotificationTriage.isBurst(list, "Rahul", now))
        val s = NotificationTriage.summary(list + i("YouTube", "New video", "x", reply = false), emptyList(), emptyList(), 2)
        assertTrue(s, s.contains("Rahul on WhatsApp (3)"))
        assertTrue(s, s.contains("SKIPPED (offers / feeds / system): YouTube 1"))
        assertTrue(s, s.contains("2 private"))
    }
}
