package com.adesh.devjarvis.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** System settings ke deep links. Infinix/Tecno (Transsion XOS) ke liye kuch extra screens bhi. */
object SetupIntents {

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    @Suppress("BatteryLife") // Personal sideloaded assistant: always-on is the whole point.
    fun ignoreBatteryOptimization(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun overlay(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun writeSettings(context: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun defaultAssistant() = listOf(
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
    )

    /** XOS "Phone Master" auto-start manager. Component names vary by XOS version, so try several. */
    fun xosAutoStart(context: Context): List<Intent> = listOf(
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.widget.activity.MainSettingGpActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.widget.activity.MainActivity",
    ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) } + appDetails(context)

    /** Starts the first intent that exists on this phone. */
    fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // ActivityNotFoundException / SecurityException: try the next one.
            }
        }
        return false
    }
}
