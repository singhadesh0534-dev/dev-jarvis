package com.adesh.devjarvis.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class ToolArgsTest {

    private fun args(json: String) = ToolArgs(Json.parseToJsonElement(json).jsonObject)
    private val now = LocalDateTime.of(2026, 10, 31, 20, 0)

    @Test fun intFromManyShapes() {
        val a = args("""{"a":6,"b":"7","c":6.6,"d":" 08 ","e":"six"}""")
        assertEquals(6, a.int("a"))
        assertEquals(7, a.int("b"))
        assertEquals(7, a.int("c"))
        assertEquals(8, a.int("d"))
        assertNull(a.int("e"))
        assertNull(a.int("missing"))
    }

    @Test fun stringTrimsAndBlankIsNull() {
        val a = args("""{"a":"  Mummy ","b":"  ","n":42}""")
        assertEquals("Mummy", a.string("a"))
        assertNull(a.string("b"))
        assertEquals("42", a.string("n"))
        assertThrows(ToolArgError::class.java) { a.requireString("b") }
    }

    @Test fun boolAndEnum() {
        val a = args("""{"on":"yes","off":false,"mode":"UP"}""")
        assertEquals(true, a.bool("on"))
        assertEquals(false, a.bool("off"))
        assertEquals("up", a.enum("mode", listOf("up", "down")))
        assertNull(a.enum("mode", listOf("left")))
    }

    @Test fun dateTimes() {
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 30), ToolArgs.parseDateTime("2026-11-01T06:30", now))
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 30), ToolArgs.parseDateTime("2026-11-01 06:30", now))
        // Time already passed today -> tomorrow.
        assertEquals(LocalDateTime.of(2026, 11, 1, 6, 0), ToolArgs.parseDateTime("6:00", now))
        assertEquals(LocalDateTime.of(2026, 10, 31, 21, 15), ToolArgs.parseDateTime("9:15 pm", now))
        assertEquals(LocalDateTime.of(2026, 10, 31, 22, 0), ToolArgs.parseDateTime("10 PM", now))
        assertNull(ToolArgs.parseDateTime("kal subah", now))
    }

    @Test fun durations() {
        assertEquals(90, ToolArgs.parseDuration("90"))
        assertEquals(300, ToolArgs.parseDuration("5m"))
        assertEquals(300, ToolArgs.parseDuration("5 minutes"))
        assertEquals(5400, ToolArgs.parseDuration("1h30m"))
        assertEquals(300, ToolArgs.parseDuration("PT5M"))
        assertEquals(300, ToolArgs.parseDuration("05:00"))
        assertEquals(3725, ToolArgs.parseDuration("1:02:05"))
        assertEquals(45, ToolArgs.parseDuration("45 sec"))
        assertNull(ToolArgs.parseDuration("thodi der"))
        assertEquals(120, args("""{"d":120}""").durationSeconds("d"))
    }
}
