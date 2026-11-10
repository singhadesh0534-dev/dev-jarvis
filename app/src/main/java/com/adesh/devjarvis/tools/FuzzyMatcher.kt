package com.adesh.devjarvis.tools

import kotlin.math.max
import kotlin.math.min

/**
 * Naam match karna: "Mummy" -> "Mummy Ji ❤️", "mom" -> "Mummy", "rahul bhai" -> "Bhai (Rahul)",
 * typo "mumy" -> "Mummy". Contacts aur apps dono ke liye. Pure Kotlin, unit-tested.
 */
object FuzzyMatcher {

    /** Rishton / nicknames ke groups — ek group ke shabd ek doosre ke barabar maane jaate hain. */
    val ALIAS_GROUPS: List<Set<String>> = listOf(
        setOf("mummy", "mumma", "mumy", "mom", "mommy", "mummi", "maa", "ma", "mother", "mammi", "amma", "mataji"),
        setOf("papa", "pappa", "dad", "daddy", "father", "pitaji", "abbu", "baba"),
        setOf("bhai", "bhaiya", "bhaiyya", "bro", "brother", "bhaiyu"),
        setOf("didi", "di", "sister", "sis", "behen", "bahan", "dii"),
        setOf("wife", "biwi", "patni", "mrs", "wifey"),
        setOf("husband", "pati", "hubby"),
        setOf("dada", "dadaji", "grandpa", "grandfather", "nana", "nanaji"),
        setOf("dadi", "dadima", "grandma", "grandmother", "nani", "nanima"),
        setOf("chacha", "chachu", "uncle", "mama", "mamaji", "tauji"),
        setOf("chachi", "aunty", "auntie", "mami", "mamiji", "tai"),
        // Apps
        setOf("youtube", "yt"),
        setOf("instagram", "insta", "ig"),
        setOf("facebook", "fb"),
        setOf("whatsapp", "wa", "watsapp", "whatsap"),
        setOf("gpay", "google pay", "googlepay", "tez"),
        setOf("photos", "gallery"),
        setOf("chrome", "browser"),
        setOf("maps", "google maps", "map"),
        setOf("telegram", "tg"),
        setOf("calculator", "calc"),
        setOf("camera", "cam"),
        setOf("settings", "setting"),
        setOf("playstore", "play store", "google play", "store"),
    )

    private val nonWord = Regex("""[^\p{L}\p{M}\p{N} ]""")
    private val spaces = Regex("""\s+""")

    /** Words that are not part of a name: "mere papa ji ko" -> "papa". */
    private val FILLER = setOf(
        "ji", "jee", "g", "sahab", "saab", "sahib", "sir", "madam", "mam", "ko", "ka", "ki", "ke", "se",
        "mera", "meri", "mere", "my", "apne", "apni", "apna", "the", "wala", "wali", "number", "contact",
    )
    private val FOLDED_GROUPS: List<Set<String>> by lazy { ALIAS_GROUPS.map { g -> g.map(Translit::fold).toSet() } }

    /** Lowercase, Hindi script -> Roman, punctuation and emoji out. */
    fun normalize(s: String): String = spaces.replace(nonWord.replace(Translit.toLatin(s).lowercase(), " "), " ").trim()

    private fun tokens(s: String) = normalize(s).split(' ').filter { it.isNotEmpty() }

    /** [query] without filler words, unless that would leave nothing. */
    fun stripFiller(query: String): String = tokens(query).filter { it !in FILLER }.joinToString(" ").ifEmpty { normalize(query) }

    private fun aliasesOf(word: String, groups: List<Set<String>>): Set<String> =
        groups.firstOrNull { word in it } ?: setOf(word)

    /**
     * Similarity 0..1 between what Sir said and a contact/app name. Tried twice: as written, and by sound
     * ("पापा" / "pappa" / "paapa" == "Papa"), so the script or spelling the recogniser chose doesn't matter.
     */
    fun score(query: String, candidate: String): Double {
        val q = stripFiller(query)
        val c = normalize(candidate)
        val asWritten = scoreNormalized(q, c, ALIAS_GROUPS)
        if (asWritten >= 0.97) return asWritten
        val bySound = scoreNormalized(Translit.fold(q), Translit.fold(c), FOLDED_GROUPS) * 0.98
        return max(asWritten, bySound)
    }

    private fun scoreNormalized(q: String, c: String, groups: List<Set<String>>): Double {
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        // Whole-phrase alias ("google pay" == "gpay").
        if (aliasesOf(q, groups).contains(c) || aliasesOf(c, groups).contains(q)) return 0.93

        val qt = q.split(' ').filter { it.isNotEmpty() }
        val ct = c.split(' ').filter { it.isNotEmpty() }
        if (qt.isEmpty() || ct.isEmpty()) return 0.0
        if (qt.all { it in ct }) return if (qt.size == ct.size) 0.97 else 0.92
        if (qt.all { w -> ct.any { it in aliasesOf(w, groups) } }) return 0.88
        if (q.length >= 3 && (c.startsWith(q) || ct.any { it.startsWith(q) })) return 0.84

        // Typos: best Jaro-Winkler per query token, averaged.
        val perToken = qt.map { w -> ct.maxOf { max(jaroWinkler(w, it), aliasesOf(w, groups).maxOf { a -> jaroWinkler(a, it) }) } }
        val tokenScore = perToken.average()
        val whole = jaroWinkler(q, c)
        return 0.8 * max(tokenScore, whole)
    }

    data class Match<T>(val item: T, val score: Double)

    /** Ranked matches above [threshold]; best first. */
    fun <T> rank(query: String, items: List<T>, threshold: Double = 0.68, names: (T) -> List<String>): List<Match<T>> =
        items.map { item -> Match(item, names(item).maxOfOrNull { score(query, it) } ?: 0.0) }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }

    /** True if the top two are too close to choose between (and are different things). */
    fun <T> isAmbiguous(matches: List<Match<T>>, sameThing: (T, T) -> Boolean): Boolean {
        if (matches.size < 2) return false
        val (a, b) = matches[0] to matches[1]
        return a.score - b.score < 0.03 && !sameThing(a.item, b.item)
    }

    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = max(0, max(a.length, b.length) / 2 - 1)
        val aM = BooleanArray(a.length)
        val bM = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val lo = max(0, i - range)
            val hi = min(b.length - 1, i + range)
            for (j in lo..hi) {
                if (!bM[j] && a[i] == b[j]) {
                    aM[i] = true; bM[j] = true; matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var t = 0
        var k = 0
        for (i in a.indices) {
            if (!aM[i]) continue
            while (!bM[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < min(4, min(a.length, b.length)) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }
}
