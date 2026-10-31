package com.adesh.devjarvis.tools

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val label: String, val packageName: String)

/** Launcher pe dikhne wale apps + fuzzy naam match ("insta", "yt"). */
@Singleton
class AppRepository @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun find(query: String): InstalledApp? = withContext(Dispatchers.IO) {
        val apps = launcherApps()
        FuzzyMatcher.rank(query, apps, threshold = 0.72) { listOf(it.label) }.firstOrNull()?.item
    }

    fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun launchIntent(app: InstalledApp): Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)

    private fun launcherApps(): List<InstalledApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
    }
}
