package com.adesh.devjarvis.core

/**
 * Ek provider ki kai API keys (alag-alag free accounts). Ek ki limit khatam hote hi agli key — bina ruke.
 * Settings mein keys comma / space / nayi line se alag likho. Keys kabhi log nahi hoti. Pure logic, unit-tested.
 */
class KeyPool(private val now: () -> Long = System::currentTimeMillis) {
    private val blockedUntil = HashMap<String, Long>()

    fun parse(raw: String): List<String> = raw.split(',', ';', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** First key that is not resting; if all are resting, the one that comes back soonest. Blank if none. */
    @Synchronized
    fun current(raw: String): String {
        val keys = parse(raw)
        val t = now()
        return keys.firstOrNull { (blockedUntil[it] ?: 0L) <= t } ?: keys.minByOrNull { blockedUntil[it] ?: 0L }.orEmpty()
    }

    /** [key] hit its limit (or was rejected): rest it. True if another key is ready right now. */
    @Synchronized
    fun rest(raw: String, key: String, forMs: Long): Boolean {
        blockedUntil[key] = now() + forMs
        val t = now()
        return parse(raw).any { (blockedUntil[it] ?: 0L) <= t }
    }

    @Synchronized
    fun readyCount(raw: String): Int = now().let { t -> parse(raw).count { (blockedUntil[it] ?: 0L) <= t } }
}
