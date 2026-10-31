package com.adesh.devjarvis.core

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val network: NetworkKind,
    val nextAlarmMillis: Long?,
)

enum class NetworkKind(val label: String) { WIFI("WI-FI"), CELLULAR("MOBILE"), OTHER("ONLINE"), NONE("OFFLINE") }

/** Battery, network, next alarm — HUD aur brain ke context block dono ke liye. */
@Singleton
class DeviceStatus @Inject constructor(@ApplicationContext private val context: Context) {

    fun snapshot(): DeviceSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val alarm = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
        return DeviceSnapshot(percent, charging, network(), alarm)
    }

    fun network(): NetworkKind {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.NONE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkKind.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.OTHER
        }
    }

    fun isOnline(): Boolean = network() != NetworkKind.NONE
}
