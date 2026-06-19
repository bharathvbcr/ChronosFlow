package com.ChronosFlow.VBCR.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration

/**
 * Keeps the home-screen widget reasonably fresh. Glance widgets do not self-update, so the
 * focus countdown and the Now/Next schedule lines would otherwise go stale until the next user
 * interaction.
 *
 * WorkManager's [androidx.work.PeriodicWorkRequest] has a 15-minute minimum interval, so this
 * uses a self-rescheduling one-time request to achieve the shorter [REFRESH_INTERVAL] cadence.
 * The loop is armed when the first widget is added (see [ChronosWidgetReceiver]) or when a paired
 * watch talks to the phone (see [WearActionListenerService]); each tick keeps itself going only
 * while [shouldKeepRunning] holds, so it self-terminates once neither a widget nor a recently
 * active watch needs it. WorkManager still defers execution under Doze, so the battery cost stays
 * modest.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { ChronosWidgetHub.refreshAll(applicationContext) }
        // Keep ticking only while something still needs refreshing; otherwise let the loop stop.
        // The current run is still RUNNING here, so REPLACE is required to queue the next tick.
        if (shouldKeepRunning(applicationContext)) {
            enqueue(applicationContext, ExistingWorkPolicy.REPLACE)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "chronos_widget_refresh"
        private val REFRESH_INTERVAL: Duration = Duration.ofMinutes(10)

        /** A watch seen within this window keeps the loop alive even with no widgets placed. */
        private val WATCH_KEEPALIVE_WINDOW: Duration = Duration.ofHours(24)

        /** Starts the refresh loop without resetting an already-scheduled tick. */
        fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * Whether the refresh loop should keep running: a home-screen widget is placed, or a paired
         * watch has talked to the phone recently. Lets the loop serve watch-only users without
         * running indefinitely after the watch stops being used.
         */
        fun shouldKeepRunning(context: Context): Boolean {
            if (ChronosWidgetHub.hasAnyWidgets(context)) return true
            return runCatching {
                EntryPointAccessors
                    .fromApplication(context, WidgetActionEntryPoint::class.java)
                    .wearLinkStatusStore()
                    .watchActiveWithin(WATCH_KEEPALIVE_WINDOW.toMillis())
            }.getOrDefault(false)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(REFRESH_INTERVAL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }
    }
}
