package com.adesh.devjarvis.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

/*
 * The two things that make every screen feel like a live hologram: one frame clock and the phone's tilt.
 * Both are read only inside draw / layer lambdas, so screens never recompose per frame.
 */

/** Seconds since the screen opened, ticking every frame (read it in draw / layer lambdas: no recomposition). */
@Composable
fun rememberHoloClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = ((it - start) / 1_000_000_000.0 % 3600.0).toFloat() }
    }
    return t
}

/**
 * How the phone is tilted, -1..1 on each axis, smoothed and slowly re-centred (holding it at any angle is "straight").
 * Drives the 3D parallax: layers at different depths move differently, like a real hologram.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt: MutableState<Offset> = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var baseX = Float.NaN
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                if (baseX.isNaN()) { baseX = x; baseY = y }
                baseX = baseX * 0.994f + x * 0.006f
                baseY = baseY * 0.994f + y * 0.006f
                val tx = ((x - baseX) / 3.5f).coerceIn(-1f, 1f)
                val ty = ((y - baseY) / 3.5f).coerceIn(-1f, 1f)
                val o = tilt.value
                tilt.value = Offset(o.x + (tx - o.x) * 0.18f, o.y + (ty - o.y) * 0.18f)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}
