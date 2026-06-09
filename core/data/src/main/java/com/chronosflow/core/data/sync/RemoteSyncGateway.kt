package com.chronosflow.core.data.sync

interface RemoteSyncGateway {
    val isConfigured: Boolean

    suspend fun push(batch: RemoteSyncBatch)
}

class RemoteSyncUnavailableException(message: String) : IllegalStateException(message)
