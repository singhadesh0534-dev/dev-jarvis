package com.adesh.devjarvis.live

/**
 * HD Live audio: JARVIS speaker pe (media stream) bolta hai, to uski apni awaaz mic mein wapas aati hai.
 * Jab tak wo bol raha hai, mic ka audio server ko "silence" bana ke bhejte hain — jab tak Sir saaf, zor se
 * na bolen (barge-in). Pure, unit-tested.
 */
class EchoGate(
    private val threshold: Float = 0.35f,
    private val loudChunksToOpen: Int = 3,
    private val tailChunks: Int = 6,
) {
    private var loud = 0
    private var tail = 0
    private var open = false

    /** @return true = send the real mic chunk, false = send silence instead. Call once per ~50 ms chunk. */
    fun pass(playing: Boolean, level: Float): Boolean {
        if (playing) {
            tail = tailChunks
            loud = if (level >= threshold) loud + 1 else 0
            if (loud >= loudChunksToOpen) open = true // Sir is talking over JARVIS: let it through until he stops playing
            return open
        }
        if (tail > 0) { // speaker just went quiet: room echo is still dying away
            tail--
            if (tail == 0) { open = false; loud = 0 }
            return open
        }
        return true
    }
}
