package com.adesh.devjarvis.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.adesh.devjarvis.core.JarvisConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Live mode ka audio: mic (16 kHz, echo canceller ON) aur speaker (24 kHz).
 * Phone ko "communication mode" mein daalte hain (jaise WhatsApp call) — isi mode mein Android ka echo
 * canceller JARVIS ki apni awaaz ko mic se hata paata hai, taaki aap beech mein bol ke rok sako.
 */
class LiveAudio(
    private val audio: AudioManager,
    private val onMicChunk: (ByteArray, Int) -> Unit,
) {
    @Volatile var micLevel = 0f
        private set
    @Volatile var outLevel = 0f
        private set

    @Volatile private var micRunning = false
    private var micThread: Thread? = null

    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var playerRunning = false
    private var playerThread: Thread? = null
    private var track: AudioTrack? = null
    private val framesWritten = AtomicLong(0)
    private var previousMode = AudioManager.MODE_NORMAL

    fun start() {
        enterCommunicationMode()
        startPlayer()
        startMic()
    }

    /** Give the mic to someone else for a moment (voice confirmation). Playback keeps working. */
    fun pauseMic() {
        micRunning = false
        micThread?.join(1_000)
        micThread = null
        leaveCommunicationMode()
    }

    fun resumeMic() {
        enterCommunicationMode()
        startMic()
    }

    fun enqueue(pcm: ByteArray) {
        queue.offer(pcm)
    }

    /** Server said "interrupted": drop everything not yet played. */
    fun flushPlayback() {
        queue.clear()
        track?.let {
            runCatching { it.pause(); it.flush(); it.play() }
            framesWritten.set(0)
        }
        outLevel = 0f
    }

    /** True while queued or buffered audio is still coming out of the speaker. */
    fun isPlaying(): Boolean {
        if (queue.isNotEmpty()) return true
        val t = track ?: return false
        val head = runCatching { t.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(0L)
        return framesWritten.get() - head > JarvisConfig.LIVE_OUTPUT_RATE / 20 // > 50 ms left
    }

    fun stop() {
        micRunning = false
        playerRunning = false
        micThread?.join(1_000)
        playerThread?.interrupt()
        playerThread?.join(1_000)
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        queue.clear()
        leaveCommunicationMode()
    }

    @SuppressLint("MissingPermission")
    private fun startMic() {
        if (micRunning) return
        micRunning = true
        micThread = Thread({
            val rate = JarvisConfig.LIVE_INPUT_RATE
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
            } catch (e: SecurityException) {
                micRunning = false; return@Thread
            }
            val effects = mutableListOf<AudioEffect>()
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.let { it.enabled = true; effects += it }
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.let { it.enabled = true; effects += it }
            try {
                if (rec.state != AudioRecord.STATE_INITIALIZED) return@Thread
                rec.startRecording()
                val samples = ShortArray(rate / 20) // 50 ms
                val bytes = ByteArray(samples.size * 2)
                while (micRunning) {
                    val n = rec.read(samples, 0, samples.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = samples[i].toInt()
                        sum += v.toDouble() * v
                        bytes[i * 2] = (v and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    micLevel = (sqrt(sum / n) / 4000.0).coerceIn(0.0, 1.0).toFloat()
                    onMicChunk(bytes, n * 2)
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                effects.forEach { runCatching { it.release() } }
                micLevel = 0f
            }
        }, "jarvis-live-mic").apply { start() }
    }

    private fun startPlayer() {
        val rate = JarvisConfig.LIVE_OUTPUT_RATE
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * 4)
            .build()
        track = t
        t.play()
        playerRunning = true
        playerThread = Thread({
            while (playerRunning) {
                val chunk = try { queue.poll(100, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { break }
                if (chunk == null) {
                    if (!isPlaying()) outLevel = 0f
                    continue
                }
                var sum = 0.0
                var i = 0
                while (i + 1 < chunk.size) {
                    val v = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                    sum += v.toDouble() * v
                    i += 2
                }
                outLevel = (sqrt(sum / (chunk.size / 2).coerceAtLeast(1)) / 6000.0).coerceIn(0.0, 1.0).toFloat()
                t.write(chunk, 0, chunk.size)
                framesWritten.addAndGet(chunk.size / 2L)
            }
        }, "jarvis-live-player").apply { start() }
    }

    private fun enterCommunicationMode() {
        previousMode = audio.mode
        runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION }
        // Headset / Bluetooth connected? Use it. Otherwise force the loudspeaker (not the earpiece).
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = audio.availableCommunicationDevices
            val external = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            (external ?: speaker)?.let { runCatching { audio.setCommunicationDevice(it) } }
        } else {
            setSpeakerphone(!audio.isWiredHeadsetOnCompat())
        }
    }

    private fun leaveCommunicationMode() {
        if (Build.VERSION.SDK_INT >= 31) runCatching { audio.clearCommunicationDevice() } else setSpeakerphone(false)
        runCatching { audio.mode = if (previousMode == AudioManager.MODE_IN_COMMUNICATION) AudioManager.MODE_NORMAL else previousMode }
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphone(on: Boolean) {
        audio.isSpeakerphoneOn = on
    }

    private fun AudioManager.isWiredHeadsetOnCompat(): Boolean =
        getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
}
