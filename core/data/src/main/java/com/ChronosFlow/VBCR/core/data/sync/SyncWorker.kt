package com.ChronosFlow.VBCR.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )

        return when (val result = entryPoint.syncRepository().pushLocalChanges()) {
            is SyncResult.Pushed -> Result.success(
                workDataOf(
                    KEY_STATUS to STATUS_PUSHED,
                    KEY_TASK_COUNT to result.taskCount,
                    KEY_TIME_BLOCK_COUNT to result.timeBlockCount
                )
            )

            SyncResult.Disabled -> Result.success(
                workDataOf(KEY_STATUS to STATUS_DISABLED)
            )

            is SyncResult.Failed -> {
                if (result.retryable) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            KEY_STATUS to STATUS_FAILED,
                            KEY_ERROR to result.message.take(MAX_ERROR_LENGTH)
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_cloud_sync"
        const val KEY_STATUS = "status"
        const val KEY_TASK_COUNT = "taskCount"
        const val KEY_TIME_BLOCK_COUNT = "timeBlockCount"
        const val KEY_ERROR = "error"
        const val STATUS_PUSHED = "pushed"
        const val STATUS_DISABLED = "disabled"
        const val STATUS_FAILED = "failed"

        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

        private const val MAX_ERROR_LENGTH = 1_000
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
}
