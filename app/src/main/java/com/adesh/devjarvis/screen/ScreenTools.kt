package com.adesh.devjarvis.screen

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.core.AppVisibility
import com.adesh.devjarvis.tools.ActivityLauncher
import com.adesh.devjarvis.tools.AppRepository
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import com.adesh.devjarvis.vision.CameraCaptureActivity
import com.adesh.devjarvis.vision.CaptureBroker
import com.adesh.devjarvis.vision.ImageUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Aankhein (camera, screenshot) aur haath (tap, type, scroll, screen agent). */
@Singleton
class ScreenTools @Inject constructor(
    @ApplicationContext private val context: Context,
    // Provider: JarvisBrain -> ToolRegistry -> ScreenTools -> JarvisBrain would be a cycle.
    private val brainProvider: Provider<JarvisBrain>,
    private val launcher: ActivityLauncher,
    private val apps: AppRepository,
) {
    private val _progress = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Screen-agent step lines for the HUD transcript. */
    val progress: SharedFlow<String> = _progress.asSharedFlow()

    private val brain: JarvisBrain get() = brainProvider.get()

    private fun service(): JarvisAccessibilityService? = JarvisAccessibilityService.get()
    private fun needsAccessibility() = ToolResult.fail("needs JARVIS Accessibility turned on in Setup")

    /** Snapshot of the app Sir is looking at — never JARVIS's own HUD. */
    private suspend fun currentScreen(svc: JarvisAccessibilityService): ScreenSnapshot? {
        var snap = svc.snapshot()
        if (snap?.packageName == context.packageName) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(700)
            snap = svc.snapshot()
        }
        return snap
    }

    // ---------------------------------------------------------------- eyes

    private val look = object : SimpleTool(
        "look", "Use the phone camera to see the real world and answer Sir's question (\"ye kya hai?\", \"isme kya likha hai?\").",
        params {
            string("question", "What Sir wants to know about what he's pointing at", required = true)
            string("camera", "back (default) | front", enum = listOf("back", "front"))
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return ToolResult.fail("camera permission is off — Sir can allow it in JARVIS Setup")
            }
            val result = CompletableDeferred<ByteArray?>()
            CaptureBroker.pending?.complete(null)
            CaptureBroker.pending = result
            val intent = Intent(context, CameraCaptureActivity::class.java)
                .putExtra(CameraCaptureActivity.EXTRA_LENS, args.enum("camera", listOf("back", "front")) ?: "back")
            val outcome = launcher.launch(intent, "JARVIS camera")
            if (outcome == ActivityLauncher.Outcome.NOT_FOUND) return ToolResult.fail("camera screen couldn't open")
            // If Android needed a tap, Sir has a notification; give him time.
            val timeout = if (outcome == ActivityLauncher.Outcome.NEEDS_TAP) 45_000L else 15_000L
            val jpeg = withTimeoutOrNull(timeout) { result.await() } ?: return ToolResult.fail("couldn't take a photo")
            val answer = brain.see(jpeg, args.requireString("question")) ?: return ToolResult.fail("the photo was taken but the vision model did not answer (all Gemini keys at their limit, or the network dropped). Tell Sir exactly that and offer to try again in a minute.")
            return ToolResult.ok(answer)
        }
    }

    private val lookAtScreen = object : SimpleTool(
        "look_at_screen",
        "Look at the current screen as a picture (screenshot with a yellow number on every tappable thing) and answer a question " +
            "about it. Use it for icons, images, colours, layout, or to find the number of an icon that read_screen shows as (no label).",
        params { string("question", "e.g. \"is screen pe kya hai?\" or \"search icon ka number kya hai?\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val snap = currentScreen(svc)
            if (snap != null) {
                (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let {
                    return ToolResult.fail("private screen (${it.reason}) — not looking")
                }
            }
            val bmp = svc.screenshot()
                ?: return snap?.let { ToolResult.ok("no screenshot on this phone; screen as text:\n" + ScreenFormatter.compact(it, 60)) }
                    ?: ToolResult.fail("couldn't capture the screen (needs Android 11+)")
            val marked = runCatching { ImageUtil.withMarks(bmp, svc.marks()) }.getOrDefault(bmp)
            val jpeg = ImageUtil.toJpeg(marked, maxSide = 1280, quality = 75)
            if (marked !== bmp) marked.recycle()
            bmp.recycle()
            val answer = brain.see(jpeg, args.requireString("question") + MARKS_NOTE)
                ?: return snap?.let { ToolResult.ok("the vision model did not answer; screen as text:\n" + ScreenFormatter.compact(it, 60)) }
                    ?: ToolResult.fail("vision unavailable")
            return ToolResult.ok(answer)
        }
    }

    private val readScreen = object : SimpleTool(
        "read_screen",
        "Read the current screen as a numbered list of everything on it: [number] kind \"label\". Then act with the number: " +
            "tap(id), long_press(id), type_text(id). Fast, no image. Things shown as (no label) are icons: their place is given " +
            "(@top-right...), or use look_at_screen to see which is which.",
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen — try look_at_screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("private screen (${it.reason}) — not reading") }
            return ToolResult.ok(ScreenFormatter.compact(snap, 70))
        }
    }

    // ---------------------------------------------------------------- hands (single steps)

    /**
     * After every action: wait for the UI, read the screen again and hand it back. So each step is checked
     * ("did it work?") without a second request, and the next step uses fresh numbers.
     */
    private suspend fun observe(svc: JarvisAccessibilityService, did: String, settleMs: Long = 700): ToolResult {
        delay(settleMs)
        val snap = svc.snapshot() ?: return ToolResult.ok("$did. (The new screen can't be read as text; use look_at_screen to check.)")
        (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let {
            return ToolResult.ok("$did. The screen now is private (${it.reason}) — stop here and let Sir do this part himself.")
        }
        return ToolResult.ok("$did. Check that it did what you wanted — screen now:\n" + ScreenFormatter.compact(snap, 45))
    }

    private fun notThere(snap: ScreenSnapshot, id: Int?, label: String?): ToolResult = ToolResult.fail(
        "no ${label?.let { "'$it'" } ?: "number $id"} on this screen. Don't repeat the same tap: pick from what IS here, scroll, " +
            "or use look_at_screen. Screen:\n" + ScreenFormatter.compact(snap, 45),
    )

    private val tap = object : SimpleTool(
        "tap",
        "Tap something on the current screen. Best: its number from read_screen or from the last action's \"screen now\" list " +
            "(add the label too as a cross-check). Or just its visible label. Returns the screen after the tap.",
        params {
            integer("id", "Element number")
            string("text", "Visible label, e.g. Search, Rahul, Like")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val id = args.int("id")
            val label = args.string("text")
            if (id == null && label == null) return ToolResult.fail("tap what? give the number or the label")
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen — try look_at_screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("stopped: ${it.reason}") }
            val el = AgentProtocol.pick(snap, id, label) ?: return notThere(snap, id, label)
            when (FinalActionDetector.classify(el.label)) {
                FinalActionDetector.Kind.FORBIDDEN -> return ToolResult.fail("I don't tap payment/purchase buttons")
                FinalActionDetector.Kind.NEEDS_CONFIRMATION ->
                    if (!ctx.confirm("Sir, '${el.label}' dabaun?")) return ToolResult.ok("cancelled — Sir said no")
                FinalActionDetector.Kind.NORMAL -> Unit
            }
            return if (svc.tap(el.id)) observe(svc, "tapped ${el.label.ifBlank { "number ${el.id}" }}") else ToolResult.fail("the tap didn't register")
        }
    }

    private val typeText = object : SimpleTool(
        "type_text",
        "Type into an input box on screen (the focused one, or give its number). submit=true presses Enter / Search afterwards. " +
            "Never for passwords/OTPs. Returns the screen after typing.",
        params {
            string("text", "Text to type", required = true)
            integer("id", "Number of the input box (optional)")
            boolean("submit", "true = press Enter / Search after typing")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            var snap: ScreenSnapshot = currentScreen(svc) ?: return ToolResult.fail("can't read this screen — try look_at_screen")
            // A search box often appears a moment after its icon was tapped: wait for it instead of failing.
            for (i in 0 until 4) {
                if (snap.elements.any { it.editable }) break
                delay(450)
                svc.snapshot()?.let { snap = it }
            }
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("stopped: ${it.reason}") }
            if (snap.elements.none { it.editable }) {
                return ToolResult.fail(
                    "there is no text box on this screen yet — tap the search icon or the field first (it may be an icon with no label). " +
                        "Screen:\n" + ScreenFormatter.compact(snap, 45),
                )
            }
            val text = args.requireString("text")
            val submit = args.bool("submit") == true
            return if (svc.type(args.int("id"), text, submit)) {
                observe(svc, "typed '${text.take(40)}'" + if (submit) " and pressed enter" else "", settleMs = if (submit) 1_600 else 600)
            } else {
                ToolResult.fail("couldn't type into that box")
            }
        }
    }

    private val scroll = object : SimpleTool(
        "scroll", "Scroll the current screen. Returns the screen after scrolling.",
        params { string("direction", "down | up", required = true, enum = listOf("down", "up")) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            currentScreen(svc)
            val down = args.enum("direction", listOf("down", "up")) != "up"
            return if (svc.scroll(down)) observe(svc, "scrolled ${if (down) "down" else "up"}") else ToolResult.fail("nothing to scroll here")
        }
    }

    private val swipe = object : SimpleTool(
        "swipe", "Swipe the current screen: left = next (next reel / page / photo), right = previous, up / down = move the page. Returns the screen after.",
        params { string("direction", "left | right | up | down", required = true, enum = AgentProtocol.SWIPE_DIRECTIONS) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            currentScreen(svc)
            val dir = args.enum("direction", AgentProtocol.SWIPE_DIRECTIONS) ?: return ToolResult.fail("direction must be left, right, up or down")
            return if (svc.swipe(dir)) observe(svc, "swiped $dir") else ToolResult.fail("swipe didn't work")
        }
    }

    private val longPress = object : SimpleTool(
        "long_press", "Long-press an item on the current screen, by number or label (to select it or open its menu). Returns the screen after.",
        params {
            integer("id", "Element number")
            string("text", "Visible label of the item")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val id = args.int("id")
            val label = args.string("text")
            if (id == null && label == null) return ToolResult.fail("long-press what? give the number or the label")
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen — try look_at_screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("stopped: ${it.reason}") }
            val el = AgentProtocol.pick(snap, id, label) ?: return notThere(snap, id, label)
            return if (svc.longPress(el.id)) observe(svc, "long-pressed ${el.label.ifBlank { "number ${el.id}" }}") else ToolResult.fail("long-press didn't work")
        }
    }

    private val goBack = object : SimpleTool("go_back", "Press Back. Returns the screen after.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            return if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) observe(svc, "went back") else ToolResult.fail("back didn't work")
        }
    }

    private val goHome = object : SimpleTool("go_home", "Go to the home screen.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_HOME)) ToolResult.ok("home screen") else needsAccessibility()
    }

    // ---------------------------------------------------------------- screen agent

    private val doTask = object : SimpleTool(
        "do_task_on_screen",
        "Do a multi-step job inside other apps by reading the screen and tapping/typing/swiping (max 25 steps): " +
            "e.g. send a message in an app, search something, change a setting, select and delete or hide photos. " +
            "Stops on password/payment screens; asks Sir before the final send/post. Only on Sir's explicit request.",
        params { string("goal", "Clear goal, e.g. \"WhatsApp pe Rahul ko 'kal milte hain' bhejo\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val goal = args.requireString("goal")
            val history = mutableListOf<String>()
            if (AppVisibility.isForeground) {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                delay(800)
            }
            for (step in 1..AgentProtocol.MAX_STEPS) {
                val snap = svc.snapshot() ?: run { delay(600); svc.snapshot() } ?: return ToolResult.fail("can't read the screen")
                (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let {
                    return ToolResult.fail("stopped for safety: ${it.reason}. Sir should finish this part himself.")
                }
                val raw = brain.complete(AgentProtocol.systemPrompt(), AgentProtocol.userPrompt(goal, history, ScreenFormatter.compact(snap)), json = true)
                val action = AgentProtocol.parse(raw) ?: return ToolResult.fail("lost my way on step $step")
                val desc = AgentProtocol.describe(action, snap)
                _progress.tryEmit("🖐 $step: $desc")
                history += desc
                if (AgentProtocol.isLooping(history)) return ToolResult.fail("stuck repeating '$desc' — stopped")

                val ok = when (action) {
                    is AgentAction.Done -> return ToolResult.ok(action.summary.ifBlank { "done" } + " (in $step steps)")
                    is AgentAction.Fail -> return ToolResult.fail(action.reason.ifBlank { "couldn't finish" })
                    is AgentAction.Tap -> {
                        val el = snap.elements.firstOrNull { it.id == action.id } ?: return ToolResult.fail("bad element on step $step")
                        when (FinalActionDetector.classify(el.label)) {
                            FinalActionDetector.Kind.FORBIDDEN -> return ToolResult.fail("stopped: '${el.label}' involves money — not doing that")
                            FinalActionDetector.Kind.NEEDS_CONFIRMATION ->
                                if (!ctx.confirm("Sir, '${el.label}' dabaun? Goal: $goal")) return ToolResult.ok("stopped before '${el.label}' — Sir said no")
                            FinalActionDetector.Kind.NORMAL -> Unit
                        }
                        svc.tap(el.id)
                    }
                    is AgentAction.Type -> svc.type(action.id, action.text, action.submit)
                    is AgentAction.Scroll -> svc.scroll(action.down)
                    is AgentAction.Swipe -> svc.swipe(action.direction)
                    is AgentAction.LongPress -> svc.longPress(action.id)
                    AgentAction.Back -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    AgentAction.Home -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    is AgentAction.OpenApp -> {
                        val app = apps.find(action.name)
                        val intent = app?.let { apps.launchIntent(it) }
                        intent != null && launcher.launch(intent, "Open ${app.label}") == ActivityLauncher.Outcome.LAUNCHED
                    }
                    AgentAction.Wait -> true
                }
                if (!ok) history[history.lastIndex] = "$desc (failed)"
                delay(if (action is AgentAction.OpenApp) 1_800 else 1_100) // let the UI settle
            }
            return ToolResult.fail("took more than ${AgentProtocol.MAX_STEPS} steps — stopped")
        }
    }

    private companion object {
        const val MARKS_NOTE = "\n(The yellow numbers on the screenshot are element numbers. When Sir or the next step needs " +
            "one, say it: e.g. \"search icon is number 14\".)"
    }

    val tools: List<JarvisTool> = listOf(look, lookAtScreen, readScreen, tap, longPress, typeText, scroll, swipe, goBack, goHome, doTask)
}
