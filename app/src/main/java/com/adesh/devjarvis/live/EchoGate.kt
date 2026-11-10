package com.adesh.devjarvis.live

/**
 * JARVIS loudspeaker pe bolta hai to uski apni awaaz mic mein wapas aati hai. Server use "Sir bol rahe hain"
 * samajh ke JARVIS ko beech mein kaat deta tha ("Hello Sir" ka sirf "Hel"). Isliye: jab tak speaker se awaaz
 * nikal rahi hai (aur uske ~350 ms baad tak) mic ka audio server ko silence bana ke jaata hai — hamesha.
 * Earphones lage hon to goonj hoti hi nahi, tab mic khula rehta hai aur Sir beech mein bol ke rok sakte hain.
 * Pure, unit-tested.
 */
class EchoGate(private val tailChunks: Int = 7) {
    private var tail = 0

    /** @return true = send the real mic chunk, false = send silence instead. Call once per ~50 ms chunk. */
    fun pass(playing: Boolean, headphones: Boolean): Boolean {
        if (headphones) {
            tail = 0
            return true
        }
        if (playing) {
            tail = tailChunks
            return false
        }
        if (tail > 0) { // speaker just went quiet: output latency + room echo are still dying away
            tail--
            return false
        }
        return true
    }
}
