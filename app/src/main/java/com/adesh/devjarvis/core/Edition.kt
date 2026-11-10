package com.adesh.devjarvis.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
        "look_at_screen", "read_screen", "tap", "long_press", "type_text", "scroll", "swipe", "go_back", "go_home",
        "do_task_on_screen", "screen_watch",
        "read_notifications", "reply_to_notification",
    )

    /** Tools that only make sense when a permission is in the manifest (Lite drops some). */
    val TOOL_NEEDS_PERMISSION = mapOf(
        "set_brightness" to "android.permission.WRITE_SETTINGS",
        "uninstall_app" to "android.permission.REQUEST_DELETE_PACKAGES",
        "emergency_sos" to "android.permission.SEND_SMS",
    )

    /** Is [permission] in this build's manifest? (Lite removes several; requesting those would just fail.) */
    fun declares(context: Context, permission: String): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions?.contains(permission) == true
    }.getOrDefault(false)

    /** Release asset to download for this edition. */
    val apkAssetName: String = if (isFull) "JARVIS.apk" else "JARVIS-lite.apk"
}
