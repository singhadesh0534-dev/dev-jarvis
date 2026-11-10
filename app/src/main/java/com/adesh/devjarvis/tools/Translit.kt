package com.adesh.devjarvis.tools

/**
 * Hindi (Devanagari) -> Roman, aur dono ko ek "sunne jaisa" roop: taaki "पापा", "Papa", "pappa", "paapa" sab
 * ek hi naam maane jaayen. Speech recognition aksar naam Hindi lipi mein deta hai jabki contact English mein save hota hai.
 * Pure, unit-tested.
 */
object Translit {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
    )
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e",
    )
    private val matras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e",
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    /** "पापा" -> "paapaa", "मम्मी जी" -> "mammee jee". Anything that is not Devanagari is kept as it is. */
    fun toLatin(s: String): String {
        if (!hasDevanagari(s)) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val base = consonants[ch]
            when {
                base != null -> {
                    var cons = base
                    var j = i + 1
                    if (j < s.length && s[j] == NUKTA) { // ज + ़ = ज़
                        cons = when (ch) { 'ज' -> "z"; 'फ' -> "f"; 'ड' -> "r"; 'ढ' -> "rh"; 'क' -> "q"; else -> base }
                        j++
                    }
                    out.append(cons)
                    val next = s.getOrNull(j)
                    when {
                        next == VIRAMA -> j++                         // half letter: no vowel
                        next != null && matras.containsKey(next) -> { out.append(matras.getValue(next)); j++ }
                        // The inherent "a" is silent at the end of a word: राम = "ram", not "rama".
                        next == null || !(next in 'ऀ'..'ॿ') -> Unit
                        else -> out.append('a')
                    }
                    i = j
                }
                vowels.containsKey(ch) -> { out.append(vowels.getValue(ch)); i++ }
                ch == 'ं' || ch == 'ँ' -> { out.append('n'); i++ }
                ch == 'ः' -> { out.append('h'); i++ }
                ch in 'ऀ'..'ॿ' -> i++                       // other signs: drop
                else -> { out.append(ch); i++ }
            }
        }
        return out.toString()
    }

    /** Rough "sounds like" key for names: long vowels shortened, doubled letters merged, a few look-alikes joined. */
    fun fold(s: String): String {
        var t = toLatin(s).lowercase()
        t = t.replace("ph", "f").replace("w", "v").replace("z", "j").replace("ck", "k").replace("q", "k")
        t = t.replace("ee", "i").replace("oo", "u").replace("aa", "a").replace("ou", "u")
        t = Regex("""y\b""").replace(t, "i")
        t = Regex("""(.)\1+""").replace(t, "$1")
        // "n" from a nasal dot before a consonant is often not written in Roman ("हाँ" / "han").
        return t
    }
}
