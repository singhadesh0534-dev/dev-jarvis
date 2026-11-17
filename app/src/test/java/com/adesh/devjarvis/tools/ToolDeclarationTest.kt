package com.adesh.devjarvis.tools

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDeclarationTest {
    private fun tool(p: JsonObject?) = object : SimpleTool("t", "test", p) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext) = ToolResult.ok("x")
    }

    /** 1.0.165-1.0.170: reminder_done had params { } and Gemini refused every Live session and request. */
    @Test fun emptyParamsAreNotSent() {
        assertFalse("parameters" in tool(params { }).declaration())
        assertFalse("parameters" in tool(null).declaration())
    }

    @Test fun realParamsAreSent() {
        val d = tool(params { string("q", "question", required = true) }).declaration()
        assertTrue("parameters" in d)
        assertTrue((d["parameters"] as JsonObject)["properties"].toString().contains("\"q\""))
    }
}
