package com.ChronosFlow.VBCR.core.data.sync

import com.ChronosFlow.VBCR.core.data.dao.TimeBlockDao
import com.ChronosFlow.VBCR.core.data.model.TimeBlockEntity
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface LocalSyncSource {
    suspend fun snapshot(): LocalSyncSnapshot
}

data class LocalSyncSnapshot(
    val tasks: List<Task>,
    val timeBlocks: List<TimeBlockEntity>
)

@Singleton
class RoomLocalSyncSource @Inject constructor(
    private val taskRepository: TaskRepository,
    private val timeBlockDao: TimeBlockDao
) : LocalSyncSource {
    override suspend fun snapshot(): LocalSyncSnapshot = LocalSyncSnapshot(
        tasks = taskRepository.getAllTasks().first(),
        timeBlocks = timeBlockDao.getAllTimeBlocks()
    )
}

fun LocalSyncSnapshot.toRemoteBatch(): RemoteSyncBatch = RemoteSyncBatch(
    tasks = tasks.map { it.toRemoteEntity() },
    timeBlocks = timeBlocks.map { it.toRemoteEntity() }
)
