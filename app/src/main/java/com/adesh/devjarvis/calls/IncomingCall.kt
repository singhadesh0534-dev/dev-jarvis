package com.adesh.devjarvis.calls

/** Incoming call: kya bolna hai aur Sir ke jawab ka matlab. Pure, unit-tested. */
object IncomingCall {
    enum class Reply { ANSWER, REJECT, UNCLEAR }

    private val REJECT_WORDS = setOf(
        "no", "nahi", "nahin", "nai", "mat", "cut", "reject", "decline", "ignore", "kat", "kato", "katdo", "busy",
        "काट", "काटो", "कट", "मत", "नहीं", "नही", "रिजेक्ट", "डिक्लाइन", "इग्नोर",
    )
    private val REJECT_PREFIXES = listOf("kaat", "काट")
    private val ANSWER_WORDS = setOf(
        "answer", "pick", "receive", "accept", "haan", "han", "haa", "ha", "yes", "ok", "okay", "theek", "thik",
        "हां", "हाँ", "हा", "आंसर", "रिसीव", "पिक", "यस", "ओके", "ठीक",
    )
    private val ANSWER_PREFIXES = listOf("utha", "उठा")

    private fun tokens(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }

    /** "mat uthao" / "kaat do" -> REJECT, "utha lo" / "haan" -> ANSWER. A refusal anywhere wins. */
    fun parse(heard: String?): Reply {
        val t = tokens(heard.orEmpty())
        if (t.isEmpty()) return Reply.UNCLEAR
        val negatedPick = t.zipWithNext().any { (a, b) -> a == "na" && ANSWER_PREFIXES.any { b.startsWith(it) } }
        if (negatedPick || t.any { w -> w in REJECT_WORDS || REJECT_PREFIXES.any { w.startsWith(it) } }) return Reply.REJECT
        if (t.any { w -> w in ANSWER_WORDS || ANSWER_PREFIXES.any { w.startsWith(it) } }) return Reply.ANSWER
        return Reply.UNCLEAR
    }

    /** Name as shown by the dialer; a bare number is not read out digit by digit. */
    fun callerForSpeech(title: String?): String {
        val t = title.orEmpty().trim()
        if (t.isEmpty()) return "kisi"
        if (t.count { it.isDigit() } >= 6 && t.count { it.isLetter() } <= 2) return "ek unknown number"
        return t.take(40)
    }

    fun announcement(caller: String) = "Sir, $caller ka call aa raha hai. Uthaun ya kaat doon?"

    private val DECLINE_LABELS = listOf("decline", "reject", "dismiss", "hang", "end", "अस्वीकार", "काट", "ख़ारिज", "खारिज", "समाप्त")
    private val ANSWER_LABELS = listOf("answer", "accept", "pick", "जवाब", "उत्तर", "स्वीकार", "उठा")

    /** Index of the notification button that answers (or declines) the call, by its label; null if not recognisable. */
    fun pickAction(labels: List<String>, answer: Boolean): Int? {
        fun isDecline(l: String) = DECLINE_LABELS.any { it in l }
        val idx = labels.map { it.lowercase() }.indexOfFirst { l ->
            if (answer) !isDecline(l) && ANSWER_LABELS.any { it in l } else isDecline(l)
        }
        return idx.takeIf { it >= 0 }
    }
}
