package com.adesh.devjarvis.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The key pools in use: text brain, backup brain and Live (Live has its own quota, so its own resting list).
 * Long rests (a rejected key, a used-up day) are remembered across app restarts, by fingerprint only, so a wrong
 * key in box 1 is not tried again on every wake.
 */
@Singleton
class KeyRing @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("key_rest", Context.MODE_PRIVATE)

    private fun pool(name: String) = KeyPool(
        saved = prefs.getString(name, "").orEmpty(),
        onChange = { value -> prefs.edit().putString(name, value).apply() },
    )

    val gemini = pool("gemini")
    val groq = pool("groq")
    /** Third brain (OpenRouter, Cerebras or any OpenAI-compatible service). */
    val extra = pool("extra")
    /** ElevenLabs voice. */
    val eleven = pool("eleven")
    /** Live has its own quota, so its own resting list (same keys). */
    val live = pool("live")
}
