package com.adesh.devjarvis.screen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.core.AppVisibility
import com.adesh.devjarvis.life.LifeMath
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.tools.ActivityLauncher
import com.adesh.devjarvis.tools.FuzzyMatcher
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Ek baar dikha do, hamesha yaad": Sir kaam haath se ek baar karte hain, JARVIS kadam likh leta hai
 * (sirf jab tak recording chalu hai), aur baad mein naam lene par wahi kadam dohrata hai.
 */
@Singleton
class MacroTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LifeStore,
    private val launcher: ActivityLauncher,
) {
    private fun service(): JarvisAccessibilityService? = JarvisAccessibilityService.get()
    private fun needsAccessibility() = ToolResult.fail("needs JARVIS Accessibility turned on in Setup")

    private fun appName(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    private fun find(name: String): Pair<String, List<MacroStep>>? {
        val macros = store.get().macros
        val key = LifeMath.key(name)
        macros[key]?.let { return key to it }
        val best = FuzzyMatcher.rank(key, macros.keys.toList(), threshold = 0.75) { listOf(it) }.firstOrNull()?.item ?: return null
        return best to macros.getValue(best)
    }

    private val teachStart = object : SimpleTool(
        "teach_start",
        "Start learning a task by watching: Sir will now do it by hand once and JARVIS records the taps and typing. Use when he says " +
            "\"main dikhata hoon\", \"ye seekh lo\", \"record karo\". Nothing is recorded in banking / payment apps or password boxes.",
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            svc.startRecording()
            return ToolResult.ok(
                "recording started (stops by itself after 5 minutes). Say only: \"Dikhaiye Sir. Kaam poora karke mujhe bulaiye aur " +
                    "uska naam bata dijiye.\" Then stay silent.",
                endsConversation = true,
            )
        }
    }

    private val teachSave = object : SimpleTool(
        "teach_save",
        "Stop the recording and save what Sir just showed under a name, so it can be repeated by voice. Use when he comes back and says " +
            "\"ho gaya\", \"isko ... naam se save karo\". Ask him for a name first if he gave none.",
        params { string("name", "Name Sir wants for this task, e.g. \"office report\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val name = LifeMath.key(args.requireString("name"))
            if (name.isEmpty()) return ToolResult.fail("the task needs a name")
            val steps = svc.stopRecording()
            if (steps.isEmpty()) {
                return ToolResult.fail("nothing was recorded — recording was not running, it timed out, or Sir only used apps JARVIS never records (banking / payment)")
            }
            store.update { it.copy(macros = it.macros + (name to steps)) }
            return ToolResult.ok("saved '$name' — ${steps.size} steps: ${MacroLogic.describe(steps, ::appName)}. Sir can now just say its name.")
        }
    }

    private val runTaught = object : SimpleTool(
        "run_taught",
        "Repeat a task Sir taught earlier by showing it (see the list of taught tasks). Use when he says its name.",
        params { string("name", "Name of the taught task", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val (name, steps) = find(args.requireString("name"))
                ?: return ToolResult.fail("no taught task like '${args.requireString("name")}'. Taught: ${store.get().macros.keys.joinToString().ifEmpty { "none" }}")
            if (AppVisibility.isForeground) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                delay(800)
            }
            steps.forEachIndexed { index, step ->
                val where = "step ${index + 1} of ${steps.size}"
                if (step.kind == "open") {
                    val intent = context.packageManager.getLaunchIntentForPackage(step.app)
                        ?: return ToolResult.fail("$where: ${appName(step.app)} is not installed any more")
                    if (launcher.launch(intent, "Open ${appName(step.app)}") != ActivityLauncher.Outcome.LAUNCHED) {
                        return ToolResult.fail("$where: Android would not let me open ${appName(step.app)} from the background — Sir should open JARVIS once and try again")
                    }
                    delay(1_900)
                    return@forEachIndexed
                }
                var element: ScreenElement? = null
                for (attempt in 0 until 5) {
                    val snap = svc.snapshot()
                    if (snap != null) {
                        (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let {
                            return ToolResult.fail("$where: stopped for safety (${it.reason}). Sir should finish this part himself.")
                        }
                        element = MacroLogic.locate(snap, step)
                        if (element != null) break
                    }
                    if (attempt == 2) svc.scroll(true) // perhaps it is further down today
                    delay(700)
                }
                val el = element ?: return ToolResult.fail(
                    "$where: couldn't find ${if (step.kind == "type") "the text box" else "'${step.label.ifBlank { step.viewId.substringAfterLast('/') }}'"} " +
                        "— the app looks different from when Sir showed it. Finish with read_screen and tap, or ask Sir to show it again.",
                )
                val done = when (step.kind) {
                    "type" -> svc.type(el.id, step.text)
                    "long_press" -> svc.longPress(el.id)
                    else -> {
                        when (FinalActionDetector.classify(el.label.ifBlank { step.label })) {
                            FinalActionDetector.Kind.FORBIDDEN -> return ToolResult.fail("$where: '${el.label}' involves money — not doing that")
                            FinalActionDetector.Kind.NEEDS_CONFIRMATION ->
                                if (!ctx.confirm("Sir, '${el.label}' dabaun?")) return ToolResult.ok("stopped before '${el.label}' — Sir said no")
                            FinalActionDetector.Kind.NORMAL -> Unit
                        }
                        svc.tap(el.id)
                    }
                }
                if (!done) return ToolResult.fail("$where: the ${step.kind} did not register")
                delay(1_000)
            }
            return ToolResult.ok("did '$name' (${steps.size} steps). Use read_screen if Sir asks what is on the screen now.")
        }
    }

    private val taughtTasks = object : SimpleTool(
        "taught_tasks", "List the tasks Sir taught by showing, or delete one.",
        params {
            string("action", "list | delete", required = true, enum = listOf("list", "delete"))
            string("name", "Task to delete")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (args.enum("action", listOf("list", "delete")) == "delete") {
                val (name, _) = find(args.string("name").orEmpty()) ?: return ToolResult.fail("no taught task with that name")
                store.update { it.copy(macros = it.macros - name) }
                return ToolResult.ok("forgot how to do '$name'")
            }
            val all = store.get().macros
            return ToolResult.ok(if (all.isEmpty()) "nothing taught yet" else all.entries.joinToString(" | ") { "${it.key}: ${MacroLogic.describe(it.value, ::appName)}" })
        }
    }

    val tools: List<JarvisTool> = listOf(teachStart, teachSave, runTaught, taughtTasks)
}
