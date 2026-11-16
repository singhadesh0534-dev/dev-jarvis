package com.adesh.devjarvis.apps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.adesh.devjarvis.BuildConfig
import com.adesh.devjarvis.R
import com.adesh.devjarvis.cloud.FactoryRest
import com.adesh.devjarvis.core.ActivityLog
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Ise asli app bana do": the app JARVIS wrote goes to GitHub's free cloud (App Factory workflow) with Sir's token,
 * comes back as a signed APK and opens Android's installer — all from inside JARVIS. Progress shows on the HUD and in
 * one notification; the token is never logged.
 */
@Singleton
class ApkFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val activityLog: ActivityLog,
) {
    /** What the factory is doing now, for the HUD ("" when idle). */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val repo = BuildConfig.UPDATE_REPO
    private val json = "application/json".toMediaType()
    private val slow = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build()

    val hasToken: Boolean get() = settings.current.githubToken.isNotBlank()
    val busy: Boolean get() = job?.isActive == true

    /** Starts the build in the background; returns at once. */
    fun start(name: String, html: String) {
        if (busy) return
        job = scope.launch {
            val ok = runCatching { build(name, html) }.getOrElse { e ->
                done(name, false, "APK nahi bana: ${e.message ?: e.javaClass.simpleName}")
                false
            }
            if (!ok) SessionTrace.add("FACTORY failed for $name")
        }
    }

    private suspend fun build(name: String, html: String): Boolean {
        val slug = FactoryRest.slug(name)
        progress(name, "GitHub cloud pe bhej raha hoon…")
        // 1. the jarvis-apps branch (made once, from the branch this JARVIS was built from)
        val (refCode, refBody) = call(get(FactoryRest.refUrl(repo, FactoryRest.BRANCH)))
        if (refCode == 404) {
            val (bCode, bBody) = call(get(FactoryRest.refUrl(repo, BuildConfig.FACTORY_BASE)))
            val base = FactoryRest.refSha(bBody) ?: return done(name, false, FactoryRest.errorText(bCode))
            val (cCode, _) = call(post(FactoryRest.createRefUrl(repo), FactoryRest.createRefBody(FactoryRest.BRANCH, base)))
            if (cCode !in 200..299) return done(name, false, FactoryRest.errorText(cCode))
        } else if (refCode !in 200..299) return done(name, false, FactoryRest.errorText(refCode))
        // 2. the app's HTML (replacing the old version of the same app)
        val (_, oldBody) = call(get(FactoryRest.contentGetUrl(repo, slug)))
        val existing = FactoryRest.fileSha(oldBody)
        val (putCode, putBody) = call(put(FactoryRest.contentUrl(repo, slug), FactoryRest.putBody(slug, name, html, existing)))
        val commit = FactoryRest.commitSha(putBody) ?: return done(name, false, FactoryRest.errorText(putCode))
        // 3. follow the cloud build (usually 3-6 minutes)
        progress(name, "Cloud pe APK ban raha hai… (3-6 min)")
        val started = System.currentTimeMillis()
        var run: FactoryRest.Run? = null
        while (System.currentTimeMillis() - started < 15 * 60_000L) {
            delay(10_000)
            val (code, body) = call(get(FactoryRest.runsUrl(repo, commit)))
            if (code == 401 || code == 403) return done(name, false, FactoryRest.errorText(code))
            run = FactoryRest.factoryRun(body) ?: continue
            if (run.status == "completed") break
            val mins = (System.currentTimeMillis() - started) / 60_000
            progress(name, if (run.status == "in_progress") "APK ban raha hai… ${mins + 1} min" else "Cloud mein line mein hai…")
        }
        val r = run ?: return done(name, false, "Cloud build shuru nahi hua — thodi der baad phir boliye.")
        if (r.status != "completed") return done(name, false, "Cloud build bahut der le raha hai — baad mein 'APK kahan hai' poochiye.")
        if (r.conclusion != "success") return done(name, false, "Cloud build fail hua (${r.conclusion}). Dekh: ${FactoryRest.runPage(repo, r.id)}")
        // 4. download the APK
        progress(name, "APK download ho raha hai…")
        val (relCode, relBody) = call(get(FactoryRest.releaseUrl(repo, slug)))
        val asset = FactoryRest.apkAssetUrl(relBody) ?: return done(name, false, FactoryRest.errorText(relCode))
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "app-$slug.apk")
        val dl = withContext(Dispatchers.IO) {
            runCatching {
                slow.newCall(get(asset).header("Accept", "application/octet-stream").build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use resp.code
                    resp.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    200
                }
            }.getOrDefault(-1)
        }
        if (dl != 200 || file.length() < 10_000) return done(name, false, if (dl == -1) "Net nahi hai — APK download nahi hua." else FactoryRest.errorText(dl))
        // 5. install
        return done(name, true, "$name ka APK taiyaar (${file.length() / 1024} KB). Install ka popup khul raha hai — Install dabaiye.", file)
    }

    private fun done(name: String, ok: Boolean, message: String, apk: File? = null): Boolean {
        _status.value = ""
        activityLog.add("make_apk", "name=$name", ok, message)
        SessionTrace.add("FACTORY ${if (ok) "ok" else "fail"} $name")
        notify(name, message, apk)
        if (apk != null) install(apk)
        return ok
    }

    private fun progress(name: String, text: String) {
        _status.value = "$name: $text"
        notify(name, text, null, ongoing = true)
    }

    /** Opens Android's installer (first time: the "allow installs from JARVIS" switch). */
    fun install(apk: File) {
        runCatching {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            context.startActivity(installIntent(apk))
        }
    }

    private fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun notify(name: String, text: String, apk: File?, ongoing: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App Factory (APK)", NotificationManager.IMPORTANCE_DEFAULT))
        val tap = apk?.let { PendingIntent.getActivity(context, 7101, installIntent(it), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(if (apk != null) "$name — APK taiyaar, tap karke install karein" else "JARVIS App Factory: $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .apply { if (ongoing) setProgress(0, 0, true) }
            .apply { tap?.let { setContentIntent(it) } }
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    // ---- GitHub calls (the token goes only in the header)
    private fun get(url: String) = Request.Builder().url(url).auth().get()
    private fun post(url: String, body: String) = Request.Builder().url(url).auth().post(body.toRequestBody(json))
    private fun put(url: String, body: String) = Request.Builder().url(url).auth().put(body.toRequestBody(json))
    private fun Request.Builder.auth() = header("Authorization", "Bearer ${settings.current.githubToken.trim()}")
        .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")

    private suspend fun call(b: Request.Builder): Pair<Int, String> = withContext(Dispatchers.IO) {
        runCatching { slow.newCall(b.build()).execute().use { it.code to (it.body?.string().orEmpty()) } }.getOrDefault(-1 to "")
    }

    private companion object {
        const val CHANNEL = "jarvis_factory"
        const val NOTIFICATION_ID = 7100
    }
}
