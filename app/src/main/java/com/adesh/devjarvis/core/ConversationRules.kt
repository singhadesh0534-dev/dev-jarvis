package com.adesh.devjarvis.core

/** Chhote, testable rules for the conversation flow. */
object ConversationRules {
    private val punctuation = Regex("""[^\p{L}\p{M}\p{N}' ]""")
    private val spaces = Regex("""\s+""")

    /** "Bas", "thank you Jarvis", "ok bye" -> close the follow-up window after replying. */
    fun isEnder(userText: String): Boolean {
        val t = spaces.replace(punctuation.replace(userText.lowercase(), " "), " ").trim()
            .removeSuffix(" jarvis").removePrefix("jarvis ").removeSuffix(" sir").trim()
        return t in JarvisConfig.CONVERSATION_ENDERS
    }
}
