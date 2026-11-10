package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.core.ConversationMode
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import javax.inject.Inject
import javax.inject.Singleton

/** Conversation modes: interpreter. (Live vision / screen watch tools live in VisionTools.) */
@Singleton
class ModeTools @Inject constructor(private val modes: ConversationMode) {

    private val interpreter = object : SimpleTool(
        "interpreter_mode",
        "Start or stop interpreter (dubhashiya) mode: you translate everything said between two languages, back and forth, " +
            "and the conversation stays open. Start when Sir says \"translate mode / dubhashiya ban jao\"; stop when he says stop.",
        params {
            boolean("on", "true = start, false = stop", required = true)
            string("language_a", "Sir's language, e.g. Hindi")
            string("language_b", "The other person's language, e.g. English, Tamil, Arabic")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val on = args.bool("on") ?: true
            modes.interpreter = on
            if (!on) return ToolResult.ok("interpreter mode off — back to normal")
            val a = args.string("language_a") ?: "Hindi"
            val b = args.string("language_b") ?: "English"
            return ToolResult.ok(
                "interpreter mode ON ($a <-> $b). From now: whatever you hear in $a, say it in $b; whatever you hear in $b, " +
                    "say it in $a. Translate only — no comments, no answers of your own — until Sir says stop, then call this tool with on=false.",
            )
        }
    }

    val tools: List<JarvisTool> = listOf(interpreter)
}
