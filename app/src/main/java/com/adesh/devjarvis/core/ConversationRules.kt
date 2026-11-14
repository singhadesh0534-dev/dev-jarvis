package com.adesh.devjarvis.core

/** Chhote, testable rules for the conversation flow. */
object ConversationRules {
    private val punctuation = Regex("""[^\p{L}\p{M}\p{N}' ]""")
    private val spaces = Regex("""\s+""")

    /** "Bas", "thank you Jarvis", "ok bye" -> close the follow-up window after replying. */
    fun isEnder(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
            .removeSuffix(" jarvis").removePrefix("jarvis ").removeSuffix(" sir").trim()
        return t in JarvisConfig.CONVERSATION_ENDERS || t in GO_QUIET_EXACT || isNotForJarvis(userText) || wantsQuiet(userText)
    }

    /** Short commands that mean "stop and go quiet" only when said on their own ("band karo" alone, not "wifi band karo"). */
    private val GO_QUIET_EXACT = setOf(
        "band karo", "bas karo", "chalo band karo", "off", "switch off", "switch off karo", "switch off kar lo",
        "switch off kar do", "band kar lo", "band kar do",
    )

    /** "So jao", "chup ho jao", "mat suno", "band ho jao": Sir wants JARVIS himself to stop listening, anywhere in the sentence. */
    private val GO_QUIET = Regex(
        """\b(?:so\s?ja(?:o|iye|a)?|chup\s?(?:ho\s?ja(?:o|iye)?|raho|rahiye|karo|kar\s?ja(?:o)?)|shut\s?up|mat\s?suno|sunna\s?band|""" +
            """stop\s?listening|go\s?to\s?sleep|band\s?ho\s?ja(?:o|iye)?|off\s?ho\s?ja(?:o|iye)?|switch\s?off\s?ho\s?ja(?:o)?|""" +
            """khud\s?ko\s?band|apne\s?aap\s?ko\s?band|aaram\s?karo)\b|सो\s?जाओ|चुप\s?(?:हो\s?जाओ|रहो|करो)|मत\s?सुनो|सुनना\s?बंद|""" +
            """बंद\s?हो\s?जाओ|ऑफ\s?हो\s?जाओ|स्विच\s?ऑफ\s?हो""",
    )

    /** "Switch off kar lo" counts too, unless he names a thing to switch off ("TV switch off karo"). */
    private val SWITCH_OFF = Regex("""\bswitch\s?off\b|स्विच\s?ऑफ""")
    private val THINGS = Regex(
        """\b(?:wifi|wi\s?fi|bluetooth|data|hotspot|torch|flash|light|lights|tv|fan|ac|location|gps|internet|net|alarm|""" +
            """music|gaana|gana|video|phone|mobile|screen|nfc|cooler|geyser|bulb)\b|वाईफाई|ब्लूटूथ|टॉर्च|लाइट|पंखा|टीवी|फोन""",
    )

    fun wantsQuiet(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
        if (t.isEmpty()) return false
        return GO_QUIET.containsMatchIn(t) || (SWITCH_OFF.containsMatchIn(t) && !THINGS.containsMatchIn(t))
    }

    private val complaints = listOf(
        "galat", "ghalat", "wrong", "ye nahi", "yeh nahi", "aisa nahi", "maine ye nahi", "maine yeh nahi", "nahi bola",
        "samjhe nahi", "samjha nahi", "samajh nahi", "bakwas", "bekar", "bekaar", "ghatiya", "kya kar rahe ho",
        "not what i", "that's wrong", "thats wrong", "you got it wrong", "sun nahi", "dobara bolo", "phir se bolo",
        "गलत", "ये नहीं", "नहीं बोला", "समझे नहीं", "बकवास", "बेकार",
    )

    /** Sir is correcting JARVIS or unhappy with the last answer — worth writing to the problem journal. */
    fun isComplaint(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
        return complaints.any { it in t }
    }

    private val notForYou = listOf(
        "tumko nahi bola", "tumhe nahi bola", "tumse nahi bola", "tumse nahi", "tumse baat nahi", "aapko nahi bola",
        "tujhe nahi bola", "tumko nahi bol raha", "tumse nahi bol raha", "not talking to you", "wasn't talking to you",
        "was not talking to you", "didn't call you", "did not call you", "tumko nahi bulaya", "tumhe nahi bulaya",
        "तुमको नहीं बोला", "तुम्हें नहीं बोला", "तुमसे नहीं", "तुमसे बात नहीं",
    )

    /** "Maine tumko nahi bola" — Sir wasn't addressing JARVIS: apologise in a word and go back to standby. */
    fun isNotForJarvis(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
        return notForYou.any { it in t }
    }
}
