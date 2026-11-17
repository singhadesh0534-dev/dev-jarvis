package com.adesh.devjarvis.core

/** The AI character's face on the HUD: what JARVIS's own last reply feels like. */
enum class AgentMood { NEUTRAL, HAPPY, SAD, ANGRY, SURPRISED, LOVE }

/** Reads the mood of JARVIS's reply (Hinglish + English + emoji). Pure, tested. */
object AgentMoods {
    private val ANGRY = Regex("""(?i)(gussa|😠|😡|🤬|bakwas|hadd hai|bas karo|pagal ho)""")
    private val SAD = Regex(
        """(?i)(\bsorry\b|maaf|afsos|nahi ho (paya|payi|saka|saki|raha|rahi)|nahi hua|nahi mila|nahi chala|\bfail|galti|dukh|udaas|""" +
            """😔|😢|😞|☹|problem|\berror\b|kharab|band hai|limit (khatam|hit))""",
    )
    private val LOVE = Regex("""(?i)(❤|💕|💖|😘|🥰|\blove you\b|i love|pyaar|pyar|miss (kiya|karti|karta|you))""")
    private val LOVE_GF = Regex("""(?i)(\bjaan\b|jaanu|babu|sweetheart|darling)""")
    private val SURPRISED = Regex("""(?i)(\bwow\b|\barre\b|\barey\b|sach mein|seriously|kya baat|\bomg\b|😮|😲|😯|!\?)""")
    private val HAPPY = Regex(
        """(?i)(ho gaya|\bdone\b|✓|✅|badhiya|shabash|mubarak|congrat|😄|😊|😀|😁|haha|great|zabardast|\bmast\b|khush|perfect|""" +
            """\bwah\b|swagat|welcome|bahut accha|ekdum)""",
    )

    fun of(reply: String, role: String = "jarvis"): AgentMood {
        val r = reply.trim()
        if (r.isEmpty()) return AgentMood.NEUTRAL
        return when {
            ANGRY.containsMatchIn(r) -> AgentMood.ANGRY
            SAD.containsMatchIn(r) -> AgentMood.SAD
            LOVE.containsMatchIn(r) || (role == "gf" && LOVE_GF.containsMatchIn(r)) -> AgentMood.LOVE
            SURPRISED.containsMatchIn(r) -> AgentMood.SURPRISED
            HAPPY.containsMatchIn(r) -> AgentMood.HAPPY
            else -> AgentMood.NEUTRAL
        }
    }

    /** Hinglish label for the HUD chip. */
    fun label(m: AgentMood): String = when (m) {
        AgentMood.NEUTRAL -> "SHAANT"
        AgentMood.HAPPY -> "KHUSH"
        AgentMood.SAD -> "UDAAS"
        AgentMood.ANGRY -> "GUSSA"
        AgentMood.SURPRISED -> "HAIRAAN"
        AgentMood.LOVE -> "PYAAR"
    }
}
