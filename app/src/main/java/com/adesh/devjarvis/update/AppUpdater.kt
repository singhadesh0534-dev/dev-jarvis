package com.adesh.devjarvis.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.adesh.devjarvis.BuildConfig
import com.adesh.devjarvis.core.Edition
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Live update: GitHub release se naya APK download karke Android ka installer kholna.
 * Har update pe Android khud "Install" poochhta hai — chupke se kuch install nahi hota.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val versionCode: Int, val versionName: String, val sizeMb: Int) : State
        data class Downloading(val percent: Int) : State
        /** Android's installer is open (or JARVIS needs "Install unknown apps" first). */
        data class Installing(val needsPermission: Boolean) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var latest: UpdateInfo? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${Edition.label}"

    /** Quiet check at most every 6 hours (app start). */
    fun checkIfDue() {
        if (System.currentTimeMillis() - prefs.getLong("last_check", 0) > 6 * 3_600_000L) check()
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State.Checking
            try {
                val info = fetchInfo()
                val asset = info.assets[Edition.apkAssetName]
                _state.value = if (info.versionCode > BuildConfig.VERSION_CODE && asset != null) {
                    State.Available(info.versionCode, info.versionName, (asset.sizeBytes / 1_000_000).toInt())
                } else {
                    State.UpToDate
                }
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Download and open the installer. [edition] = "full" to switch Lite -> Full (same package, data kept;
     * Play Protect may block that one install — see Settings for the two ways around it).
     */
    fun downloadAndInstall(full: Boolean = Edition.isFull) {
        if (job?.isActive == true) return
        if (!Edition.declares(context, android.Manifest.permission.REQUEST_INSTALL_PACKAGES)) {
            // Lite has no install permission: let the browser download it; Sir taps the file to install.
            val name = if (full) "JARVIS.apk" else "JARVIS-lite.apk"
            val url = latest?.assets?.get(name)?.url
                ?: "https://github.com/${BuildConfig.UPDATE_REPO}/releases/download/$RELEASE_TAG/$name"
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            _state.value = State.Installing(needsPermission = false)
            return
        }
        job = scope.launch {
            try {
                val info = latest ?: fetchInfo()
                val name = if (full) "JARVIS.apk" else "JARVIS-lite.apk"
                val asset = info.assets[name] ?: error("$name not in the release")
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, name)
                _state.value = State.Downloading(0)
                http.newCall(Request.Builder().url(asset.url).build()).execute().use { r ->
                    if (!r.isSuccessful) error("download HTTP ${r.code}")
                    val body = r.body ?: error("empty download")
                    val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes.coerceAtLeast(1)
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                _state.value = State.Downloading((done * 100 / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                openInstaller(file)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun fetchInfo(): UpdateInfo {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/tags/$RELEASE_TAG")
            .header("Accept", "application/vnd.github+json")
            .build()
        val info = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("GitHub HTTP ${r.code}")
            UpdateInfo.parse(r.body?.string().orEmpty()) ?: error("release info missing")
        }
        latest = info
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        return info
    }

    private fun openInstaller(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // One-time: Android wants Sir to allow "Install unknown apps" for JARVIS.
            _state.value = State.Installing(needsPermission = true)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        _state.value = State.Installing(needsPermission = false)
    }

    /** After Sir allowed "Install unknown apps": install the file we already downloaded. */
    fun retryInstall() {
        val file = File(context.cacheDir, "updates").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
        if (file != null) openInstaller(file) else downloadAndInstall()
    }

    companion object {
        const val RELEASE_TAG = "jarvis-latest"
    }
}
