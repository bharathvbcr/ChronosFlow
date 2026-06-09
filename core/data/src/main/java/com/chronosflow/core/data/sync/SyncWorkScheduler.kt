package com.chronosflow.core.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.Operation
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun enqueueOneTimeSync(): Operation =
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            SyncWorker.oneTimeRequest()
        )
}
