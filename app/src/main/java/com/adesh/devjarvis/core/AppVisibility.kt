package com.adesh.devjarvis.core

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Kya JARVIS ki koi screen abhi dikh rahi hai? (Background se apps kholne ke rules iss pe depend karte hain.) */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    val isForeground: Boolean get() = started > 0

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) { started++ }
    override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
