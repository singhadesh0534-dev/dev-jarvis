package com.adesh.devjarvis.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.core.SessionSignal
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Always-on JARVIS: foreground service (type microphone) jo "Hello Dev" sunta rehta hai,
 * screen off / lock hone par bhi. Baaki saara kaam [JarvisSession] karta hai.
 */
@AndroidEntryPoint
class JarvisService : Service() {

    @Inject lateinit var session: JarvisSession
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceController.ensureChannels(this)
        scope.launch {
            session.signals.collect { signal ->
                if (signal == SessionSignal.MicSilenced) {
                    // Background se start hue the, Android ne mic mute kar diya. User ka ek tap chahiye.
                    ServiceController.notifyNeedsTap(this@JarvisService)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SLEEP) {
            settings.update { it.copy(alwaysOn = false) }
            ServiceController.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ServiceController.hasMicPermission(this) || !enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESUME) settings.update { it.copy(alwaysOn = true) }
        NotificationManagerCompat.from(this).cancel(ServiceController.NOTIF_RESUME)
        session.setServiceActive(true)
        return START_STICKY
    }

    /** Returns false if Android refused the microphone foreground service (background start). */
    private fun enterForeground(): Boolean {
        if (foreground) return true
        val notification = ServiceController.onlineNotification(this)
        return try {
            ServiceCompat.startForeground(
                this, ServiceController.NOTIF_ONLINE, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            foreground = true
            true
        } catch (e: Exception) {
            // Android 14+: mic FGS from background -> SecurityException. startForeground() must still be
            // called (or the app crashes), so use the special-use type briefly, then stop.
            if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    ServiceCompat.startForeground(
                        this, ServiceController.NOTIF_ONLINE, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                }
            }
            ServiceController.notifyNeedsTap(this)
            false
        }
    }

    override fun onDestroy() {
        session.setServiceActive(false)
        session.stopEverything()
        scope.cancel()
        foreground = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_SLEEP = "com.adesh.devjarvis.action.SLEEP"
        const val ACTION_RESUME = "com.adesh.devjarvis.action.RESUME"
    }
}
