package com.adesh.devjarvis.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.adesh.devjarvis.MainActivity
import com.adesh.devjarvis.R

/** Ek tap mein JARVIS se baat: quick-settings tile aur home-screen widget dono yahi intent bhejte hain. */
object QuickAccess {
    const val ACTION_TALK = "com.adesh.devjarvis.action.TALK"

    fun talkIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_TALK)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun talkPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7, talkIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Quick Settings tile: notification shade se ek tap. */
class JarvisTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(QuickAccess.talkPendingIntent(this))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(QuickAccess.talkIntent(this))
        }
    }
}

/** Home-screen widget: mini reactor; tap = JARVIS sunna shuru. */
class JarvisWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_jarvis)
            views.setOnClickPendingIntent(R.id.widget_root, QuickAccess.talkPendingIntent(context))
            manager.updateAppWidget(id, views)
        }
    }
}
