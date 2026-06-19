package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderReconcileScheduler {
    private const val UNIQUE_WORK_NAME = "chronos_reminder_reconcile"
    val reconcileWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
    val reconcileInitialDelaySeconds: Long = 60

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderReconcileWorker>()
            .setInitialDelay(reconcileInitialDelaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                reconcileWorkPolicy,
                request
            )
    }
}
