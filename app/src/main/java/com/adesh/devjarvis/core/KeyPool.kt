package com.adesh.devjarvis.core

/**
 * Ek provider ki kai API keys (alag-alag free accounts). Ek ki limit khatam hote hi agli key — bina ruke.
 * Settings mein keys comma / space / nayi line se alag likho. Keys kabhi log nahi hoti. Pure logic, unit-tested.
 */
class KeyPool(private val now: () -> Long = System::currentTimeMillis) {
    private val blockedUntil = HashMap<String, Long>()

    /**
     * One key per line (the Settings boxes are stored one per line; older versions used commas). Inside a key
     * every space or line break is removed: a key never contains one, but copying a long key on a phone often
     * inserts one, which used to cut the key in two. Quotes / brackets around it are dropped.
     */
    fun parse(raw: String): List<String> = raw.split('\n', ',', ';')
        .map { part -> part.filterNot { it.isWhitespace() }.trim('"', '\'', '`', '[', ']', '(', ')', '<', '>') }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Box contents by position (empty boxes kept), for the Settings screen. */
    fun boxes(raw: String): List<String> = raw.split('\n', ',', ';').map { part -> part.filterNot { it.isWhitespace() } }

    /** Settings boxes -> stored value; empty boxes keep their place. */
    fun store(boxes: List<String>): String = boxes.joinToString("\n") { box -> box.filterNot { it.isWhitespace() } }.trimEnd('\n')

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
