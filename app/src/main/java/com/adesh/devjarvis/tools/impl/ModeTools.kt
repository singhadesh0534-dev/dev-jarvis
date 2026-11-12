package com.adesh.devjarvis.tools.impl

import android.os.Build
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.brain.SkillWriter
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
    private val learned: com.adesh.devjarvis.brain.LearnedSkills,
    // Providers: JarvisBrain -> ToolRegistry -> ModeTools would otherwise be a cycle.
    private val brain: javax.inject.Provider<com.adesh.devjarvis.brain.JarvisBrain>,
    private val registry: javax.inject.Provider<com.adesh.devjarvis.tools.ToolRegistry>,
) {
    private val liveCamera = object : SimpleTool(
        "live_camera",
        "Start or stop LIVE camera vision: you keep seeing through the phone camera (about one frame a second) and can talk about " +
            "what you see. Start ONLY when Sir explicitly asks (\"camera se live dekho\", \"dekhte raho\", \"camera on karke batao\", " +
            "\"video pe dekho\"); never on your own. " +
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
            val before = eyes.framesSent
            val outcome = launcher.launch(eyes.startCamera(front = args.enum("camera", listOf("back", "front")) == "front"), "JARVIS live camera")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) {
                eyes.stopCamera()
                return ToolResult.fail("camera screen couldn't open")
            }
            if (outcome == ActivityLauncher.Outcome.NEEDS_TAP) {
                return ToolResult.ok(
                    "Sir must tap the JARVIS notification to open the camera; tell him that. Once it opens, frames arrive about " +
                        "once a second. When he says stop / band karo, call this tool with on=false.",
                )
            }
            // Answer only once the first picture has really arrived, so the reply is about what the camera shows.
            val seeing = eyes.awaitFrame(before)
            return ToolResult.ok(
                (if (seeing) "live camera ON and the first frames have arrived: you can see through the camera now. "
                else "live camera is opening; frames will arrive in a moment (if you still see nothing, say so plainly). ") +
                    "A new frame comes about once a second. Answer what Sir asks about what you see, in one or two lines; don't " +
                    "narrate everything unprompted. When he says stop / band karo, call this tool with on=false.",
            )
        }
    }

    private val screenWatch = object : SimpleTool(
        "screen_watch",
        "Start or stop LIVE screen watching: you keep seeing Sir's phone screen (a frame every few seconds) to guide or help him. " +
            "Start ONLY when Sir explicitly asks (\"screen share karo\", \"meri screen dekho / dekhte raho\", \"screen pe guide karo\", " +
            "\"screen dekh ke ye kar do\"); never on your own. " +
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
            val before = eyes.framesSent
            eyes.startScreenWatch()
            val seeing = eyes.awaitFrame(before, 3_000L)
            return ToolResult.ok(
                "screen watch ON: you get Sir's screen every few seconds (password, payment and OTP screens are skipped). " +
                    (if (seeing) "The first picture has arrived. "
                    else "No picture yet: JARVIS's own screen is never sent, so tell Sir to open the app he wants help with. ") +
                    "Every tappable thing in the picture carries a yellow number: to do something for him use that number straight " +
                    "away with tap / type_text / long_press (no read_screen needed first); each action returns the new screen, and " +
                    "the next picture shows the new numbers — never reuse an old number. To guide him instead, say what to press " +
                    "in words. Don't read the whole screen aloud. When he says stop / band karo, call this tool with on=false.",
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
        "Get the exact working method for a kind of request BEFORE you answer or act on it. Call it whenever the request fits a " +
            "skill, even if you think you know how. Pass a skill id, or describe the request in a few English words and the best " +
            "matching skill is found (the id says what it is for). Core skills: " + Skills.index() + ". Plus a library of ${Skills.ALL.size - Skills.CORE.size} more, found by " +
            "words, in these areas: " + Skills.areas() + ".",
        params { string("skill", "Skill id, or a few words describing the request", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val found = Skills.search(args.requireString("skill"))
            val best = found.firstOrNull()
                ?: return ToolResult.fail(
                    "no skill for this yet. If it is a task with several steps on the phone, call write_skill with a short " +
                        "description of this kind of request (you will get a tested playbook and keep it); for anything simple, " +
                        "answer with your own best method.",
                )
            val others = found.drop(1).take(3)
            return ToolResult.ok(
                Skills.playbook(best) +
                    if (others.isEmpty()) "" else "\n(If this does not fit, related skills: ${others.joinToString { it.id }})",
            )
        }
    }

    private val writeSkill = object : SimpleTool(
        "write_skill",
        "Write a new skill (a tested step-by-step playbook) for a kind of request, think it through with the strongest " +
            "reasoning, save it for ever and get it back to follow now. Use it when use_skill has nothing for a task with " +
            "several steps, and when Sir corrects how you did a task (pass his words as lesson; the skill for that kind of " +
            "request is improved). action=list shows the skills you wrote, action=delete removes one.",
        params {
            string("action", "write (default) | list | delete", enum = listOf("write", "list", "delete"))
            string("task", "The kind of request, in a few English words, e.g. \"order groceries on Blinkit\"")
            string("lesson", "Sir's correction or wish, in his words, if any")
            string("skill", "Id of an existing skill to improve or delete")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            when (args.enum("action", listOf("write", "list", "delete")) ?: "write") {
                "list" -> return ToolResult.ok(
                    learned.all().takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.id}: ${it.whenToUse}" }
                        ?: "no skills written yet",
                )
                "delete" -> {
                    val id = args.string("skill") ?: return ToolResult.fail("which skill? give its id")
                    return if (learned.delete(id)) ToolResult.ok("skill $id deleted") else ToolResult.fail("no skill I wrote is called $id")
                }
            }
            val task = args.string("task") ?: args.string("skill")?.replace('_', ' ')
                ?: return ToolResult.fail("describe the kind of request in a few words (task)")
            val existing = args.string("skill")?.let { Skills.find(it) } ?: Skills.search(task, 1).firstOrNull()
                .takeIf { !args.string("lesson").isNullOrBlank() }
            val names = registry.get().tools.map { it.name }
            val raw = brain.get().complete(
                SkillWriter.system(names), SkillWriter.request(task, args.string("lesson"), existing),
                json = true, thinking = "high",
            ) ?: return ToolResult.fail("couldn't write the skill now (the brain is offline or at its limit); do the task with your own best method")
            val skill = SkillWriter.parse(raw, keepId = existing?.id)
                ?: return ToolResult.fail("the skill came out unusable; do the task with your own best method")
            learned.save(skill)
            return ToolResult.ok(
                (if (existing != null) "Improved skill '${skill.id}' and saved it. " else "New skill '${skill.id}' written and saved. ") +
                    "Tell Sir in a few words that you have learned it, then follow it now:\n" + Skills.playbook(skill),
            )
        }
    }

    val tools: List<JarvisTool> = listOf(interpreter, liveCamera, screenWatch, useSkill, writeSkill)
}
