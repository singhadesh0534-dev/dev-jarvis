package com.adesh.devjarvis.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * The two things that make every screen feel like a live hologram: one frame clock and the phone's tilt.
 * Both are read only inside draw / layer lambdas, so screens never recompose per frame.
 */

/**
 * How lively the hologram may be on this phone right now: frames per second for the motion, or 0 for a still picture.
 * "Remove animations" (animator scale 0) -> still; battery saver -> 15 fps; otherwise 30 fps — smooth for slow
 * holographic motion and a quarter of the work of redrawing at 120 Hz, so the phone stays cool and responsive.
 */
fun holoFps(context: Context): Int {
    val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    if (scale == 0f) return 0
    val saver = runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
    return if (saver) 15 else 30
}

/** Waits for the next frame that is at least [gapMs] after the last one (capped frame rate) and returns its time. */
private suspend fun nextFrame(gapMs: Long): Long {
    delay((gapMs - 9).coerceAtLeast(1))
    return withFrameNanos { it }
}

/**
 * Seconds since the screen opened, ticking at [holoFps] (read it in draw / layer lambdas: no recomposition).
 * Stays at 0 when the phone asks for no animations.
 */
@Composable
fun rememberHoloClock(): State<Float> {
    val context = LocalContext.current
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val fps = holoFps(context)
        if (fps == 0) return@LaunchedEffect
        val gap = 1000L / fps
        val start = withFrameNanos { it }
        while (true) {
            val now = nextFrame(gap)
            t.floatValue = ((now - start) / 1_000_000_000.0 % 3600.0).toFloat()
        }
    }
    return t
}

/**
 * How the phone is tilted, -1..1 on each axis, smoothed and slowly re-centred (holding it at any angle is "straight").
 * Drives the 3D parallax: layers at different depths move differently, like a real hologram. The sensor only stores
 * its reading; the shown tilt moves at the hologram's frame rate and only when it really changed, so a phone lying
 * still costs nothing.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt: MutableState<Offset> = remember { mutableStateOf(Offset.Zero) }
    val target = remember { FloatArray(2) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = if (holoFps(context) == 0) null else sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseX = Float.NaN
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                if (baseX.isNaN()) { baseX = x; baseY = y }
                baseX = baseX * 0.994f + x * 0.006f
                baseY = baseY * 0.994f + y * 0.006f
                target[0] = ((x - baseX) / 3.5f).coerceIn(-1f, 1f)
                target[1] = ((y - baseY) / 3.5f).coerceIn(-1f, 1f)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    LaunchedEffect(Unit) {
        val fps = holoFps(context)
        if (fps == 0) return@LaunchedEffect
        val gap = 1000L / fps
        while (true) {
            nextFrame(gap)
            val o = tilt.value
            val n = Offset(o.x + (target[0] - o.x) * 0.3f, o.y + (target[1] - o.y) * 0.3f)
            if (abs(n.x - o.x) > 0.002f || abs(n.y - o.y) > 0.002f) tilt.value = n
        }
    }
    return tilt
}
