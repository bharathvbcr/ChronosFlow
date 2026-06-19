package com.ChronosFlow.VBCR.interop

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Periodically mirrors DevTime (Meridian)'s shared tasks into ChronosFlow via [InteropSyncManager],
 * so DevTime edits show up here even when the user never opens the app — the background counterpart
 * to ChronosFlow exposing its own data through [InteropProvider].
 *
 * Quiet by design: [InteropSyncManager.syncFromPeer] no-ops when DevTime isn't installed or isn't a
 * trusted peer, so the worker is safe to keep scheduled before (or without) the user ever connecting
 * DevTime. Uses the plain [CoroutineWorker] + [EntryPointAccessors] pattern (no @HiltWorker factory),
 * matching [com.ChronosFlow.VBCR.core.data.sync.CalendarBackgroundSyncWorker].
 */
class InteropSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            InteropSyncWorkerEntryPoint::class.java,
        ).interopSyncManager()
        return runCatching {
            manager.syncFromPeer()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_interop_sync"
        const val SYNC_INTERVAL_HOURS = 6L

        /** Enqueue (or update) the periodic DevTime sync. Safe to call on every app startup. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<InteropSyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InteropSyncWorkerEntryPoint {
    fun interopSyncManager(): InteropSyncManager
}
