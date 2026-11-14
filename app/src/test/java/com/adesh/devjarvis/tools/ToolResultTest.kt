package com.adesh.devjarvis.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultTest {
    @Test fun finishedWorkIsDone() {
        assertTrue(ToolResult.ok("Wi-Fi off").done)
    }

    @Test fun halfDoneIsNeverDone() {
        val r = ToolResult.partial("NOT done: settings opened, Sir must tap")
        assertTrue(r.ok)
        assertFalse(r.done)
        assertFalse(ToolResult.ok("x").copy(partial = true).done)
    }

    @Test fun failureIsNotDone() {
        assertFalse(ToolResult.fail("no").done)
    }
}
