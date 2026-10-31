package com.adesh.devjarvis.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Roz ke API requests ginta hai — HUD mein "FUEL xx%" dikhane ke liye. Har din reset. */
@Singleton
class UsageTracker @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val _fuelPercent = MutableStateFlow(computeFuel())
    val fuelPercent: StateFlow<Int> = _fuelPercent.asStateFlow()

    fun recordGeminiRequest() {
        val today = LocalDate.now().toString()
        val count = if (prefs.getString(KEY_DAY, null) == today) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit().putString(KEY_DAY, today).putInt(KEY_COUNT, count + 1).apply()
        _fuelPercent.value = computeFuel()
    }

    /** 429 aaya matlab aaj ka quota khatam, chahe hamari ginti kuch bhi kahe. */
    fun markExhausted() {
        prefs.edit().putString(KEY_DAY, LocalDate.now().toString())
            .putInt(KEY_COUNT, JarvisConfig.GEMINI_DAILY_REQUEST_BUDGET).apply()
        _fuelPercent.value = 0
    }

    fun refresh() {
        _fuelPercent.value = computeFuel()
    }

    private fun computeFuel(): Int {
        val today = LocalDate.now().toString()
        val used = if (prefs.getString(KEY_DAY, null) == today) prefs.getInt(KEY_COUNT, 0) else 0
        val budget = JarvisConfig.GEMINI_DAILY_REQUEST_BUDGET
        return (100 - used * 100 / budget).coerceIn(0, 100)
    }

    private companion object {
        const val KEY_DAY = "day"
        const val KEY_COUNT = "count"
    }
}
