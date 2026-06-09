package com.chronosflow.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

class ReminderReconcileWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val reconciler = EntryPointAccessors.fromApplication(
                applicationContext,
                ReminderReconcileEntryPoint::class.java
            ).pendingAlarmReconciler()
            reconciler.reconcile()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
