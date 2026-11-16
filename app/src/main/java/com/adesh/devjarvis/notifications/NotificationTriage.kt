package com.adesh.devjarvis.notifications

import com.adesh.devjarvis.tools.FuzzyMatcher

/**
 * Sorts Sir's notifications like a good assistant would: people who matter and urgent messages first, ordinary ones
 * next, offers / feeds / system noise last (only counted). Groups several messages from one person. Pure, unit-tested.
 * OTP / bank / payment notifications never get here (they are dropped before storing).
 */
object NotificationTriage {
    data class Item(val key: String, val app: String, val title: String, val text: String, val time: Long, val canReply: Boolean)

    enum class Level { IMPORTANT, NORMAL, LOW }

    private val URGENT = Regex(
        """(?i)\b(urgent|emergency|asap|jaldi|turant|abhi call|call me|call karo|phone karo|help|madad|accident|hospital|kahan ho|where are you)\b|missed call""",
    )
    private val PROMO = Regex(
        """(?i)\b(sale|offer|% off|off on|discount|cashback|coupon|deal|deals|win|winner|lottery|free trial|limited time|shop now|order now|recommended|trending|new video|live now|subscribe|liked your|followers?|reel|stories?)\b""",
    )
    private val SYSTEM = Regex("""(?i)\b(downloading|uploading|usb|charging|battery|syncing|backup|playing|now playing|running in background|screenshot)\b""")
    private val NOISY_APPS = setOf("amazon", "flipkart", "myntra", "meesho", "swiggy", "zomato", "youtube", "play store", "google play", "snapchat", "facebook", "shorts")

    fun isUrgent(text: String) = URGENT.containsMatchIn(text)

    fun level(i: Item, contacts: List<String>, apps: List<String>): Level {
        val all = "${i.title} ${i.text}"
        val fromContact = contacts.any { FuzzyMatcher.score(it, i.title) >= 0.85 }
        val fromApp = apps.any { FuzzyMatcher.score(it, i.app) >= 0.85 }
        return when {
            fromContact -> Level.IMPORTANT
            i.canReply && isUrgent(all) -> Level.IMPORTANT
            "missed call" in all.lowercase() -> Level.IMPORTANT
            SYSTEM.containsMatchIn(all) && !i.canReply -> Level.LOW
            PROMO.containsMatchIn(all) || i.app.lowercase() in NOISY_APPS -> Level.LOW
            i.canReply || fromApp -> Level.NORMAL
            else -> Level.NORMAL
        }
    }

    /** Several messages from the same person within [windowMs]: probably wants an answer now. */
    fun isBurst(items: List<Item>, title: String, now: Long, windowMs: Long = 5 * 60_000L, count: Int = 3): Boolean =
        items.count { it.title.equals(title, ignoreCase = true) && it.canReply && now - it.time in 0..windowMs } >= count

    /** One spoken-friendly summary, important first, noise only counted. */
    fun summary(items: List<Item>, contacts: List<String>, apps: List<String>, hiddenPrivate: Int): String {
        if (items.isEmpty()) return "no notifications" + if (hiddenPrivate > 0) " ($hiddenPrivate private ones hidden)" else ""
        val byLevel = items.sortedByDescending { it.time }.groupBy { level(it, contacts, apps) }
        fun people(list: List<Item>) = list.groupBy { it.title to it.app }.entries.joinToString(" | ") { (who, msgs) ->
            val n = if (msgs.size > 1) " (${msgs.size})" else ""
            "${who.first} on ${who.second}$n: ${msgs.first().text.replace('\n', ' ').take(100)}"
        }
        return buildList {
            byLevel[Level.IMPORTANT]?.let { add("IMPORTANT: " + people(it)) }
            byLevel[Level.NORMAL]?.let { add("OTHERS: " + people(it.take(8))) }
            byLevel[Level.LOW]?.let { low -> add("SKIPPED (offers / feeds / system): " + low.groupBy { it.app }.entries.joinToString(", ") { "${it.key} ${it.value.size}" }) }
            if (hiddenPrivate > 0) add("($hiddenPrivate private OTP/bank ones hidden)")
        }.joinToString("\n")
    }
}
