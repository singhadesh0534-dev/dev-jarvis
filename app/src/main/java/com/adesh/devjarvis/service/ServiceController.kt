package com.adesh.devjarvis.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adesh.devjarvis.MainActivity
import com.adesh.devjarvis.R
import com.adesh.devjarvis.core.JarvisConfig
import java.util.concurrent.TimeUnit

/** Service start/stop, notifications aur watchdog — sab ek jagah. */
object ServiceController {

    const val CHANNEL_ONLINE = "jarvis_online"
    const val CHANNEL_ALERTS = "jarvis_alerts"
    const val NOTIF_ONLINE = 1
    const val NOTIF_RESUME = 2
    private const val WATCHDOG = "jarvis_watchdog"

    fun hasMicPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Try to start the always-on service. From the background Android may refuse (or start it with a muted
     * mic); in that case the service posts a "tap to bring JARVIS online" notification instead.
     */
    fun start(context: Context): Boolean {
        ensureChannels(context)
        scheduleWatchdog(context)
        if (!hasMicPermission(context)) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, JarvisService::class.java))
            true
        } catch (e: Exception) {
            // Android 12+: ForegroundServiceStartNotAllowedException when we're in the background.
            notifyNeedsTap(context)
            false
        }
    }

    fun stop(context: Context) {
        cancelWatchdog(context)
        context.stopService(Intent(context, JarvisService::class.java))
        NotificationManagerCompat.from(context).cancel(NOTIF_RESUME)
    }

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONLINE, "JARVIS online", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Chhota sa notification jab JARVIS \"Hello Dev\" sun raha ho"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "JARVIS alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Jab JARVIS ko wapas online karne ke liye ek tap chahiye"
            }
        )
    }

    fun onlineNotification(context: Context): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sleep = PendingIntent.getService(
            context, 1,
            Intent(context, JarvisService::class.java).setAction(JarvisService.ACTION_SLEEP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS online")
            .setContentText("\"Hello Dev\" ya \"Jarvis\" boliye")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Sleep", sleep)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Tap on this starts the service from a notification — Android allows the microphone in that case,
     * even after a reboot or after XOS killed us.
     */
    fun notifyNeedsTap(context: Context) {
        ensureChannels(context)
        val wake = PendingIntent.getForegroundService(
            context, 2,
            Intent(context, JarvisService::class.java).setAction(JarvisService.ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS standby mein hai")
            .setContentText("Tap karke online kijiye, Sir — Android ne background mein mic rok diya.")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(wake)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIF_RESUME, notification)
        }
    }

    fun scheduleWatchdog(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(JarvisConfig.WATCHDOG_INTERVAL_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WATCHDOG, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelWatchdog(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHDOG)
    }
}
