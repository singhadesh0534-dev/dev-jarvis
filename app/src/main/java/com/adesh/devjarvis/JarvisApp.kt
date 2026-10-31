package com.adesh.devjarvis

import android.app.Application
import com.adesh.devjarvis.core.AppVisibility
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppVisibility.install(this)
    }
}
