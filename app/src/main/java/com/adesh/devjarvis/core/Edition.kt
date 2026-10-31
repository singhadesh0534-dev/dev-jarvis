package com.adesh.devjarvis.core

import com.adesh.devjarvis.BuildConfig

/**
 * FULL = sab kuch. LITE = Accessibility + Notification access ke bina (Play Protect sideload pe block nahi karta).
 */
object Edition {
    val isFull: Boolean = BuildConfig.FLAVOR == "full"
    val label: String = if (isFull) "FULL" else "LITE"

    /** Tools that need the Accessibility or Notification-listener service — hidden from the brain in Lite. */
    val FULL_ONLY_TOOLS = setOf(
        "take_screenshot", "lock_screen", "close_current_app",
        "look_at_screen", "read_screen", "tap", "type_text", "scroll", "go_back", "go_home", "do_task_on_screen",
        "read_notifications", "reply_to_notification",
    )

    /** Release asset to download for this edition. */
    val apkAssetName: String = if (isFull) "JARVIS.apk" else "JARVIS-lite.apk"
}
