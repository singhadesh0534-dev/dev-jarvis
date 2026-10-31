package com.adesh.devjarvis.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kya JARVIS ki koi screen abhi dikh rahi hai? (Background launch rules + floating bubble iss pe depend karte hain.) */
object AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    val isForeground: Boolean get() = started > 0

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started++
        _foreground.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        _foreground.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
