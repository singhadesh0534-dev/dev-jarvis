package com.adesh.devjarvis.tools.impl

import android.content.Context
import android.content.Intent
import com.adesh.devjarvis.apps.AppMaker
import com.adesh.devjarvis.apps.AppViewerActivity
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** "Ye app banao": JARVIS builds small apps for Sir right on the phone (one-file web apps, offline, free). */
@Singleton
class AppBuilderTools @Inject constructor(
    @ApplicationContext private val context: Context,
    // Provider breaks the JarvisBrain -> ToolRegistry -> tools -> JarvisBrain cycle.
    private val brain: Provider<JarvisBrain>,
    private val factory: com.adesh.devjarvis.apps.ApkFactory,
    private val web: com.adesh.devjarvis.apps.LocalAppServer,
) {
    private val dir get() = File(context.filesDir, "apps").apply { mkdirs() }

    private fun fileFor(name: String) = File(dir, AppMaker.slug(name) + ".html")

    private fun find(name: String): File? = fileFor(name).takeIf { it.exists() }
        ?: dir.listFiles()?.firstOrNull { it.nameWithoutExtension.contains(AppMaker.slug(name)) || AppMaker.slug(name).contains(it.nameWithoutExtension) }

    /** For testing, Sir wants the app in Chrome, not inside JARVIS; JARVIS's own viewer only if no browser opens. */
    private fun open(file: File) = web.openInChrome(file) || runCatching {
        context.startActivity(com.adesh.devjarvis.apps.AppInstaller.intentFor(context, file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun label(file: File) = file.nameWithoutExtension.replace('-', ' ').replaceFirstChar(Char::uppercase)

    /** Home-screen icon; Android shows its own "Add" box for Sir to tap. */
    private fun pin(file: File) = com.adesh.devjarvis.apps.AppInstaller.pin(context, file, label(file))

    /** Write, then one review pass that fixes bugs; the reviewed version is kept only if it is still a whole app. */
    private suspend fun generate(system: String, request: String): String? = withTimeoutOrNull(240_000) {
        val first = AppMaker.extractHtml(brain.get().complete(system, request, json = false, maxTokens = AppMaker.MAX_TOKENS))
            ?.takeIf { AppMaker.looksComplete(it) } ?: return@withTimeoutOrNull null
        val reviewed = AppMaker.extractHtml(
            brain.get().complete(AppMaker.REVIEW, "REQUEST:\n$request\n\nAPP:\n$first", json = false, maxTokens = AppMaker.MAX_TOKENS),
        )
        reviewed?.takeIf { AppMaker.looksComplete(it) } ?: first
    }

    private val build = object : SimpleTool(
        "build_app",
        "Build a small app Sir asks for (\"ek kharcha app banao\", \"quiz app banao\", \"todo list app banao\"): a complete " +
            "offline one-file web app, written, checked for bugs, saved on the phone and opened. Takes up to a minute or " +
            "two: tell Sir in a few words first. Free. Not for Play Store apps.",
        params {
            string("name", "Short name of the app", required = true)
            string("idea", "Everything Sir wants in it, in detail, in his words plus what clearly follows", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = args.requireString("name")
            val html = generate(AppMaker.BUILD, "App name: $name\nWhat Sir wants: ${args.requireString("idea")}")
                ?: return ToolResult.fail("the app could not be written right now (brain busy, limit or slow net) — tell Sir and offer to try again")
            val file = fileFor(name)
            withContext(Dispatchers.IO) { file.writeText(html) }
            val opened = open(file)
            val pinned = pin(file)
            return ToolResult.ok("App '$name' built (${html.length / 1000} KB), checked once for bugs, saved" +
                (if (opened) " and opened in Chrome for testing (Chrome keeps its own data for it)." else ". It could not open by itself: Sir can say 'meri $name app kholo'.") +
                (if (pinned) " Android is showing an 'Add to home screen' box: tell Sir to tap Add, then the app has its own icon on the home screen." else "") +
                " Ask him to try it and say what to change.")
        }
    }

    private val change = object : SimpleTool(
        "change_app",
        "Change an app JARVIS built before (\"kharcha app mein total bhi dikhao\"). Rewrites it with the change and opens it.",
        params {
            string("name", "Which app", required = true)
            string("change", "What to change or add", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            val current = withContext(Dispatchers.IO) { file.readText() }
            val html = generate(AppMaker.CHANGE, "CHANGE:\n${args.requireString("change")}\n\nCURRENT APP:\n$current")
                ?: return ToolResult.fail("the change could not be made right now — the old version is untouched")
            withContext(Dispatchers.IO) { file.writeText(html) }
            open(file)
            return ToolResult.ok("Changed and reopened '${file.nameWithoutExtension}' in Chrome. Ask Sir to check it (pull down to reload if it shows the old one).")
        }
    }

    private val openApp = object : SimpleTool(
        "open_my_app",
        "Open an app JARVIS built for Sir earlier, or list them when no name is given.",
        params { string("name", "Which app (optional)") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = args.string("name") ?: return ToolResult.ok("Sir's apps: ${names()}")
            val file = find(name) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            return if (open(file)) ToolResult.ok("opened ${file.nameWithoutExtension} in Chrome") else ToolResult.fail("could not open it")
        }
    }

    private val install = object : SimpleTool(
        "install_my_app",
        "Put an app JARVIS built on the home screen as its own icon (\"ise phone mein install karo\", \"home screen pe lagao\"). " +
            "Android shows an 'Add to home screen' box that Sir taps. This is a home-screen app, not a Play Store APK.",
        params { string("name", "Which app", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            return if (pin(file)) ToolResult.ok("Android's 'Add to home screen' box is on screen for '${label(file)}': tell Sir to tap Add.")
            else ToolResult.fail("this phone's launcher does not allow adding icons from apps — Sir can open it by saying 'meri ${label(file)} app kholo'")
        }
    }

    private val makeApk = object : SimpleTool(
        "make_apk",
        "Turn an app JARVIS built into a REAL Android APK that installs like any app (\"ise asli app bana do\", " +
            "\"iska APK banao\", \"phone mein asli app ki tarah install karo\"). Built free on GitHub's cloud with Sir's token " +
            "from Settings, takes 3-6 minutes in the background; Android's installer opens by itself when it is ready.",
        params { string("name", "Which app", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            if (!com.adesh.devjarvis.core.Edition.declares(context, android.Manifest.permission.REQUEST_INSTALL_PACKAGES)) {
                return ToolResult.fail("this Lite edition cannot install APKs — the Full JARVIS can")
            }
            if (!factory.hasToken) {
                return ToolResult.fail("no GitHub token yet. Tell Sir: one time only, Settings > API keys > 'GitHub token banao' button, " +
                    "generate, paste the token there and SAVE; then say this again.")
            }
            if (factory.busy) return ToolResult.ok("an APK is already being built; this one can follow when it is done — tell Sir")
            val html = withContext(Dispatchers.IO) { file.readText() }
            factory.start(label(file), html)
            return ToolResult.ok("Started: '${label(file)}' is being built into a real APK on GitHub's free cloud (3-6 minutes, " +
                "progress on the HUD and in a notification). When ready, Android's installer opens: Sir taps Install. " +
                "Tell him this in one or two lines and carry on talking meanwhile.")
        }
    }

    private fun names() = dir.listFiles()?.map { it.nameWithoutExtension }?.joinToString().orEmpty().ifBlank { "(none yet)" }

    val tools: List<JarvisTool> = listOf(build, change, openApp, install, makeApk)
}
