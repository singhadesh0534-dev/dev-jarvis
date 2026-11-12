package com.adesh.devjarvis.core

import java.security.MessageDigest

/**
 * Keys that their service REJECTED (wrong, revoked, wrong type) — not keys that are merely at their limit.
 * Settings shows these boxes in red. Only a fingerprint of each key is kept, never the key. Pure, unit-tested.
 */
object BadKeys {
    /** Fingerprints of rejected keys. */
    @Volatile var bad: Set<String> = emptySet()
        private set

    /** The Settings screen listens here so a box turns red (or back) at once. */
    @Volatile var listener: ((Set<String>) -> Unit)? = null

    private var save: ((String) -> Unit)? = null

    /** Called once at app start with what was saved last time and how to save a change. */
    fun init(saved: String, save: (String) -> Unit) {
        bad = saved.split(',').filter { it.isNotBlank() }.toSet()
        this.save = save
    }

    fun fingerprint(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.trim().toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    fun isBad(key: String): Boolean = key.isNotBlank() && fingerprint(key) in bad

    /** The service said this key is not valid. */
    fun mark(key: String) {
        if (key.isNotBlank()) change { it + fingerprint(key) }
    }

    /** The service accepted this key (or only said "limit reached"). */
    fun clear(key: String) = change { it - fingerprint(key) }

    private fun change(f: (Set<String>) -> Set<String>) {
        val before = bad
        val after = f(before)
        if (after == before) return
        bad = after
        listener?.invoke(after)
        save?.invoke(after.joinToString(","))
    }
}
