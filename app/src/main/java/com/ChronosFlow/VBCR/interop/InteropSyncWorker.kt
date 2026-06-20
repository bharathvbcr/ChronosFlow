package com.ChronosFlow.VBCR.interop

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Periodically mirrors DevTime (Meridian)'s shared tasks into ChronosFlow via [InteropSyncManager].
 *
 * PRIV-006: This worker will NOT be scheduled unless the user has explicitly granted interop consent
 * ([ChronosPreferencesDataSource.isInteropConsentGranted] == true). Call [ensureScheduled] on every
 * app startup — it is a no-op when consent has not been granted, and cancels any previously-scheduled
 * work if consent is revoked so the worker never runs without explicit permission.
 */
class InteropSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 5) return Result.failure()
        // Double-check consent inside doWork in case it was revoked after the worker was enqueued.
        val prefs = EntryPointAccessors.fromApplication(
            applicationContext,
            InteropSyncWorkerEntryPoint::class.java,
        ).chronosPreferencesDataSource()
        if (!prefs.isInteropConsentGranted()) {
            Log.i(TAG, "Interop consent not granted — skipping sync and cancelling future runs.")
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }
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
        private const val TAG = "InteropSyncWorker"

        /**
         * Enqueue the periodic DevTime sync only when the user has granted interop consent.
         * Cancels the unique work when consent has not been granted, so previously-scheduled
         * instances from before the PRIV-006 gate was added are also cleaned up.
         */
        fun ensureScheduled(context: Context) {
            val prefs = context.getSharedPreferences("chronos_preferences", Context.MODE_PRIVATE)
            val consentGranted = prefs.getBoolean("interop.consent.granted", false)
            if (!consentGranted) {
                // Cancel any work that may have been scheduled before this consent gate was added.
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
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
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InteropSyncWorkerEntryPoint {
    fun interopSyncManager(): InteropSyncManager
    fun chronosPreferencesDataSource(): ChronosPreferencesDataSource
}
