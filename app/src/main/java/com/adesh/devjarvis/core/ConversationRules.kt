package com.adesh.devjarvis.core

/** Chhote, testable rules for the conversation flow. */
object ConversationRules {
    private val punctuation = Regex("""[^\p{L}\p{M}\p{N}' ]""")
    private val spaces = Regex("""\s+""")

    /** "Bas", "thank you Jarvis", "ok bye" -> close the follow-up window after replying. */
    fun isEnder(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
            .removeSuffix(" jarvis").removePrefix("jarvis ").removeSuffix(" sir").trim()
        return t in JarvisConfig.CONVERSATION_ENDERS || isNotForJarvis(userText)
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
