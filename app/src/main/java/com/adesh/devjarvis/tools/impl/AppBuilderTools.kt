package com.adesh.devjarvis.tools.impl

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.adesh.devjarvis.apps.AppMaker
import com.adesh.devjarvis.apps.AppTester
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.tools.ContactRepository
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.PhoneNumbers
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * "Ye app banao": JARVIS builds small apps for Sir right on the phone (one-file web apps, offline, free) — on the
 * JARVIS app kit, then tests them himself (taps through every button, fixes every error), opens them in Chrome, looks
 * at the screen and fixes what looks wrong. Every change keeps the old version for "pichla version wapas lao".
 */
@Singleton
class AppBuilderTools @Inject constructor(
    @ApplicationContext private val context: Context,
    // Provider breaks the JarvisBrain -> ToolRegistry -> tools -> JarvisBrain cycle.
    private val brain: Provider<JarvisBrain>,
    private val factory: com.adesh.devjarvis.apps.ApkFactory,
    private val web: com.adesh.devjarvis.apps.LocalAppServer,
    private val tester: AppTester,
    private val contacts: ContactRepository,
) {
    private val dir get() = File(context.filesDir, "apps").apply { mkdirs() }
    private val historyRoot get() = File(context.filesDir, "app-history").apply { mkdirs() }

    private fun fileFor(name: String) = File(dir, AppMaker.slug(name) + ".html")

    private fun appFiles(): List<File> = dir.listFiles()?.filter { it.isFile && it.extension == "html" }.orEmpty()

    private fun find(name: String): File? = fileFor(name).takeIf { it.exists() }
        ?: AppMaker.slug(name).let { s -> appFiles().firstOrNull { it.nameWithoutExtension.contains(s) || s.contains(it.nameWithoutExtension) } }

    /** For testing, Sir wants the app in Chrome, not inside JARVIS; JARVIS's own viewer only if no browser opens. */
    private fun open(file: File) = web.openInChrome(file) || runCatching {
        context.startActivity(com.adesh.devjarvis.apps.AppInstaller.intentFor(context, file).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun label(file: File) = file.nameWithoutExtension.replace('-', ' ').replaceFirstChar(Char::uppercase)

    /** Home-screen icon; Android shows its own "Add" box for Sir to tap. */
    private fun pin(file: File) = com.adesh.devjarvis.apps.AppInstaller.pin(context, file, label(file))

    // ---------------------------------------------------------------- making and checking

    private suspend fun write(system: String, request: String): String? =
        AppMaker.extractHtml(brain.get().complete(system, request, json = false, maxTokens = AppMaker.MAX_TOKENS))
            ?.takeIf { AppMaker.looksComplete(it) }

    /** What the build pipeline found out, for the tool's answer. */
    private class Report(var fixedRounds: Int = 0, var errorsLeft: String = "", var looked: Boolean = false, var lookFixed: String = "")

    /** Test the app in the hidden browser and fix what breaks, up to two rounds. */
    private suspend fun testAndFix(html0: String, request: String, report: Report): String {
        var html = html0
        repeat(2) {
            val errors = runCatching { tester.errors(html) }.getOrDefault(emptyList())
            if (errors.isEmpty()) { report.errorsLeft = ""; return html }
            report.errorsLeft = AppMaker.errorReport(errors)
            val fixed = write(AppMaker.FIX + "\n\n" + AppMaker.KIT, "REQUEST:\n$request\n\nPROBLEMS JARVIS CAUGHT WHILE TESTING:\n${report.errorsLeft}\n\nAPP:\n$html")
                ?: return html
            html = fixed
            report.fixedRounds++
        }
        report.errorsLeft = AppMaker.errorReport(runCatching { tester.errors(html) }.getOrDefault(emptyList()))
        return html
    }

    /** After it opened in Chrome: look at the screen; if something looks wrong, fix it and reopen. */
    private suspend fun lookAndFix(file: File, request: String, report: Report) {
        val svc = com.adesh.devjarvis.accessibility.JarvisAccessibilityService.get() ?: return
        delay(2_500)
        val bmp = runCatching { svc.screenshot() }.getOrNull() ?: return
        val jpeg = withContext(Dispatchers.Default) {
            runCatching { com.adesh.devjarvis.vision.ImageUtil.toJpeg(bmp, maxSide = 1024, quality = 70) }.getOrNull()
        }
        bmp.recycle()
        jpeg ?: return
        report.looked = true
        val problems = AppMaker.lookProblems(brain.get().see(jpeg, AppMaker.LOOK)) ?: return
        val current = withContext(Dispatchers.IO) { file.readText() }
        val fixed = write(AppMaker.FIX + "\n\n" + AppMaker.KIT, "REQUEST:\n$request\n\nWHAT LOOKED WRONG ON SCREEN:\n$problems\n\nAPP:\n$current") ?: return
        val checked = testAndFix(fixed, request, Report())
        save(file, checked)
        report.lookFixed = problems.lineSequence().take(3).joinToString("; ")
        open(file)
    }

    /** Saves, keeping the old version in the history (newest 10). */
    private suspend fun save(file: File, html: String) = withContext(Dispatchers.IO) {
        if (file.exists()) {
            val h = File(historyRoot, file.nameWithoutExtension).apply { mkdirs() }
            file.copyTo(File(h, "${System.currentTimeMillis()}.html"), overwrite = true)
            val names = h.listFiles()?.map { it.name }.orEmpty()
            AppMaker.versionsToPrune(names).forEach { File(h, it).delete() }
        }
        file.writeText(html)
    }

    private fun summary(r: Report): String = buildString {
        append(if (r.fixedRounds > 0) " JARVIS tested it himself (filled the inputs, tapped every button) and fixed ${r.fixedRounds} round(s) of errors."
        else " JARVIS tested it himself (filled the inputs, tapped every button): no errors.")
        if (r.errorsLeft.isNotBlank()) append(" Still seen in the test: ${r.errorsLeft.lineSequence().first().removePrefix("- ")}.")
        if (r.lookFixed.isNotBlank()) append(" On screen he saw and fixed: ${r.lookFixed}.")
        else if (r.looked) append(" He looked at it on screen: it looks fine.")
    }

    // ---------------------------------------------------------------- tools

    private val build = object : SimpleTool(
        "build_app",
        "Build a small app Sir asks for (\"ek kharcha app banao\", \"quiz app banao\", \"todo list app banao\"): a complete " +
            "offline one-file web app on the JARVIS app kit, then JARVIS tests it himself, fixes errors, opens it in Chrome " +
            "and checks how it looks. Takes one to three minutes: tell Sir in a few words first. Free.",
        params {
            string("name", "Short name of the app", required = true)
            string("idea", "Everything Sir wants in it, in detail, in his words plus what clearly follows", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = args.requireString("name")
            val idea = args.requireString("idea")
            val request = "App name: $name\nWhat Sir wants: $idea\n${AppMaker.recipeFor("$name $idea")}"
            val report = Report()
            val file = fileFor(name)
            val html = withTimeoutOrNull(300_000) {
                val first = write(AppMaker.BUILD + "\n\n" + AppMaker.KIT, request) ?: return@withTimeoutOrNull null
                testAndFix(first, request, report)
            } ?: return ToolResult.fail("the app could not be written right now (brain busy, limit or slow net) — tell Sir and offer to try again")
            save(file, html)
            val opened = open(file)
            if (opened) withTimeoutOrNull(120_000) { lookAndFix(file, request, report) }
            val pinned = pin(file)
            SessionTrace.add("APP built ${file.nameWithoutExtension} fixes=${report.fixedRounds} looked=${report.looked}")
            return ToolResult.ok("App '$name' built (${html.length / 1000} KB) and saved" +
                (if (opened) ", opened in Chrome for testing." else ". It could not open by itself: Sir can say 'meri $name app kholo'.") +
                summary(report) +
                (if (pinned) " Android is showing an 'Add to home screen' box: Sir can tap Add for its own icon." else "") +
                " Tell him briefly, ask him to try it and say what to change. 'Iska asli APK banao' makes a real app.")
        }
    }

    private val change = object : SimpleTool(
        "change_app",
        "Change an app JARVIS built before (\"kharcha app mein total bhi dikhao\"). Rewrites it with the change, tests it, " +
            "keeps the old version (undo possible) and reopens it in Chrome.",
        params {
            string("name", "Which app", required = true)
            string("change", "What to change or add", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            val current = withContext(Dispatchers.IO) { file.readText() }
            val change = args.requireString("change")
            val report = Report()
            val html = withTimeoutOrNull(300_000) {
                val first = write(AppMaker.CHANGE + "\n\n" + AppMaker.KIT, "CHANGE:\n$change\n\nCURRENT APP:\n$current")
                    ?: return@withTimeoutOrNull null
                testAndFix(first, "Change to make: $change", report)
            } ?: return ToolResult.fail("the change could not be made right now — the old version is untouched")
            save(file, html)
            open(file)
            withTimeoutOrNull(120_000) { lookAndFix(file, "Change made: $change", report) }
            return ToolResult.ok("Changed and reopened '${label(file)}' in Chrome; the old version is kept ('pichla version wapas lao' undoes it)." +
                summary(report) + " Ask Sir to check it (pull down to reload if it shows the old one).")
        }
    }

    private val undo = object : SimpleTool(
        "undo_app",
        "Bring back the previous version of an app JARVIS built (\"pichla version wapas lao\", \"ye change hata do\", \"undo karo\"). " +
            "Each call goes one version further back.",
        params { string("name", "Which app", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            val h = File(historyRoot, file.nameWithoutExtension)
            val latest = h.listFiles()?.maxByOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L }
                ?: return ToolResult.fail("no older version of '${label(file)}' is kept")
            withContext(Dispatchers.IO) {
                file.copyTo(File(h, "redo.html"), overwrite = true) // the undone one, just in case
                latest.copyTo(file, overwrite = true)
                latest.delete()
            }
            open(file)
            val left = h.listFiles()?.count { it.nameWithoutExtension.toLongOrNull() != null } ?: 0
            return ToolResult.ok("'${label(file)}' is back to its previous version and reopened in Chrome ($left older version(s) still kept).")
        }
    }

    private val openApp = object : SimpleTool(
        "open_my_app",
        "Open an app JARVIS built for Sir earlier (in Chrome, for testing), or list them when no name is given.",
        params { string("name", "Which app (optional)") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = args.string("name") ?: return ToolResult.ok("Sir's apps: ${names()}")
            val file = find(name) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            return if (open(file)) ToolResult.ok("opened ${label(file)} in Chrome") else ToolResult.fail("could not open it")
        }
    }

    private val install = object : SimpleTool(
        "install_my_app",
        "Put an app JARVIS built on the home screen as its own icon (\"home screen pe lagao\"). Android shows an 'Add to home " +
            "screen' box that Sir taps. For a real installable app use make_apk.",
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

    private val share = object : SimpleTool(
        "share_my_app",
        "Send an app JARVIS built to someone (\"ye app Rahul ko WhatsApp karo\", \"is app ko share karo\"). Sends the real APK " +
            "if one was made, else the app file (opens in any browser). Opens WhatsApp (to that contact if given) or the share " +
            "menu; Sir taps send himself.",
        params {
            string("name", "Which app", required = true)
            string("contact", "Who to send it to (optional)")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val file = find(args.requireString("name")) ?: return ToolResult.fail("no app by that name. Apps: ${names()}")
            val apk = File(File(context.cacheDir, "updates"), "app-${com.adesh.devjarvis.cloud.FactoryRest.slug(label(file))}.apk").takeIf { it.isFile }
            val shareFile = apk ?: withContext(Dispatchers.IO) {
                File(File(context.cacheDir, "shots"), "${file.nameWithoutExtension}.html").apply { parentFile?.mkdirs(); file.copyTo(this, overwrite = true) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", shareFile)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(if (apk != null) "application/vnd.android.package-archive" else "text/html")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, "${label(file)} — JARVIS ne banaya" + if (apk == null) " (browser mein kholo)" else "")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val who = args.string("contact")
            val wa = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
                runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }
            var to = ""
            if (wa != null && who != null) {
                when (val l = contacts.resolve(who)) {
                    is ContactRepository.Lookup.Found -> {
                        intent.setPackage(wa).putExtra("jid", PhoneNumbers.forWhatsApp(l.number) + "@s.whatsapp.net")
                        to = " to ${l.contact.name}"
                    }
                    else -> intent.setPackage(wa)
                }
            }
            val ok = runCatching { context.startActivity(if (intent.`package` != null) intent else Intent.createChooser(intent, "App bhejo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            return if (ok) ToolResult.ok("Opened ${if (intent.`package` != null) "WhatsApp" else "the share menu"}$to with ${if (apk != null) "the APK" else "the app file"} of '${label(file)}': Sir taps send.")
            else ToolResult.fail("could not open sharing")
        }
    }

    private fun names() = appFiles().map { label(it) }.joinToString().ifBlank { "(none yet)" }

    val tools: List<JarvisTool> = listOf(build, change, undo, openApp, install, makeApk, share)
}
