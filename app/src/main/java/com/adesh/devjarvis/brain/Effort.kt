package com.adesh.devjarvis.brain

/**
 * Har sawaal ko utna hi "sochne" do jitna zaroori hai: seedhe command (torch, call, volume, app kholo) = sabse tez,
 * mushkil kaam (research, plan, samjhao, compare, hisaab) = zyada soch. Isse jawab jaldi aata hai aur free quota bachta hai.
 * Pure, unit-tested.
 */
object Effort {
    const val FAST = "minimal"
    const val NORMAL = "low"
    /** Same level as NORMAL for now: it is the highest one known to work on the free Flash model. */
    const val DEEP = "low"

    private val deep = listOf(
        "research", "compare", "tulna", "analysis", "analyse", "analyze", "plan bana", "strategy", "samjhao", "samjha",
        "explain", "kyun", "kyon", "why", "kaise kaam", "step by step", "detail", "vistaar", "poori jaankari", "pros and cons",
        "debug", "code", "solve", "hisaab lagao", "calculate", "fayda nuksan", "difference", "antar", "review", "likh do", "draft",
    )

    fun forQuery(text: String): String {
        val t = text.lowercase()
        val words = t.split(Regex("\\s+")).count { it.isNotBlank() }
        return when {
            deep.any { it in t } -> DEEP
            words <= 9 -> FAST
            else -> NORMAL
        }
    }
}
