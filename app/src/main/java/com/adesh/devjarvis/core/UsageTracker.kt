package com.adesh.devjarvis.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Which free tier is doing the work. */
enum class Provider { GEMINI, LIVE, GROQ }

/**
 * Roz ka free-tier "fuel": Gemini text requests, Live minutes, Groq requests — har din reset.
 * Asli limit Google/Groq decide karte hain; 429 aate hi us provider ko aaj ke liye khaali maan lete hain.
 */
@Singleton
class UsageTracker @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private val _fuel = MutableStateFlow(Provider.entries.associateWith { computeFuel(it) })
    val fuel: StateFlow<Map<Provider, Int>> = _fuel.asStateFlow()

    /** Gemini text fuel, kept for older callers. */
    val fuelPercent: StateFlow<Int> get() = geminiFuel
    private val geminiFuel = MutableStateFlow(computeFuel(Provider.GEMINI))

    fun recordGeminiRequest() = add(Provider.GEMINI, 1)
    fun recordGroqRequest() = add(Provider.GROQ, 1)
    fun recordLiveSeconds(seconds: Int) = add(Provider.LIVE, seconds)

    /** 429 / quota error: treat this provider as empty until local midnight (or [untilMillis]). */
    fun markExhausted(provider: Provider = Provider.GEMINI, untilMillis: Long = nextMidnight()) {
        prefs.edit().putLong(keyBlocked(provider), untilMillis).apply()
        refresh()
    }

    fun isExhausted(provider: Provider): Boolean = System.currentTimeMillis() < prefs.getLong(keyBlocked(provider), 0)

    fun refresh() {
        _fuel.value = Provider.entries.associateWith { computeFuel(it) }
        geminiFuel.value = computeFuel(Provider.GEMINI)
    }

    private fun add(provider: Provider, amount: Int) {
        val today = LocalDate.now().toString()
        val kDay = "day_${provider.name}"
        val kCount = "count_${provider.name}"
        val count = if (prefs.getString(kDay, null) == today) prefs.getInt(kCount, 0) else 0
        prefs.edit().putString(kDay, today).putInt(kCount, count + amount).apply()
        refresh()
    }

    private fun computeFuel(provider: Provider): Int {
        if (isExhausted(provider)) return 0
        val today = LocalDate.now().toString()
        val used = if (prefs.getString("day_${provider.name}", null) == today) prefs.getInt("count_${provider.name}", 0) else 0
        val budget = when (provider) {
            Provider.GEMINI -> JarvisConfig.GEMINI_DAILY_REQUEST_BUDGET
            Provider.LIVE -> JarvisConfig.LIVE_DAILY_MINUTES_BUDGET * 60
            Provider.GROQ -> JarvisConfig.GROQ_DAILY_REQUEST_BUDGET
        }
        return (100 - used * 100L / budget).toInt().coerceIn(0, 100)
    }

    private fun keyBlocked(p: Provider) = "blocked_until_${p.name}"

    private fun nextMidnight(): Long =
        LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
