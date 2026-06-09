package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.TaskDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.data.sync.NoOpSyncMutationNotifier
import com.chronosflow.core.data.sync.SyncMutationNotifier
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskContactSnapshot
import com.chronosflow.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val syncMutationNotifier: SyncMutationNotifier = NoOpSyncMutationNotifier
) : TaskRepository {
    override fun getAllTasks(): Flow<List<Task>> = taskDao.observeAllTasksWithChecklist().map { tasks ->
        tasks.map { it.toDomain() }
    }

    override suspend fun getTaskById(id: String): Task? = taskDao.getTaskWithChecklistById(id)?.toDomain()
    override suspend fun saveTask(task: Task) {
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
