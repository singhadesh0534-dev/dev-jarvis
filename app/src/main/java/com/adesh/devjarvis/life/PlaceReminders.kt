package com.adesh.devjarvis.life

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adesh.devjarvis.core.LocationFinder
import com.adesh.devjarvis.proactive.ProactivePolicy
import com.adesh.devjarvis.proactive.ProactiveSpeaker
import com.adesh.devjarvis.reminders.ReminderScheduler
import dagger.Lazy
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Ghar pahunchun to yaad dilana": jab tak koi aisa reminder baaki hai, har ~15 minute mein ek baar location
 * dekh ke check hota hai. Koi reminder nahi = koi location check nahi.
 */
@Singleton
class PlaceReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LifeStore,
    private val location: LocationFinder,
    private val speaker: Lazy<ProactiveSpeaker>,
) {
    /** Start the periodic check if something is waiting, stop it otherwise. */
    fun schedule() {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (store.get().placeReminders.isEmpty()) {
            wm.cancelUniqueWork(WORK)
        } else {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlaceWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }

    suspend fun check() {
        val data = store.get()
        if (data.placeReminders.isEmpty()) return schedule()
        val fix = location.current(10_000L) ?: return
        val result = PlaceLogic.check(data.places, data.placeReminders, fix.latitude, fix.longitude)
        if (result.remaining != data.placeReminders) store.update { it.copy(placeReminders = result.remaining) }
        result.fire.forEach { r ->
            val line = "Sir, aap ${r.place} pahunch gaye. Yaad dilana tha: ${r.text}"
            ReminderScheduler.notify(context, (r.id % 100_000).toInt() + 200_000, "JARVIS: ${r.place}", r.text)
            runCatching { speaker.get().offer(ProactivePolicy.Kind.REMINDER, line) }
        }
        if (result.remaining.isEmpty()) schedule()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun placeReminders(): PlaceReminders
    }

    private companion object {
        const val WORK = "jarvis_place_reminders"
    }
}

class PlaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching {
            EntryPointAccessors.fromApplication(applicationContext, PlaceReminders.Access::class.java).placeReminders().check()
        }
        return Result.success()
    }
}
