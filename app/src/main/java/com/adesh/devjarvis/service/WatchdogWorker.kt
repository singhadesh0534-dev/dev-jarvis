package com.adesh.devjarvis.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Har 15 min: always-on ON hai par service mar gayi (XOS ne maar diya)? To wapas uthao. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settings(): SettingsRepository
        fun session(): JarvisSession
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        if (!deps.settings().current.alwaysOn) return Result.success()
        // JarvisSession lives on the main thread.
        val running = withContext(Dispatchers.Main) { deps.session().ui.value.alwaysOn }
        if (!running) ServiceController.start(applicationContext)
        return Result.success()
    }
}
