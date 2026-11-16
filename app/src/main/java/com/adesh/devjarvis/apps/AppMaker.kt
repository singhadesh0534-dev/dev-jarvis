package com.adesh.devjarvis.apps

/**
 * "Ye app banao": JARVIS writes a complete one-file web app (HTML + CSS + JS, works offline in the phone's WebView),
 * checks it once more for bugs, saves it and opens it. 100% free: only the Gemini key Sir already has.
 * This file holds the prompts and the pure checks. Unit-tested.
 */
object AppMaker {
    const val MAX_TOKENS = 30_000

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
