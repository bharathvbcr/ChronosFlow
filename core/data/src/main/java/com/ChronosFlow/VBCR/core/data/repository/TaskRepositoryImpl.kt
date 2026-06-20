package com.ChronosFlow.VBCR.core.data.repository

import androidx.room.withTransaction
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.dao.TaskDao
import com.ChronosFlow.VBCR.core.data.mapper.toDomain
import com.ChronosFlow.VBCR.core.data.mapper.toEntity
import com.ChronosFlow.VBCR.core.data.sync.NoOpSyncMutationNotifier
import com.ChronosFlow.VBCR.core.data.sync.SyncMutationNotifier
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val database: ChronosDatabase,
    private val taskDao: TaskDao,
    private val syncMutationNotifier: SyncMutationNotifier = NoOpSyncMutationNotifier
) : TaskRepository {
    override fun getAllTasks(): Flow<List<Task>> = taskDao.observeAllTasksWithChecklist().map { tasks ->
        tasks.map { it.toDomain() }
    }

    override suspend fun getTaskById(id: String): Task? = taskDao.getTaskWithChecklistById(id)?.toDomain()
    override suspend fun saveTask(task: Task) {
        database.withTransaction {
            taskDao.upsertTask(task.toEntity())
            taskDao.replaceChecklistItems(
                task.id,
                task.checklist.mapIndexed { index, item -> item.toEntity(task.id, index) }
            )
            taskDao.replaceTaskContact(
                task.id,
                task.linkedContact?.toEntity(task.id),
                task.linkedContact.orEmptyMethods(task.id)
            )
            taskDao.replaceTaskActions(
                task.id,
                task.actions.mapIndexed { index, action -> action.toEntity(task.id, index) }
            )
            taskDao.replaceTaskAttachments(
                task.id,
                task.attachments.mapIndexed { index, attachment -> attachment.toEntity(task.id, index) }
            )
        }
        syncMutationNotifier.notifyLocalMutation()
    }
    override suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task.toEntity())
        syncMutationNotifier.notifyLocalMutation()
    }

    private fun TaskContactSnapshot?.orEmptyMethods(taskId: String) = this
        ?.methods
        ?.mapIndexed { index, method -> method.toEntity(taskId, index) }
        .orEmpty()
}
