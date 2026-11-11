package com.adesh.devjarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.core.JarvisConfig
import com.adesh.devjarvis.core.KeyRing
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

/**
 * JARVIS ki awaaz (Classic mode, reminders, confirmations): agar Sir ne ElevenLabs key daali hai aur internet hai
 * to har vaakya ElevenLabs ki awaaz mein, warna phone ka apna TTS. ElevenLabs fail ho (limit khatam, galat key,
 * network) to wahi vaakya turant phone ke TTS se bola jaata hai — JARVIS kabhi chup nahi rehta.
 * Live mode ki awaaz Gemini ki apni hoti hai; ye class wahan use nahi hoti. Key kabhi log nahi hoti.
 */
@Singleton
class JarvisVoice @Inject constructor(
    @ApplicationContext private val context: Context,
    private val local: AndroidTts,
    private val settings: SettingsRepository,
    private val http: OkHttpClient,
    private val device: DeviceStatus,
    private val keys: KeyRing,
) : SpeechOutput {

    // A failure in the voice must never take the app down: note it and carry on with the phone voice.
    private val guard = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        com.adesh.devjarvis.core.ProblemJournal.add("VOICE", "voice worker error: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + guard)
    private val queue = Channel<Pair<Int, String>>(Channel.UNLIMITED)
    private val generation = AtomicInteger(0)
    /** Sentences handed to the cloud queue and not finished yet. */
    private val cloudPending = AtomicInteger(0)
    private val cloudSpeaking = MutableStateFlow(false)
    private val cloudLevel = MutableStateFlow(0f)
    @Volatile private var cloudOffUntil = 0L
    /** Free accounts may be refused raw PCM: then MP3 is used from there on. */
    @Volatile private var pcmRefused = false
    @Volatile private var call: Call? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var player: MediaPlayer? = null

    override val speaking: StateFlow<Boolean> =
        combine(cloudSpeaking, local.speaking) { a, b -> a || b }.stateIn(scope, SharingStarted.Eagerly, false)
    override val level: StateFlow<Float> =
        combine(cloudLevel, local.level) { a, b -> max(a, b) }.stateIn(scope, SharingStarted.Eagerly, 0f)
    override val error: StateFlow<String?> get() = local.error

    init {
        scope.launch {
            for ((gen, text) in queue) {
                try {
                    if (gen == generation.get()) {
                        // A sentence that takes longer than this is stuck: give up on the cloud for it.
                        val spoken = cloudUsable() && kotlinx.coroutines.withTimeoutOrNull(SENTENCE_TIMEOUT_MS) {
                            kotlinx.coroutines.runInterruptible { runCatching { speakCloud(gen, text) }.getOrDefault(false) }
                        } == true
                        if (!spoken) runCatching { call?.cancel() }
                        if (!spoken && gen == generation.get()) {
                            // Same sentence, phone voice, still in order.
                            local.speak(text)
                            kotlinx.coroutines.withTimeoutOrNull(60_000) { local.awaitIdle() }
                        }
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    com.adesh.devjarvis.core.ProblemJournal.add("VOICE", "sentence failed: ${t.javaClass.simpleName}: ${t.message?.take(120)}")
                    cloudOffUntil = System.currentTimeMillis() + 10 * 60_000L
                    runCatching { local.speak(text) }
                } finally {
                    if (cloudPending.decrementAndGet() <= 0) {
                        cloudPending.set(0)
                        cloudSpeaking.value = false
                        cloudLevel.value = 0f
                    }
                }
            }
        }
    }

    private fun cloudUsable(): Boolean =
        settings.current.elevenApiKey.isNotBlank() && System.currentTimeMillis() >= cloudOffUntil && device.isOnline()

    override fun speak(text: String) {
        if (text.isBlank()) return
        // No ElevenLabs key: exactly the old path, nothing in between.
        if (settings.current.elevenApiKey.isBlank() && cloudPending.get() == 0) {
            local.speak(text)
            return
        }
        // Once a reply started in the cloud voice, later sentences queue behind it so nothing overlaps.
        if (cloudPending.get() > 0 || runCatching { cloudUsable() }.getOrDefault(false)) {
            cloudPending.incrementAndGet()
            cloudSpeaking.value = true
            queue.trySend(generation.get() to text)
        } else {
            local.speak(text)
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        runCatching { call?.cancel() }
        runCatching { track?.let { it.pause(); it.flush() } }
        runCatching { player?.stop() }
        local.stop()
    }

    override suspend fun awaitIdle() {
        if (settings.current.elevenApiKey.isBlank() && cloudPending.get() == 0) return local.awaitIdle()
        val giveUpAt = System.currentTimeMillis() + 90_000L
        while (true) {
            while (cloudPending.get() > 0) {
                if (System.currentTimeMillis() > giveUpAt) { // never leave JARVIS waiting on a stuck voice
                    cloudPending.set(0)
                    cloudSpeaking.value = false
                    return
                }
                delay(40)
            }
            local.awaitIdle()
            if (cloudPending.get() == 0) return
        }
    }

    override fun availableVoices(): List<VoiceInfo> = local.availableVoices()
    override fun activeVoiceName(): String? =
        if (cloudUsable()) "ElevenLabs (${settings.current.elevenVoiceId.ifBlank { JarvisConfig.ELEVEN_VOICE_ID }})" else local.activeVoiceName()
    override fun applySettings() = local.applySettings()

    // ---------------------------------------------------------------- ElevenLabs

    /** True if the sentence was spoken in the cloud voice. */
    private fun speakCloud(gen: Int, text: String): Boolean {
        val s = settings.current
        val (autoVoice, autoModel) = choose(keys.eleven.current(s.elevenApiKey))
        val voice = s.elevenVoiceId.trim().ifBlank { autoVoice }
        val body = buildJsonObject {
            put("text", text)
            put("model_id", s.elevenModel.trim().ifBlank { autoModel })
            putJsonObject("voice_settings") { put("stability", 0.5); put("similarity_boost", 0.8) }
        }.toString().toRequestBody(JSON)
        repeat(4) {
            if (gen != generation.get()) return true // stopped: nothing more to say
            val key = keys.eleven.current(s.elevenApiKey)
            if (key.isBlank()) return false
            val format = if (pcmRefused) "mp3_44100_128" else "pcm_24000"
            val request = Request.Builder()
                .url("${JarvisConfig.ELEVEN_BASE_URL}/text-to-speech/$voice/stream?output_format=$format")
                .header("xi-api-key", key)
                .post(body)
                .build()
            try {
                val c = http.newCall(request).also { call = it }
                val outcome: Int = c.execute().use { resp ->
                    val code = resp.code
                    val audio = resp.body
                    when {
                        resp.isSuccessful && audio != null -> {
                            if (pcmRefused) playMp3(gen, audio) else playPcm(gen, audio)
                            DONE
                        }
                        // Wrong key or this account's characters are used up: rest it, try the next key if there is one.
                        code == 401 || code == 402 || code == 429 -> {
                            SessionTrace.add("VOICE eleven http $code, key rested")
                            if (keys.eleven.rest(s.elevenApiKey, key, KEY_REST_MS)) {
                                RETRY
                            } else {
                                cloudOffUntil = System.currentTimeMillis() + 30 * 60_000L
                                FAILED
                            }
                        }
                        (code == 400 || code == 403 || code == 422) && !pcmRefused -> {
                            SessionTrace.add("VOICE eleven http $code for pcm, switching to mp3")
                            pcmRefused = true
                            RETRY
                        }
                        else -> {
                            SessionTrace.add("VOICE eleven http $code, phone voice for 10 min")
                            cloudOffUntil = System.currentTimeMillis() + 10 * 60_000L
                            FAILED
                        }
                    }
                }
                if (outcome == DONE) return true
                if (outcome == FAILED) return false
            } catch (e: IOException) {
                if (gen != generation.get()) return true
                SessionTrace.add("VOICE eleven network ${e.javaClass.simpleName}")
                return false
            } finally {
                call = null
            }
        }
        return false
    }

    /**
     * Sir only gives the key: the best voice and model of that account are looked up once (and remembered),
     * with the built-in defaults when the lookup fails.
     */
    private fun choose(key: String): Pair<String, String> {
        val tag = key.hashCode().toString()
        val prefs = context.getSharedPreferences("eleven_choice", Context.MODE_PRIVATE)
        if (prefs.getString("for", "") == tag) {
            return prefs.getString("voice", JarvisConfig.ELEVEN_VOICE_ID).orEmpty().ifBlank { JarvisConfig.ELEVEN_VOICE_ID } to
                prefs.getString("model", JarvisConfig.ELEVEN_MODEL).orEmpty().ifBlank { JarvisConfig.ELEVEN_MODEL }
        }
        fun fetch(path: String): String? = runCatching {
            http.newCall(Request.Builder().url("${JarvisConfig.ELEVEN_BASE_URL}/$path").header("xi-api-key", key).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
        val voices = fetch("voices")
        val models = fetch("models")
        val voice = ElevenPicker.pickVoice(voices) ?: JarvisConfig.ELEVEN_VOICE_ID
        val model = ElevenPicker.pickModel(models) ?: JarvisConfig.ELEVEN_MODEL
        SessionTrace.add("VOICE eleven chose voice=$voice model=$model (lists: ${voices != null}/${models != null})")
        // Remember only a real answer, so a failed lookup is tried again next time.
        if (voices != null && models != null) prefs.edit().putString("for", tag).putString("voice", voice).putString("model", model).apply()
        return voice to model
    }

    /** Raw 24 kHz PCM, played while it downloads. */
    private fun playPcm(gen: Int, body: ResponseBody) {
        val rate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes())
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf * 4, rate)) // >= 0.5 s
            .build()
        track = t
        var frames = 0L
        try {
            t.play()
            val input = body.byteStream()
            val buf = ByteArray(4_800)
            var kept = 0
            while (gen == generation.get()) {
                val n = input.read(buf, kept, buf.size - kept)
                if (n < 0) break
                val total = kept + n
                val even = total - (total and 1) // whole 16-bit samples only
                if (even > 0) {
                    var sum = 0.0
                    var i = 0
                    while (i + 1 < even) {
                        val v = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
                        sum += v.toDouble() * v
                        i += 2
                    }
                    cloudLevel.value = (sqrt(sum / (even / 2)) / 6000.0).coerceIn(0.0, 1.0).toFloat()
                    t.write(buf, 0, even)
                    frames += even / 2
                }
                kept = total - even
                if (kept == 1) buf[0] = buf[even]
            }
            if (gen == generation.get()) {
                // Let what is still in the buffer come out of the speaker.
                runCatching { t.stop() }
                val deadline = System.currentTimeMillis() + 4_000
                while (gen == generation.get() && System.currentTimeMillis() < deadline &&
                    (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < frames
                ) {
                    Thread.sleep(30)
                }
            }
        } finally {
            track = null
            runCatching { t.pause(); t.flush() }
            runCatching { t.release() }
            cloudLevel.value = 0f
        }
    }

    /** MP3: saved to a small cache file, then played. */
    private fun playMp3(gen: Int, body: ResponseBody) {
        val file = File(context.cacheDir, "jarvis_voice.mp3")
        file.outputStream().use { out -> body.byteStream().copyTo(out) }
        if (gen != generation.get()) return
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(attributes())
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.start()
            cloudLevel.value = 0.7f
            val deadline = System.currentTimeMillis() + p.duration.coerceAtLeast(500) + 3_000
            while (gen == generation.get() && System.currentTimeMillis() < deadline && runCatching { p.isPlaying }.getOrDefault(false)) {
                Thread.sleep(40)
            }
        } finally {
            player = null
            runCatching { p.stop() }
            runCatching { p.release() }
            cloudLevel.value = 0f
        }
    }

    private fun attributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private companion object {
        val JSON = "application/json".toMediaType()
        const val KEY_REST_MS = 6 * 3_600_000L
        const val SENTENCE_TIMEOUT_MS = 25_000L
        const val DONE = 0
        const val RETRY = 1
        const val FAILED = 2
    }
}
