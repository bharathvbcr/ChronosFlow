package com.chronosflow.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chronosflow.core.domain.repository.CalendarEventRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps imported device-calendar events fresh on a periodic WorkManager schedule, so widgets,
 * reminders, and the next app open already reflect calendar edits without the user opening the
 * app. This complements the in-app triggers (per-date gate, foreground refresh, manual sync):
 * those only run while DayDial is alive, this runs even when it is not.
 *
 * The refresh is quiet by design — [CalendarEventRepository.syncFromDeviceCalendar] no-ops without
 * READ_CALENDAR permission, so the worker is safe to keep scheduled before the user grants it and
 * simply starts populating once access is allowed.
 */
@Singleton
class CalendarBackgroundSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Enqueue (or update) the periodic sync. Safe to call on every app startup. */
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<CalendarBackgroundSyncWorker>(
            CalendarBackgroundSyncWorker.SYNC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CalendarBackgroundSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class CalendarBackgroundSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            CalendarBackgroundSyncEntryPoint::class.java
        ).calendarEventRepository()

        return runCatching {
            val zone = ZoneId.systemDefault()
            val (start, end) = syncWindow(LocalDate.now(zone), zone)
            repository.syncFromDeviceCalendar(start, end)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_calendar_background_sync"
        const val SYNC_INTERVAL_HOURS = 6L
        const val SYNC_WINDOW_DAYS = 7L

        /**
         * Rolling window the background sync refreshes: from the start of [today] through
         * [SYNC_WINDOW_DAYS] days ahead, matching the upcoming week the planner shows.
         */
        fun syncWindow(today: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
            today.atStartOfDay(zone).toInstant() to
                today.plusDays(SYNC_WINDOW_DAYS).atStartOfDay(zone).toInstant()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CalendarBackgroundSyncEntryPoint {
    fun calendarEventRepository(): CalendarEventRepository
}
