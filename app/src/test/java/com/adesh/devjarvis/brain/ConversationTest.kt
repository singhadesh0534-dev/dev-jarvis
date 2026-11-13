package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTest {
    @Test
    fun keepsOnlyLastTurns() {
        val c = Conversation(maxTurns = 2)
        repeat(5) { c.addExchange("q$it", "a$it") }
        assertEquals(listOf("q3", "a3", "q4", "a4"), c.snapshot().map { it.text })
        assertEquals(Role.USER, c.snapshot().first().role)
    }

    @Test fun survivesARestart() {
        val a = Conversation(maxTurns = 2)
        a.addExchange("hello\tthere", "Ji Sir,\nbataiye")
        a.addExchange("kal kya hai", "Meeting 10 baje")
        val b = Conversation(maxTurns = 2)
        b.restore(a.encode())
        assertEquals(a.snapshot().map { it.role }, b.snapshot().map { it.role })
        assertEquals("hello there", b.snapshot().first().text)
        assertEquals("Ji Sir, bataiye", b.snapshot()[1].text)
        b.restore("garbage\nX")
        assertEquals(0, b.snapshot().size)
    }
}
