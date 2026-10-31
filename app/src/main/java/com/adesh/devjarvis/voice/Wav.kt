package com.adesh.devjarvis.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM16 mono -> WAV bytes (for Whisper upload). Pure, unit-tested. */
object Wav {
    fun fromPcm16(samples: ShortArray, count: Int, sampleRate: Int): ByteArray {
        val dataBytes = count * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()
        val pcm = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) pcm.putShort(samples[i])
        return ByteArrayOutputStream(44 + dataBytes).apply { write(header); write(pcm.array()) }.toByteArray()
    }
}
