package com.chronosflow.core.data.di

import com.chronosflow.core.data.sync.FirestoreRemoteSyncGateway
import com.chronosflow.core.data.sync.LocalSyncSource
import com.chronosflow.core.data.sync.RemoteSyncGateway
import com.chronosflow.core.data.sync.RoomLocalSyncSource
import com.chronosflow.core.data.sync.SyncMutationNotifier
import com.chronosflow.core.data.sync.WorkManagerSyncMutationNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindLocalSyncSource(source: RoomLocalSyncSource): LocalSyncSource

    @Binds
    abstract fun bindRemoteSyncGateway(gateway: FirestoreRemoteSyncGateway): RemoteSyncGateway

    @Binds
    abstract fun bindSyncMutationNotifier(notifier: WorkManagerSyncMutationNotifier): SyncMutationNotifier
}
