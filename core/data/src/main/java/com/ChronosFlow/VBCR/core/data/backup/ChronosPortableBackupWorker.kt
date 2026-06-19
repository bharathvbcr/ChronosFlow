package com.ChronosFlow.VBCR.core.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

class ChronosPortableBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ChronosPortableBackupWorkerEntryPoint::class.java
        )

        return runCatching {
            entryPoint.portableBackupRepository().refreshSnapshot()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "chronosflow_portable_backup_refresh"

        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ChronosPortableBackupWorker>().build()
    }
}

@Singleton
class ChronosPortableBackupScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun enqueueRefresh(): Operation =
        WorkManager.getInstance(context).enqueueUniqueWork(
            ChronosPortableBackupWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            ChronosPortableBackupWorker.oneTimeRequest()
        )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChronosPortableBackupWorkerEntryPoint {
    fun portableBackupRepository(): ChronosPortableBackupRepository
}
