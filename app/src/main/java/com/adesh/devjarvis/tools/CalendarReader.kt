package com.adesh.devjarvis.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Phone calendar se events padhna (briefing + "aaj kya hai?"). */
@Singleton
class CalendarReader @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** "10:00 AM Team call" lines between [from] and [to]. */
    suspend fun events(from: Long, to: Long): List<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val fmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val out = mutableListOf<String>()
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 10) {
                val title = c.getString(0) ?: continue
                val allDay = c.getInt(2) == 1
                out += if (allDay) "all day: $title" else "${fmt.format(Date(c.getLong(1)))} $title"
            }
        }
        out
    }
}
