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
            val answer = brain.see(jpeg, args.requireString("question")) ?: return ToolResult.fail("vision needs a Gemini key and internet")
            return ToolResult.ok(answer)
        }
    }

    private val lookAtScreen = object : SimpleTool(
        "look_at_screen", "Look at the current screen (screenshot) and answer Sir's question about it.",
        params { string("question", "e.g. \"is screen pe kya likha hai?\"", required = true) },
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
                ?: return snap?.let { ToolResult.ok("screen text: " + ScreenFormatter.readable(it)) }
                    ?: ToolResult.fail("couldn't capture the screen (needs Android 11+)")
            val jpeg = ImageUtil.toJpeg(bmp)
            bmp.recycle()
            val answer = brain.see(jpeg, args.requireString("question"))
                ?: return snap?.let { ToolResult.ok("screen text: " + ScreenFormatter.readable(it)) } ?: ToolResult.fail("vision unavailable")
            return ToolResult.ok(answer)
        }
    }

    private val readScreen = object : SimpleTool("read_screen", "Read the visible text of the current screen (fast, no image).") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("private screen (${it.reason}) — not reading") }
            return ToolResult.ok("${snap.appLabel}: " + ScreenFormatter.readable(snap))
        }
    }

    // ---------------------------------------------------------------- hands (single steps)

    private val tap = object : SimpleTool(
        "tap", "Tap a button/item on the current screen by its visible label.",
        params { string("text", "Visible label, e.g. Search, Rahul, Like", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("stopped: ${it.reason}") }
            val label = args.requireString("text")
            val el = AgentProtocol.findByLabel(snap, label) ?: return ToolResult.fail("no '$label' on screen")
            when (FinalActionDetector.classify(el.label)) {
                FinalActionDetector.Kind.FORBIDDEN -> return ToolResult.fail("I don't tap payment/purchase buttons")
                FinalActionDetector.Kind.NEEDS_CONFIRMATION ->
                    if (!ctx.confirm("Sir, '${el.label}' dabaun?")) return ToolResult.ok("cancelled — Sir said no")
                FinalActionDetector.Kind.NORMAL -> Unit
            }
            return if (svc.tap(el.id)) ToolResult.ok("tapped '${el.label}'") else ToolResult.fail("tap didn't work")
        }
    }

    private val typeText = object : SimpleTool(
        "type_text", "Type text into the focused (or first) input box on screen. Never for passwords/OTPs.",
        params { string("text", "Text to type", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            val snap = currentScreen(svc) ?: return ToolResult.fail("can't read this screen")
            (ScreenSafety.check(snap) as? ScreenSafety.Verdict.Blocked)?.let { return ToolResult.fail("stopped: ${it.reason}") }
            return if (svc.type(null, args.requireString("text"))) ToolResult.ok("typed") else ToolResult.fail("no text box on screen")
        }
    }

    private val scroll = object : SimpleTool(
        "scroll", "Scroll the current screen.",
        params { string("direction", "down | up", required = true, enum = listOf("down", "up")) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val svc = service() ?: return needsAccessibility()
            currentScreen(svc)
            return if (svc.scroll(args.enum("direction", listOf("down", "up")) != "up")) ToolResult.ok("scrolled") else ToolResult.fail("nothing to scroll")
        }
    }

    private val goBack = object : SimpleTool("go_back", "Press Back.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_BACK)) ToolResult.ok("went back") else needsAccessibility()
    }

    private val goHome = object : SimpleTool("go_home", "Go to the home screen.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult =
            if (JarvisAccessibilityService.global(AccessibilityService.GLOBAL_ACTION_HOME)) ToolResult.ok("home screen") else needsAccessibility()
    }

    // ---------------------------------------------------------------- screen agent

    private val doTask = object : SimpleTool(
        "do_task_on_screen",
        "Do a multi-step job inside other apps by reading the screen and tapping/typing (max 15 steps). " +
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
                    is AgentAction.Type -> svc.type(action.id, action.text)
                    is AgentAction.Scroll -> svc.scroll(action.down)
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

    val tools: List<JarvisTool> = listOf(look, lookAtScreen, readScreen, tap, typeText, scroll, goBack, goHome, doTask)
}
