package com.adesh.devjarvis.wakeword

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Battery bachao: chup kamre mein Vosk ko audio mat do (decoding hi CPU khaata hai).
 * Awaaz aate hi gate khulta hai aur pichhle ~300 ms (pre-roll) bhi bhej deta hai, taaki "Hello" ka
 * shuruaati hissa na kate. Awaaz band hone ke ~1.2 s baad gate band. Pure, unit-tested.
 */
class EnergyGate(
    private val preRollChunks: Int = 3,
    private val hangoverChunks: Int = 12,
    private val minThreshold: Double = 250.0,
    private val factor: Double = 2.2,
) {
    sealed interface Result {
        /** Feed these chunks (pre-roll first) to the recognizer. */
        class Feed(val chunks: List<ShortArray>) : Result
        /** Gate just closed: reset the recognizer. */
        data object Closed : Result
        data object Skip : Result
    }

    private var noiseFloor = 120.0
    private var open = false
    private var hangover = 0
    private val preRoll = ArrayDeque<ShortArray>()

    val isOpen: Boolean get() = open

    fun offer(chunk: ShortArray, n: Int): Result {
        val r = rms(chunk, n)
        val loud = r > max(noiseFloor * factor, minThreshold)
        if (!loud && !open) noiseFloor = noiseFloor * 0.97 + r * 0.03
        val copy = chunk.copyOf(n)
        return when {
            loud -> {
                hangover = hangoverChunks
                if (!open) {
                    open = true
                    val out = preRoll.toList() + listOf(copy)
                    preRoll.clear()
                    Result.Feed(out)
                } else {
                    Result.Feed(listOf(copy))
                }
            }
            open && hangover > 0 -> {
                hangover--
                Result.Feed(listOf(copy))
            }
            open -> {
                open = false
                remember(copy)
                Result.Closed
            }
            else -> {
                remember(copy)
                Result.Skip
            }
        }
    }

    private fun remember(c: ShortArray) {
        preRoll.addLast(c)
        while (preRoll.size > preRollChunks) preRoll.removeFirst()
    }

    private fun rms(a: ShortArray, n: Int): Double {
        if (n <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until n) { val v = a[i].toDouble(); sum += v * v }
        return sqrt(sum / n)
    }
}
