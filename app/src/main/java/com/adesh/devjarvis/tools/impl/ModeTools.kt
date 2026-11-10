package com.adesh.devjarvis.tools.impl

import android.os.Build
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.brain.Skills
import com.adesh.devjarvis.core.ConversationMode
import com.adesh.devjarvis.tools.ActivityLauncher
import com.adesh.devjarvis.vision.LiveEyes
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import javax.inject.Inject
import javax.inject.Singleton

/** Conversation modes: interpreter, live camera, live screen watch. The last two start only on Sir's command. */
@Singleton
class ModeTools @Inject constructor(
    private val modes: ConversationMode,
    private val eyes: LiveEyes,
    private val launcher: ActivityLauncher,
) {
    private val liveCamera = object : SimpleTool(
        "live_camera",
        "Start or stop LIVE camera vision: you keep seeing through the phone camera (about one frame a second) and can talk about " +
            "what you see. Start ONLY when Sir explicitly asks (\"camera se live dekho\", \"dekhte raho\"); never on your own. " +
            "For a single question about one thing use `look` instead. Stop when Sir says band karo.",
        params {
            boolean("on", "true = start, false = stop", required = true)
            string("camera", "back (default) | front", enum = listOf("back", "front"))
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (args.bool("on") == false) {
                eyes.stopCamera()
                return ToolResult.ok("live camera off")
            }
            if (!eyes.liveSessionOpen) return ToolResult.fail("live camera works only in Live voice mode — use the look tool for one photo instead")
            if (!eyes.hasCameraPermission()) return ToolResult.fail("camera permission is off — Sir can allow it in JARVIS Setup")
            val outcome = launcher.launch(eyes.startCamera(front = args.enum("camera", listOf("back", "front")) == "front"), "JARVIS live camera")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) {
                eyes.stopCamera()
                return ToolResult.fail("camera screen couldn't open")
            }
            return ToolResult.ok(
                (if (outcome == ActivityLauncher.Outcome.NEEDS_TAP) "Sir must tap the JARVIS notification to open the camera. " else "") +
                    "live camera ON: frames now arrive about once a second. Answer what Sir asks about what you see; don't narrate " +
                    "everything unprompted. When he says stop / band karo, call this tool with on=false.",
            )
        }
    }

    private val screenWatch = object : SimpleTool(
        "screen_watch",
        "Start or stop LIVE screen watching: you keep seeing Sir's phone screen (a frame every few seconds) to guide or help him. " +
            "Start ONLY when Sir explicitly asks (\"meri screen dekhte raho\", \"screen pe guide karo\"); never on your own. " +
            "Password, payment and OTP screens are never sent. Stop when Sir says band karo.",
        params { boolean("on", "true = start, false = stop", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (args.bool("on") == false) {
                eyes.stopScreenWatch()
                return ToolResult.ok("screen watch off")
            }
            if (!eyes.liveSessionOpen) return ToolResult.fail("screen watch works only in Live voice mode — use look_at_screen for one look instead")
            if (!JarvisAccessibilityService.isConnected) return ToolResult.fail("needs JARVIS Accessibility turned on in Setup")
            if (Build.VERSION.SDK_INT < 30) return ToolResult.fail("screen watch needs Android 11 or newer")
            eyes.startScreenWatch()
            return ToolResult.ok(
                "screen watch ON: you now get Sir's screen every few seconds (private screens are skipped). Help with what he asks; " +
                    "don't read everything aloud. When he says stop / band karo, call this tool with on=false.",
            )
        }
    }

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

    private val useSkill = object : SimpleTool(
        "use_skill",
        "Get the exact working method for a kind of request BEFORE you answer or act on it. Call it whenever the request fits one " +
            "of these, even if you think you know how: " + Skills.index(),
        params { string("skill", "Skill id", required = true, enum = Skills.ALL.map { it.id }) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val skill = Skills.find(args.requireString("skill"))
                ?: return ToolResult.fail("no such skill; available: ${Skills.ALL.joinToString { it.id }}")
            return ToolResult.ok(Skills.playbook(skill))
        }
    }

    val tools: List<JarvisTool> = listOf(interpreter, liveCamera, screenWatch, useSkill)
}
