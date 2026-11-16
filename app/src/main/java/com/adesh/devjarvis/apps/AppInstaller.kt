package com.adesh.devjarvis.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File

/**
 * Puts an app JARVIS built on the home screen as its own icon, so it opens like any installed app: its own name,
 * its own colourful icon, its own place in Recents. Android asks Sir once ("Add to home screen") — that tap is his.
 */
object AppInstaller {

    /** The intent that opens one built app in its own task. */
    fun intentFor(context: Context, file: File): Intent =
        Intent(context, AppViewerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("devjarvis-app://" + file.nameWithoutExtension)) // one task per app
            .putExtra(AppViewerActivity.EXTRA_FILE, file.absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

    fun canPin(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Shows Android's "Add to home screen" box for this app. False if the launcher can't pin icons. */
    fun pin(context: Context, file: File, label: String): Boolean {
        if (!canPin(context)) return false
        val info = ShortcutInfoCompat.Builder(context, "app-" + file.nameWithoutExtension)
            .setShortLabel(label.take(24))
            .setLongLabel(label.take(40))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(label)))
            .setIntent(intentFor(context, file))
            .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    /** A bold adaptive icon: a two-tone gradient picked from the name, with its first letter. */
    fun icon(label: String, size: Int = 432): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (a, b) = palette(label)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), a, b, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
        // soft ring, like the JARVIS reactor
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = size * 0.025f; color = 0x55FFFFFF }
        c.drawCircle(size / 2f, size / 2f, size * 0.27f, ring)
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "J"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        c.drawText(letter, size / 2f, y, text)
        return bmp
    }

    private val PALETTES = listOf(
        0xFF00B8D4.toInt() to 0xFF304FFE.toInt(),
        0xFFFF6F00.toInt() to 0xFFD50000.toInt(),
        0xFF00C853.toInt() to 0xFF00796B.toInt(),
        0xFFAA00FF.toInt() to 0xFF304FFE.toInt(),
        0xFFFF4081.toInt() to 0xFF7C4DFF.toInt(),
        0xFFFFC400.toInt() to 0xFFFF6D00.toInt(),
    )

    fun palette(label: String): Pair<Int, Int> = PALETTES[Math.floorMod(label.lowercase().hashCode(), PALETTES.size)]
}
