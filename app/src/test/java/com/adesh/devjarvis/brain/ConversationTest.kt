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
}
