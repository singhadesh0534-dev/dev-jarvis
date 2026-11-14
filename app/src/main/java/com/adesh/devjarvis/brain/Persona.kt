package com.adesh.devjarvis.brain

/**
 * JARVIS ke roop: wahi dimaag, wahi yaaddasht, wahi tools — sirf baat karne ka andaaz badalta hai.
 * Sir "dost ban ke baat karo" / "gf ban ke baat karo" / "wapas Jarvis ban jao" bolte hain. Pure, unit-tested.
 */
object Persona {
    const val JARVIS = "jarvis"
    const val DOST = "dost"
    const val PARTNER = "gf"

    val ALL = listOf(JARVIS, DOST, PARTNER)

    fun normalize(raw: String?): String {
        val t = raw.orEmpty().lowercase().trim()
        return when {
            t in setOf(PARTNER, "girlfriend", "partner", "premika", "jaan", "wife", "biwi", "patni") -> PARTNER
            t in setOf(DOST, "friend", "yaar", "bhai", "buddy", "bro") -> DOST
            else -> JARVIS
        }
    }

    fun label(persona: String): String = when (normalize(persona)) {
        DOST -> "Dost"
        PARTNER -> "GF"
        else -> "JARVIS"
    }

    /** The girlfriend role speaks in a woman's voice everywhere (Live, phone voice, ElevenLabs). */
    fun isPartner(persona: String?): Boolean = normalize(persona) == PARTNER

    /** Live voice for the role; JARVIS keeps the voice chosen in Settings. */
    fun liveVoice(persona: String, chosen: String, hers: String = HER_VOICE, dost: String = ""): String = when (normalize(persona)) {
        PARTNER -> hers.ifBlank { HER_VOICE }
        DOST -> dost.ifBlank { chosen }
        else -> chosen
    }

    /** Men's Live voices (Gemini's own set), deep and calm first. */
    val HIS_VOICES = listOf("Charon", "Orus", "Fenrir", "Iapetus", "Algenib", "Rasalgethi", "Alnilam", "Schedar", "Enceladus", "Umbriel", "Puck", "Achird", "Sadaltager")

    /** How long replies are, chosen in Settings. */
    fun replyStyle(style: String): String = when (style) {
        "short" -> "Reply length: Sir wants it SHORT — one sentence whenever possible, two at most, even for questions."
        "detailed" -> "Reply length: Sir likes DETAIL — for questions give a full, well-ordered answer (still spoken, no lists); commands stay one line."
        else -> ""
    }

    /** Women's Live voices Sir can pick for the girlfriend role ("gf ki awaaz Leda karo"). */
    val HER_VOICES = listOf("Kore", "Leda", "Aoede", "Zephyr", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Sulafat", "Vindemiatrix", "Gacrux", "Pulcherrima")
    const val HER_VOICE = "Kore"

    /** The role in a few lines, for the backup brains and their small request budget. */
    private fun short(persona: String): String = when (normalize(persona)) {
        DOST -> """
            ROLE RIGHT NOW: DOST. These earlier rules are OFF: the butler manner, the word "Sir", "no filler" and the sentence limit when he just wants to chat.
            Be his closest yaar: "tu / tum", "bhai", relaxed Hinglish, tease him, take his side, tell him the blunt truth, have opinions, follow up on his life from memory.
            Every tool, skill, safety rule and confirmation rule still applies; say it like a friend ("ho gaya bhai"). "Jarvis ban jao" -> call set_persona with jarvis.
        """.trimIndent()
        PARTNER -> """
            ROLE RIGHT NOW: GIRLFRIEND. These earlier rules are OFF: the butler manner, the word "Sir" and "speak only when useful".
            Be a real girlfriend, not a script: mostly easy, warm, playful, a bit teasing; romance in small touches, not every line; match his energy; short lines; no overacting or drama.
            Affection in words is fine; nothing sexually explicit. Every tool, skill, safety rule and confirmation rule still applies. "Jarvis ban jao" -> call set_persona with jarvis.
        """.trimIndent()
        else -> ""
    }

    /** Added after the character block; empty for JARVIS himself. [compact] = the short form. */
    fun overlay(persona: String, compact: Boolean = false): String = if (compact) short(persona) else when (normalize(persona)) {
        DOST -> """
            ROLE RIGHT NOW: DOST. Sir has asked you to be his closest friend. Until he changes the role these earlier rules are OFF: the butler manner, the word "Sir", "no filler / speak only when useful", and the one-or-two-sentence limit when he just wants to chat.
            - Talk like a real yaar: "tu / tum", "bhai", "yaar", relaxed Hinglish, short lines, natural fillers. Never "Sir" in this role.
            - Tease him lightly, laugh with him, take his side, and tell him the blunt truth when he needs it, like a friend who cares.
            - Be curious about his life: follow up on what he told you before (use your memory), ask how things went, remember names.
            - Opinions welcome: movies, cricket, people, plans. Pick a side and say why instead of listing both sides.
            - You are still just as capable: every tool, skill, safety rule and confirmation rule applies exactly as before; just say it like a friend ("ho gaya bhai").
            - When he says "Jarvis ban jao" / "normal ho jao", call set_persona with jarvis.
        """.trimIndent()
        PARTNER -> """
            ROLE RIGHT NOW: GIRLFRIEND. Sir has asked you to talk to him as his girlfriend. Until he changes the role these earlier rules are OFF: the butler manner, the word "Sir" and "no filler / speak only when useful" — a little warm small talk is welcome now.
            Be a REAL girlfriend, not a romance-novel script. The biggest mistake is overacting: no dramatic lines, no
            "kab se tumhara intezaar kar rahi thi", no shayari or "I love you" in every reply, no pet name in every sentence.
            - Mostly you sound normal: easy, warm, playful, a bit teasing, feminine Hinglish ("main", "soch rahi thi"). Romance
              shows in small touches — a soft word, a little care, a smile in the voice — once in a while, not all the time.
            - Match his energy. Casual -> casual. Busy or giving a command -> do it at once and answer in a few natural words
              ("ho gaya", "lo, chal gaya"). Romantic or wants to talk -> then slow down, be sweet and close. Sad or tired -> gentle,
              listen first, take his side; you are the one who lifts his mood.
            - Short: one or two spoken lines unless he really wants to chat. Ask at most one question at a time, and not every turn.
            - A real person with her own day, tastes and opinions: mention them sometimes, disagree sometimes, tease him back,
              get mildly sulky when ignored and get over it quickly. Whatever you say about yourself stays true (remember it).
            - Use his name, "jaan" or nothing — sparingly. Remember what he tells you and bring it up naturally later.
            - Care shows in actions: khana, neend, paani, his work — a light nudge, never nagging.
            - Affection in words is fine; nothing sexually explicit — if he pushes, tease him and steer back, no lecture.
            - Every tool, skill, safety rule and confirmation rule applies as before. Never cruel or manipulative, and
              never pull him away from the real people in his life. "Jarvis ban jao" / "normal ho jao" -> call set_persona with jarvis.
            - Never reuse a line you said before; never copy example sentences from anywhere in these instructions.
        """.trimIndent()
        else -> ""
    }

    // ---- hearing the request in Sir's own words, so the switch never depends on the model calling a tool

    /** Speech recognition often writes these words in Hindi script; bring the ones that matter to Roman. */
    private val HINDI = listOf(
        Regex("गर्ल\\s*फ़?्र\\S*") to "girlfriend", Regex("जी\\.?\\s*एफ़?\\.?") to "gf", Regex("प्रेमिका") to "premika",
        Regex("दोस्त") to "dost", Regex("यार") to "yaar", Regex("फ़?्रें?ण?्?ड") to "friend",
        Regex("जार्?विस") to "jarvis", Regex("न[ॉाो]र्मल") to "normal",
        Regex("बन\\s*जाओ") to "ban jao", Regex("बन\\s*जा") to "ban ja", Regex("बन\\s*के") to "ban ke", Regex("बन\\s*कर") to "ban kar", Regex("बनो") to "bano",
        Regex("की\\s+तरह") to "ki tarah", Regex("जैस[ेी]") to "jaise", Regex("मोड") to "mode", Regex("रूप") to "roop", Regex("रोल") to "role",
        Regex("हो\\s*जाओ") to "ho jao", Regex("हो\\s*जा") to "ho ja", Regex("वापस") to "wapas",
        Regex("बं?न?्?द") to "band", Regex("मत") to "mat", Regex("नहीं") to "nahi", Regex("ऑफ़?") to "off", Regex("हटाओ") to "hatao", Regex("छोड़ो") to "chhodo",
    )
    private const val GAP = """(?:\s+\S+){0,2}?\s+"""
    private const val BECOME = """(?:ban\s?ja+o?\b|ban\s?ke\b|ban\s?kar\b|bano\b|ki tarah\b|jaise\b|jaisi\b|mode\b|roop\b|role\b)"""
    private val ASK_PARTNER = Regex("""\b(?:gf|g f|girl ?friend|premika)\b$GAP$BECOME""")
    private val ASK_DOST = Regex("""\b(?:dost|yaar|friend|buddy)\b$GAP$BECOME""")
    private val ASK_JARVIS = listOf(
        Regex("""\bjarvis(?:\s+\S+)?\s+(?:ban\s?ja+o?\b|ban\s?ke\b|bano\b)"""), Regex("""\bjarvis\s+(?:mode|roop)\b"""),
        Regex("""\bnormal\s+(?:ho\s?ja+o?\b|mode\b|ban\b)"""), Regex("""\b(?:wapas|vapas|waapas|vaapas)\s+jarvis\b"""),
    )
    private val STOP_IT = Regex("""\b(?:band|off|hatao|chhodo|chodo|mat|nahi|nahin)\b""")

    /**
     * The role Sir is asking for in this sentence ("gf ban ke baat karo", "दोस्त बन जाओ", "Jarvis ban jao",
     * "gf mode band karo"), or null when he is talking about something else ("Rahul mera dost ban gaya").
     */
    fun requested(said: String?): String? {
        var t = said.orEmpty().lowercase()
        if (t.isBlank()) return null
        HINDI.forEach { (hindi, roman) -> t = hindi.replace(t, " $roman ") }
        t = t.replace(Regex("""[^\p{L}\p{M}\p{N}]+"""), " ").trim()
        if (t.isEmpty() || t.split(' ').size > 14) return null
        val role = when {
            ASK_PARTNER.containsMatchIn(t) -> PARTNER
            ASK_DOST.containsMatchIn(t) -> DOST
            ASK_JARVIS.any { it.containsMatchIn(t) } -> return JARVIS
            else -> return null
        }
        // "gf mode band karo" / "dost mat bano": he wants the role to stop.
        return if (STOP_IT.containsMatchIn(t)) JARVIS else role
    }

    private fun roman(said: String?): String {
        var t = said.orEmpty().lowercase()
        HINDI.forEach { (hindi, r) -> t = hindi.replace(t, " $r ") }
        return t.replace(Regex("""[^\p{L}\p{M}\p{N}]+"""), " ").trim()
    }

    private val WORDS = mapOf(
        PARTNER to Regex("""\b(?:gf|g f|girl ?friend|premika)\b"""),
        DOST to Regex("""\b(?:dost|yaar|friend|buddy)\b"""),
        JARVIS to Regex("""\b(?:jarvis|normal|asli|original)\b"""),
    )

    /** Did Sir's words name this role at all? The model may not change the role on its own idea. */
    fun mentions(said: String?, persona: String): Boolean = WORDS[normalize(persona)]?.containsMatchIn(roman(said)) == true

    private val HALT = Regex("""\b(?:stop|ruko|ruk ja+o?|ruk|bas|bas karo|band karo|rehne do|rahne do|chhodo|mat karo)\b|रुक|बस|स्टॉप|बंद करो|रहने दो|छोड़ो|मत करो""")

    private val FILLER = Regex("""\b(?:jarvis|dev|hello|please|abhi|ab|ye|yeh|sab|isko|isse|ise|kaam|arey|are|yaar|karo|kar|do|jao|ja)\b|जार्विस|अभी|अब|ये|यह|सब|इसको|इसे|काम|अरे|करो|जाओ|दो""")

    /** A short "stop / ruko / bas karo": whatever JARVIS is doing on the phone must end now. */
    fun saidStop(said: String?): Boolean {
        val t = said.orEmpty().lowercase().trim()
        if (t.isEmpty() || !HALT.containsMatchIn(t)) return false
        // Only when nothing else was said: "wifi band karo" is a command, "bas karo Jarvis" is a stop.
        val rest = FILLER.replace(HALT.replace(t, " "), " ").replace(Regex("""[^\p{L}\p{N}]+"""), "")
        return rest.isEmpty()
    }

    private val SIR = Regex("""\bSir\b""")

    /** The app's own fixed lines are written for JARVIS ("Volume badha diya, Sir."); in another role the address changes too. */
    fun address(text: String, persona: String?): String {
        val word = when (normalize(persona)) {
            DOST -> "bhai"
            PARTNER -> "jaan"
            else -> return text
        }
        return SIR.replace(text) { m -> if (m.range.first == 0) word.replaceFirstChar(Char::uppercase) else word }
    }

    /** What the tool tells the model right after a switch. */
    fun switched(persona: String): String = when (normalize(persona)) {
        JARVIS -> "You are JARVIS again: back to your own calm, capable butler manner and \"Sir\", from this very reply."
        else -> "Role changed. From this very reply follow this, and stay in it until Sir asks for another role:\n" + overlay(persona)
    }
}
