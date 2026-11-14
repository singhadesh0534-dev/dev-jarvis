package com.adesh.devjarvis.live

/**
 * Mic chunks (50 ms) -> the fixed frames a speech model wants (Silero: 512 samples at 16 kHz), keeping the latest
 * verdict "is this human speech?" for each chunk. Pure, unit-tested; the model itself is passed in as [classify].
 */
class SpeechFrames(private val frameSize: Int = 512, private val classify: (ShortArray) -> Boolean) {
    private val frame = ShortArray(frameSize)
    private var filled = 0

    /** The latest speech verdict; false until the first full frame has been judged. */
    var speaking = false
        private set

    /** Feed one chunk; returns whether speech is heard now. */
    fun offer(samples: ShortArray, n: Int): Boolean {
        var i = 0
        while (i < n) {
            val take = minOf(frameSize - filled, n - i)
            System.arraycopy(samples, i, frame, filled, take)
            filled += take
            i += take
            if (filled == frameSize) {
                speaking = classify(frame)
                filled = 0
            }
        }
        return speaking
    }
}
