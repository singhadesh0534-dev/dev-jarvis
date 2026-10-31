package com.adesh.devjarvis.brain

/**
 * Model kabhi-kabhi markdown/emoji/URL bhej deta hai. TTS ko dene se pehle saaf karo,
 * warna JARVIS "asterisk asterisk" bolega.
 */
object SpeechSanitizer {

    private val url = Regex("""https?://\S+|www\.\S+""")
    private val markdownLink = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val codeFence = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)
    private val markdownSymbols = Regex("""[*_`#>~|]+""")
    private val bullet = Regex("""(?m)^\s*(?:[-•]|\d+[.)])\s+""")
    private val spaces = Regex("""\s+""")

    fun clean(text: String): String {
        var t = text
        t = codeFence.replace(t, " ")
        t = markdownLink.replace(t) { it.groupValues[1] }
        t = url.replace(t, " ")
        t = bullet.replace(t, "")
        t = markdownSymbols.replace(t, " ")
        t = removeEmoji(t)
        return spaces.replace(t, " ").trim()
    }

    private fun removeEmoji(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun isEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF ||   // emoticons, symbols, pictographs, flags
            cp in 0x2600..0x27BF ||  // misc symbols & dingbats
            cp in 0x1F900..0x1F9FF ||
            cp == 0xFE0F || cp == 0x200D || // variation selector, zero-width joiner
            cp in 0x1F3FB..0x1F3FF  // skin tones
}
