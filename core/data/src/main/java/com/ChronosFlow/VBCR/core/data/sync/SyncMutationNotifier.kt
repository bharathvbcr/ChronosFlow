package com.ChronosFlow.VBCR.core.data.sync

import com.ChronosFlow.VBCR.core.data.backup.ChronosPortableBackupScheduler
import javax.inject.Inject
import javax.inject.Singleton

interface SyncMutationNotifier {
    fun notifyLocalMutation()
}

data object NoOpSyncMutationNotifier : SyncMutationNotifier {
    override fun notifyLocalMutation() = Unit
}

@Singleton
class WorkManagerSyncMutationNotifier @Inject constructor(
    private val scheduler: SyncWorkScheduler,
    private val portableBackupScheduler: ChronosPortableBackupScheduler
) : SyncMutationNotifier {
    override fun notifyLocalMutation() {
        runCatching {
            portableBackupScheduler.enqueueRefresh()
        }
        runCatching {
            scheduler.enqueueOneTimeSync()
        }
    }
}
