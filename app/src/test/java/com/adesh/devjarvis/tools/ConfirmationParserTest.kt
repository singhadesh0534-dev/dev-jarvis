package com.adesh.devjarvis.tools

import com.adesh.devjarvis.tools.ConfirmationParser.Answer
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationParserTest {
    @Test fun yes() {
        listOf("Haan", "haan kar do", "Yes please", "ok", "theek hai bhej do", "kar do na", "Ji haan", "go ahead")
            .forEach { assertEquals(it, Answer.YES, ConfirmationParser.parse(it)) }
    }

    @Test fun no() {
        listOf("nahi", "No", "rehne do", "cancel", "mat karo", "haan nahi", "don't send", "abhi nahi yaar")
            .forEach { assertEquals(it, Answer.NO, ConfirmationParser.parse(it)) }
    }

    @Test fun unclear() {
        listOf("", null, "kya?", "Mumbai mein mausam").forEach { assertEquals(it.toString(), Answer.UNCLEAR, ConfirmationParser.parse(it)) }
    }
}
