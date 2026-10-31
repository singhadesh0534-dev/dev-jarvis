package com.adesh.devjarvis.tools

/** "Haan kar do" / "nahi rehne do" / "yes" / "cancel" -> YES / NO / UNCLEAR. Pure, unit-tested. */
object ConfirmationParser {

    enum class Answer { YES, NO, UNCLEAR }

    private val no = listOf(
        "nahi", "nahin", "nai", "no", "nope", "mat", "cancel", "ruko", "rehne do", "rahne do",
        "stop", "wait", "don't", "dont", "do not", "never", "abhi nahi", "mat karo",
    )
    private val yes = listOf(
        "haan", "haa", "ha", "han", "haanji", "haan ji", "ji haan", "yes", "yeah", "yep", "yup", "ok", "okay",
        "kar do", "karo", "kardo", "bhej do", "bhejo", "laga do", "sure", "bilkul", "theek hai", "thik hai",
        "chalo", "go ahead", "confirm", "do it", "please", "zaroor", "of course", "call karo", "send",
    )
    private val nonWord = Regex("""[^\p{L}\p{M}\p{N}' ]""")
    private val spaces = Regex("""\s+""")

    fun parse(text: String?): Answer {
        if (text.isNullOrBlank()) return Answer.UNCLEAR
        val t = " " + spaces.replace(nonWord.replace(text.lowercase(), " "), " ").trim() + " "
        // Negation wins: "haan nahi", "no don't" -> NO.
        if (no.any { t.contains(" $it ") }) return Answer.NO
        if (yes.any { t.contains(" $it ") }) return Answer.YES
        return Answer.UNCLEAR
    }
}
