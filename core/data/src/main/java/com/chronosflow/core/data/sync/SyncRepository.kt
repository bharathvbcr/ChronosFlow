package com.chronosflow.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val localSyncSource: LocalSyncSource,
    private val remoteSyncGateway: RemoteSyncGateway
) {
    suspend fun syncTasks(): SyncResult = pushLocalChanges()

    suspend fun pushLocalChanges(): SyncResult {
        if (!remoteSyncGateway.isConfigured) {
            return SyncResult.Disabled
        }

        return runCatching {
            val snapshot = localSyncSource.snapshot()
            val batch = snapshot.toRemoteBatch()
            remoteSyncGateway.push(batch)
            SyncResult.Pushed(
                taskCount = batch.tasks.size,
                timeBlockCount = batch.timeBlocks.size
            )
        }.getOrElse { error ->
            SyncResult.Failed(
                retryable = error !is RemoteSyncUnavailableException,
                message = error.message ?: error::class.java.simpleName
            )
        }
    }
}

sealed interface SyncResult {
    data class Pushed(
        val taskCount: Int,
        val timeBlockCount: Int
    ) : SyncResult

    data object Disabled : SyncResult

    data class Failed(
        val retryable: Boolean,
        val message: String
    ) : SyncResult
}
