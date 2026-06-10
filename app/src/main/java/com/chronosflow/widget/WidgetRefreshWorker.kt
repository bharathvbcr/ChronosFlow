package com.chronosflow.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * Keeps the home-screen widget reasonably fresh. Glance widgets do not self-update, so the
 * focus countdown and the Now/Next schedule lines would otherwise go stale until the next user
 * interaction.
 *
 * WorkManager's [androidx.work.PeriodicWorkRequest] has a 15-minute minimum interval, so this
 * uses a self-rescheduling one-time request to achieve the shorter [REFRESH_INTERVAL] cadence.
 * The loop is started when the first widget is added and cancelled when the last is removed
 * (see [ChronosGlanceWidgetReceiver]). WorkManager still defers execution under Doze, so the
 * battery cost stays modest.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching { ChronosGlanceWidgetReceiver.refreshAll(applicationContext) }
        // The current run is still RUNNING here, so REPLACE is required to queue the next tick.
        enqueue(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "chronos_widget_refresh"
        private val REFRESH_INTERVAL: Duration = Duration.ofMinutes(10)

        /** Starts the refresh loop without resetting an already-scheduled tick. */
        fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(REFRESH_INTERVAL)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }
    }
}
