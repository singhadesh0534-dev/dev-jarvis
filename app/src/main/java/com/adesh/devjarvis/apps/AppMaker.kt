package com.adesh.devjarvis.apps

/**
 * "Ye app banao": JARVIS writes a complete one-file web app (HTML + CSS + JS, works offline in the phone's WebView),
 * checks it once more for bugs, saves it and opens it. 100% free: only the Gemini key Sir already has.
 * This file holds the prompts and the pure checks. Unit-tested.
 */
object AppMaker {
    const val MAX_TOKENS = 30_000

    /**
     * The JARVIS app kit: one tested base every app is built on (look, storage, phone features), so new apps start
     * from something that already works instead of from nothing.
     */
    val KIT = """
        JARVIS APP KIT — use it in every app:
        - Look: dark UI with CSS variables in :root — --bg:#05080f; --card:#0d1626; --line:#1e3a52; --text:#eaf6ff;
          --dim:#7f9bb3; --accent:#00e5ff; --good:#00e676; --bad:#ff5252. system-ui font, 16px base, cards with 14px
          radius, buttons at least 44px tall, a sticky header with the app name, safe padding (16px), no horizontal
          scroll at 360-430px width, empty states with a friendly line, a small toast for feedback.
        - Put this exact script first inside the main <script> and use it for all saving and phone features:
          const store={get(k,d){try{const v=localStorage.getItem(k);return v==null?d:JSON.parse(v)}catch(e){return d}},
          set(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}}};
          const jarvis={vibrate(ms){try{window.JarvisPhone?JarvisPhone.vibrate(ms|0):navigator.vibrate&&navigator.vibrate(ms)}catch(e){}},
          speak(t){try{if(window.JarvisPhone)JarvisPhone.speak(String(t));else if(window.speechSynthesis)speechSynthesis.speak(new SpeechSynthesisUtterance(String(t)))}catch(e){}},
          notify(title,text){try{if(window.JarvisPhone)JarvisPhone.notify(String(title),String(text||''));else if(window.Notification){Notification.permission==='granted'?new Notification(title,{body:text}):Notification.requestPermission()}}catch(e){}},
          toast(t){const d=document.createElement('div');d.textContent=t;d.style.cssText='position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#0d1626;color:#eaf6ff;border:1px solid #00e5ff;padding:10px 16px;border-radius:12px;z-index:9999;font:14px system-ui';document.body.appendChild(d);setTimeout(()=>d.remove(),1800)}};
        - Phone features only through that kit: jarvis.vibrate(30) on important taps, jarvis.speak(...) to read aloud,
          jarvis.notify(title, text) for alerts, jarvis.toast(...) for feedback. Photos / camera: <input type="file"
          accept="image/*" capture="environment"> and FileReader to show them. Never alert() for normal feedback.
        - Never use external URLs, fetch to the internet, CDNs, web fonts or remote images.
    """.trimIndent()

    /** Extra must-haves for common kinds of app, picked from Sir's words. */
    fun recipeFor(idea: String): String {
        val t = idea.lowercase()
        fun has(vararg w: String) = w.any { it in t }
        return when {
            has("todo", "to-do", "task", "kaam ki list", "checklist") ->
                "Recipe (todo): add with Enter or button, tick done (strike-through), edit by tapping text, delete with confirm, " +
                    "filters All / Pending / Done, count of pending, clear done, optional due date sorted soonest first."
            has("kharcha", "expense", "budget", "hisab", "hisaab", "paisa", "money") ->
                "Recipe (expenses): amount + category + note + date, today / this month totals, per-category totals with simple bars, " +
                    "monthly budget with remaining, list newest first with delete, rupee formatting (₹ with commas)."
            has("quiz", "question", "sawal", "sawaal", "mcq") ->
                "Recipe (quiz): at least 10 real questions on the topic, one at a time with 4 options, instant right/wrong colour, " +
                    "score and progress bar, final result screen with retry, best score saved."
            has("game", "khel", "snake", "puzzle", "tic", "2048") ->
                "Recipe (game): touch controls (big buttons or swipe), start / pause / restart, score and saved high score, " +
                    "requestAnimationFrame for motion, a clear game-over screen, works in portrait."
            has("note", "notes", "diary", "likh") ->
                "Recipe (notes): list with title + first line + date, create / edit full screen, autosave while typing, search, " +
                    "delete with confirm, pin to top."
            has("habit", "aadat", "streak", "routine", "gym", "water", "paani") ->
                "Recipe (habits): add habits, tick today, current and best streak, last 7 days as dots, gentle jarvis.vibrate on tick."
            else -> ""
        }
    }

    val BUILD = """
        You are an expert front-end engineer. Build the app Sir describes as ONE complete, self-contained HTML file:
        inline <style> and <script>, no external URLs, CDNs, libraries, fonts or images — it must work offline inside an
        Android WebView. Mobile-first and touch friendly (390 px wide phone), a clean modern dark UI with good contrast,
        labels in the language Sir used (Hinglish / Hindi / English). Keep all user data in localStorage so it survives
        closing the app. Every button and feature must really work: no placeholders, no TODO, no lorem ipsum, no fake
        sample data unless he asked for it. Validate inputs, handle empty states, and never throw a JavaScript error.
        Output ONLY the HTML document, starting with <!DOCTYPE html> and ending with </html> — no explanation, no
        markdown fences.
    """.trimIndent()

    val REVIEW = """
        You are a strict senior reviewer. Below are Sir's request and a single-file HTML app built for it. Find and fix
        every problem: JavaScript errors, buttons that do nothing, features from the request that are missing, data not
        saved in localStorage, layout broken on a 390 px wide phone, external resources (remove them). Keep everything
        that already works. Output ONLY the full corrected HTML document, from <!DOCTYPE html> to </html>, no
        explanation, no markdown fences.
    """.trimIndent()

    val CHANGE = """
        You are an expert front-end engineer. Below is Sir's current single-file HTML app and the change he wants.
        Apply the change completely, keep everything else working, keep it self-contained and offline, keep the data in
        localStorage compatible. Output ONLY the full updated HTML document, from <!DOCTYPE html> to </html>, no
        explanation, no markdown fences.
    """.trimIndent()

    /** Fix pass after JARVIS ran the app himself and caught errors / looked at it. */
    val FIX = """
        You are an expert front-end engineer. JARVIS ran Sir's single-file HTML app in a phone browser, tapped through it
        and caught the problems listed below (JavaScript errors, broken features, or what looked wrong on screen). Fix
        every one of them at the root, keep everything that works, keep the data in localStorage compatible and keep it
        self-contained and offline. Output ONLY the full corrected HTML document, from <!DOCTYPE html> to </html>, no
        explanation, no markdown fences.
    """.trimIndent()

    /** What JARVIS asks when he looks at a screenshot of the app. */
    val LOOK = """
        You are checking a screenshot of a phone app that was just built. Look for real visual problems only: text cut off
        or overlapping, buttons off screen or too small to tap, unreadable contrast, broken or empty layout, horizontal
        overflow, error text on the page. If it looks fine, answer exactly OK. Otherwise list each problem in one short line
        (max 6 lines), no other words.
    """.trimIndent()

    /** The model's look verdict: null when it is fine, else the problems. */
    fun lookProblems(answer: String?): String? {
        val a = answer?.trim().orEmpty()
        if (a.isEmpty()) return null
        val first = a.lineSequence().first().trim().trimEnd('.', '!').uppercase()
        return if (first == "OK" || first == "LOOKS OK" || first == "ALL OK") null else a.take(600)
    }

    /** The first lines JARVIS's test harness puts in the app it tests: no blocking dialogs, every error reported. */
    const val TEST_HOOKS = "<script>window.alert=function(){};window.confirm=function(){return true};" +
        "window.prompt=function(m,d){return d||'Test'};window.onerror=function(m,s,l){console.error('JS error: '+m+' (line '+l+')')};" +
        "window.addEventListener('unhandledrejection',function(e){console.error('Promise error: '+((e.reason&&e.reason.message)||e.reason))});</script>"

    /** Fills the inputs with sample values, then taps every button / checkbox once, slowly; logs when done. */
    const val TEST_CLICKS = "(async function(){try{var w=function(ms){return new Promise(function(r){setTimeout(r,ms)})};" +
        "document.querySelectorAll('input:not([type=checkbox]):not([type=radio]):not([type=file]),textarea').forEach(function(i,n){" +
        "try{i.value=(i.type==='number'||i.inputMode==='numeric')?String(10+n):(i.type==='date'?'2026-01-15':'Test '+(n+1));" +
        "i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}))}catch(e){}});" +
        "var els=Array.prototype.slice.call(document.querySelectorAll('button,[onclick],input[type=checkbox],[role=button]')).slice(0,40);" +
        "for(var k=0;k<els.length;k++){try{els[k].click()}catch(e){console.error('tap failed: '+e.message)}await w(80)}" +
        "}catch(e){console.error('test: '+e.message)}console.log('JARVIS_TEST_DONE')})();"

    /** The app with the test hooks right at the start of <head> (or the document). */
    fun withTestHooks(html: String): String {
        val i = html.lowercase().indexOf("<head>")
        return if (i >= 0) html.substring(0, i + 6) + TEST_HOOKS + html.substring(i + 6) else TEST_HOOKS + html
    }

    /** Distinct, short error lines from the console (the harness's own markers left out). */
    fun errorReport(lines: List<String>, max: Int = 8): String =
        lines.map { it.trim() }.filter { it.isNotEmpty() && "JARVIS_TEST_DONE" !in it }
            .distinct().take(max).joinToString("\n") { "- " + it.take(220) }

    /** Version history: which saved versions to delete so only the newest [keep] stay (names are timestamps). */
    fun versionsToPrune(names: List<String>, keep: Int = 10): List<String> =
        names.sortedByDescending { it.substringBefore('.').toLongOrNull() ?: 0L }.drop(keep)

    /** A file name from what Sir called it ("Kharcha app" -> kharcha-app). */
    fun slug(name: String): String {
        val s = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        return s.ifBlank { "app-" + (name.hashCode().toUInt() % 100000u) }
    }

    /** The HTML document inside whatever the model returned (fences or chatter around it), or null. */
    fun extractHtml(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val lower = raw.lowercase()
        val start = lower.indexOf("<!doctype html").takeIf { it >= 0 } ?: lower.indexOf("<html").takeIf { it >= 0 } ?: return null
        val end = lower.lastIndexOf("</html>").takeIf { it > start } ?: return null
        return raw.substring(start, end + "</html>".length)
    }

    /** A usable app: a whole document with a body and some script or interactive content. */
    fun looksComplete(html: String?): Boolean {
        if (html == null || html.length < 300) return false
        val l = html.lowercase()
        return "<body" in l && "</body>" in l && ("<script" in l || "<button" in l || "<input" in l)
    }
}
