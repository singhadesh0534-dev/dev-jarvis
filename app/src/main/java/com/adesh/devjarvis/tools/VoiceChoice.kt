package com.adesh.devjarvis.tools

import com.adesh.devjarvis.brain.Persona

/**
 * "Awaaz badlo" done right: the voice Sir hears belongs to the role JARVIS is in (JARVIS, dost or gf each have their own),
 * and pitch / speed words ("high", "thoda bhaari", "+10") become real numbers. Pure, unit-tested.
 */
object VoiceChoice {
    /** Every Live voice name, men's and women's. */
    val ALL: List<String> = Persona.HIS_VOICES + Persona.HER_VOICES

    /** What each Live voice sounds like, so a disliked voice is swapped for a clearly different one. */
    val CHARACTER: Map<String, String> = mapOf(
        "Charon" to "calm, informative", "Orus" to "firm, deep", "Fenrir" to "excitable, energetic", "Iapetus" to "clear",
        "Algenib" to "gravelly, heavy", "Rasalgethi" to "informative", "Alnilam" to "firm", "Schedar" to "even, steady",
        "Enceladus" to "breathy, soft", "Umbriel" to "easy-going", "Puck" to "upbeat, young", "Achird" to "friendly",
        "Sadaltager" to "knowledgeable",
        "Kore" to "firm", "Leda" to "youthful", "Aoede" to "breezy", "Zephyr" to "bright", "Callirrhoe" to "easy-going",
        "Autonoe" to "bright", "Despina" to "smooth", "Erinome" to "clear", "Laomedeia" to "upbeat", "Achernar" to "soft",
        "Sulafat" to "warm", "Vindemiatrix" to "gentle", "Gacrux" to "mature", "Pulcherrima" to "forward",
    )

    /** "Charon (calm, informative), Orus (firm, deep), …" for a tool description. */
    fun described(names: List<String>): String = names.joinToString(", ") { n -> CHARACTER[n]?.let { "$n ($it)" } ?: n }

    /** Words Sir uses for a kind of voice -> the characters that fit it. */
    private val WANTS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("tagd", "strong", "powerful", "damdaar", "dabang", "firm", "dhamakedar") to listOf("firm", "excitable", "gravelly"),
        listOf("bhaari", "bhari", "heavy", "deep", "gehri", "gahri", "bass", "moti") to listOf("gravelly", "deep", "firm"),
        listOf("soft", "meethi", "mithi", "sweet", "pyaari", "pyari", "komal", "dheemi", "gentle", "naram") to listOf("soft", "gentle", "warm", "breathy"),
        listOf("natural", "normal", "asli", "real", "insaan", "human") to listOf("friendly", "easy-going", "warm", "even"),
        listOf("young", "jawaan", "jawan", "cute", "chhoti") to listOf("youthful", "upbeat", "bright"),
        listOf("mature", "senior", "samajhdaar") to listOf("mature", "informative", "knowledgeable"),
        listOf("energetic", "excited", "josh", "fast", "tez") to listOf("excitable", "upbeat", "lively"),
        listOf("clear", "saaf") to listOf("clear"),
        listOf("smooth", "silky") to listOf("smooth"),
        listOf("dost", "friendly", "yaar") to listOf("friendly", "easy-going"),
    )

    /**
     * "Tagdi awaaz", "thodi soft", "natural wali": the allowed voice whose character fits best, never the current one.
     * Null when the words describe no kind of voice.
     */
    fun byDescription(raw: String, allowed: List<String>, current: String): String? {
        val t = raw.lowercase()
        val traits = WANTS.filter { (words, _) -> words.any { it in t } }.flatMap { it.second }
        if (traits.isEmpty()) return null
        fun fit(v: String) = traits.count { tr -> CHARACTER[v]?.contains(tr) == true }
        return allowed.filter { !it.equals(current, true) && fit(it) > 0 }.maxByOrNull { fit(it) }
    }

    /** A voice name, or else a description of one, for this list. */
    fun pick(raw: String, allowed: List<String>, current: String): String? =
        voiceName(raw, allowed) ?: byDescription(raw, allowed, current)

    /** The setting that holds the voice of this role. */
    fun itemFor(role: String?): String = when (Persona.normalize(role)) {
        Persona.DOST -> "dost_voice"
        Persona.PARTNER -> "gf_voice"
        else -> "live_voice"
    }

    /** The exact voice name Sir asked for (any case), or null when it isn't one of the Live voices. */
    fun voiceName(raw: String, allowed: List<String> = ALL): String? {
        val t = raw.trim()
        return allowed.firstOrNull { it.equals(t, ignoreCase = true) }
            ?: allowed.firstOrNull { Regex("""\b${it.lowercase()}\b""").containsMatchIn(t.lowercase()) }
    }

    private val UP = listOf("high", "upar", "badha", "tez", "zyada", "jyada", "patli", "more", "increase", "fast", "up")
    private val DOWN = listOf("low", "deep", "neeche", "niche", "kam", "ghata", "bhaari", "bhari", "heavy", "moti", "slow", "decrease", "down")
    private val NORMAL = listOf("normal", "default", "reset", "pehle jaisa", "pehle jaisi")

    /**
     * A pitch / speed value on a scale [min]..[max] (100 = normal) from what the model passed: "120", "+10", "-15",
     * "high", "thoda bhaari", "normal". Null when nothing sensible can be read.
     */
    fun level(raw: String, current: Int, min: Int, max: Int, step: Int = 15): Int? {
        val t = raw.lowercase().trim()
        Regex("""^([+-])\s*(\d{1,3})""").find(t)?.let { m ->
            val n = m.groupValues[2].toInt()
            return (if (m.groupValues[1] == "+") current + n else current - n).coerceIn(min, max)
        }
        t.filter(Char::isDigit).toIntOrNull()?.let { n ->
            return when {
                n in min..max -> n
                n in 0..10 -> min + (max - min) * n / 10
                else -> null
            }
        }
        return when {
            NORMAL.any { it in t } -> 100.coerceIn(min, max)
            UP.any { it in t } -> (current + step).coerceIn(min, max)
            DOWN.any { it in t } -> (current - step).coerceIn(min, max)
            else -> null
        }
    }
}
