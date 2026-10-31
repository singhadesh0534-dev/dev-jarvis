package com.adesh.devjarvis.wakeword

import android.content.Context
import com.adesh.devjarvis.core.JarvisConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Speaker (voiceprint) model — "sirf meri awaaz" ke liye. Ek baar download, phir offline. */
@Singleton
class SpeakerModelManager @Inject constructor(
    @ApplicationContext context: Context,
    private val http: OkHttpClient,
) {
    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val root = File(context.filesDir, "vosk")
    val modelDir = File(root, JarvisConfig.SPEAKER_MODEL_NAME)

    private val _state = MutableStateFlow(if (isInstalled()) State.Ready else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    fun isInstalled(): Boolean =
        File(modelDir, DONE_MARKER).exists() && (modelDir.listFiles()?.any { it.isFile && it.name != DONE_MARKER } == true)

    fun download() {
        if (job?.isActive == true) return
        if (isInstalled()) { _state.value = State.Ready; return }
        job = scope.launch {
            _state.value = State.Downloading(0)
            val tmp = File(root, "spk-download.tmp")
            try {
                root.mkdirs()
                fetch(tmp)
                unzip(tmp)
                File(modelDir, DONE_MARKER).writeText("ok")
                _state.value = State.Ready
            } catch (e: Exception) {
                modelDir.deleteRecursively()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                tmp.delete()
            }
        }
    }

    private suspend fun fetch(target: File) {
        http.newCall(Request.Builder().url(JarvisConfig.SPEAKER_MODEL_URL).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: 13_000_000L
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        _state.value = State.Downloading((done * 100 / total).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
    }

    private fun unzip(zip: File) {
        modelDir.deleteRecursively()
        modelDir.mkdirs()
        val canonicalRoot = modelDir.canonicalPath + File.separator
        var files = 0
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relative.isEmpty()) continue
                val out = File(modelDir, relative)
                if (!out.canonicalPath.startsWith(canonicalRoot)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                    files++
                }
            }
        }
        if (files == 0) error("speaker model empty after unzip")
    }

    private companion object {
        const val DONE_MARKER = ".jarvis_spk_ready"
    }
}
