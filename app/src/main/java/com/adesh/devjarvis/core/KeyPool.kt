package com.adesh.devjarvis.core

/**
 * Ek provider ki kai API keys (alag-alag free accounts). Ek ki limit khatam hote hi agli key — bina ruke.
 * Settings mein keys comma / space / nayi line se alag likho. Keys kabhi log nahi hoti. Pure logic, unit-tested.
 */
class KeyPool(
    /** Long rests (a rejected key, a used-up day) saved as "fingerprint:until;…" so they survive an app restart. */
    saved: String = "",
    private val onChange: ((String) -> Unit)? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Keyed by a fingerprint of the key, never the key itself. */
    private val blockedUntil = HashMap<String, Long>()

    init {
        saved.split(';').forEach { part ->
            val (fp, until) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
            until.toLongOrNull()?.let { if (it > now()) blockedUntil[fp] = it }
        }
    }

    private fun fp(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private fun until(key: String): Long = blockedUntil[fp(key)] ?: 0L

    /** Is this key resting right now (rejected or at its limit)? */
    @Synchronized
    fun isResting(key: String): Boolean = until(key) > now()

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
        return keys.firstOrNull { until(it) <= t } ?: keys.minByOrNull { until(it) }.orEmpty()
    }

    /** [key] hit its limit (or was rejected): rest it. True if another key is ready right now. */
    @Synchronized
    fun rest(raw: String, key: String, forMs: Long): Boolean {
        blockedUntil[fp(key)] = now() + forMs
        val t = now()
        if (forMs >= LONG_REST_MS) onChange?.invoke(
            blockedUntil.filterValues { it - t >= LONG_REST_MS / 2 }.entries.joinToString(";") { "${it.key}:${it.value}" },
        )
        return parse(raw).any { until(it) <= t }
    }

    /** Box number (1-based) of the key in use now, or 0 when there is none. */
    @Synchronized
    fun currentBox(raw: String): Int {
        val key = current(raw)
        if (key.isEmpty()) return 0
        return boxes(raw).indexOfFirst { it == key }.let { if (it >= 0) it + 1 else parse(raw).indexOf(key) + 1 }
    }

    @Synchronized
    fun readyCount(raw: String): Int = now().let { t -> parse(raw).count { until(it) <= t } }

    private companion object {
        const val LONG_REST_MS = 3_600_000L
    }
}
