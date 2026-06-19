package com.ChronosFlow.VBCR.core.data.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChronosPortableBackupInitializer @Inject constructor(
    private val repository: ChronosPortableBackupRepository,
    private val scheduler: ChronosPortableBackupScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            runCatching {
                repository.restoreIfDatabaseEmpty()
                repository.refreshSnapshot()
            }.onFailure {
                scheduler.enqueueRefresh()
            }
        }
    }
}
