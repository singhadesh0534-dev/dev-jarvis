package com.adesh.devjarvis.core

import javax.inject.Inject
import javax.inject.Singleton

/** The key pools in use: text brain, backup brain and Live (Live has its own quota, so its own resting list). */
@Singleton
class KeyRing @Inject constructor() {
    val gemini = KeyPool()
    val groq = KeyPool()
    /** Live has its own quota, so its own resting list (same keys). */
    val live = KeyPool()
}
