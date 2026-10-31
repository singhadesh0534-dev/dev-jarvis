package com.adesh.devjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * JARVIS ke "haath" (Phase 3: sirf global actions — lock, screenshot, home, back).
 * Bonus: jab ye ON ho, Android JARVIS ko background se apps/calls kholne deta hai.
 * Phase 6 mein yahi screen padhne aur tap karne ke kaam aayega.
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: JarvisAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        /** One of [AccessibilityService.GLOBAL_ACTION_HOME] etc. False if the service is off. */
        fun global(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
