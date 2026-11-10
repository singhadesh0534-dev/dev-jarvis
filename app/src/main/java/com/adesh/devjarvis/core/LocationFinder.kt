package com.adesh.devjarvis.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone ki location — sirf tab jab koi tool maange (SOS, place reminders). Background tracking nahi.
 * Plain LocationManager, no Play Services dependency.
 */
@Singleton
class LocationFinder @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    private fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** A fresh fix if one arrives within [timeoutMs], else the newest last-known fix, else null. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 8_000L): Location? {
        val lm = manager ?: return null
        if (!hasPermission()) return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
            .filter { it != LocationManager.PASSIVE_PROVIDER }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return last
        val fresh = runCatching { withTimeoutOrNull(timeoutMs) { requestOnce(lm, providers) } }.getOrNull()
        return fresh ?: last
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(lm: LocationManager, providers: List<String>): Location? {
        if (providers.isEmpty()) return null
        val result = CompletableDeferred<Location>()
        val cancel = CancellationSignal()
        val listeners = mutableListOf<LocationListener>()
        try {
            withContext(Dispatchers.Main) {
                providers.forEach { provider ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 30) {
                            lm.getCurrentLocation(provider, cancel, context.mainExecutor) { loc ->
                                if (loc != null) result.complete(loc)
                            }
                        } else {
                            // Explicit object: older Android calls the other callbacks, which have no default there.
                            val listener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    result.complete(location)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                                override fun onProviderEnabled(provider: String) = Unit
                                override fun onProviderDisabled(provider: String) = Unit
                            }
                            listeners += listener
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        }
                    }
                }
            }
            return result.await()
        } finally {
            cancel.cancel()
            listeners.forEach { runCatching { lm.removeUpdates(it) } }
        }
    }

    private companion object {
        const val FRESH_MS = 60_000L
    }
}
