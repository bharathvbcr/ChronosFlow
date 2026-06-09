package com.chronosflow.core.data.dao

import androidx.room.*
import com.chronosflow.core.data.model.TaskActionEntity
import com.chronosflow.core.data.model.TaskAttachmentEntity
import com.chronosflow.core.data.model.TaskContactMethodEntity
import com.chronosflow.core.data.model.TaskContactSnapshotEntity
import com.chronosflow.core.data.model.TaskEntity
import com.chronosflow.core.data.model.TaskChecklistItemEntity
import com.chronosflow.core.data.model.TaskWithChecklistItems
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY priority DESC, updatedAt DESC")
    fun observeAllTasksWithChecklist(): Flow<List<TaskWithChecklistItems>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskWithChecklistById(id: String): TaskWithChecklistItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItems(items: List<TaskChecklistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaskContactSnapshot(snapshot: TaskContactSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskContactMethods(items: List<TaskContactMethodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskActions(items: List<TaskActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskAttachments(items: List<TaskAttachmentEntity>)

    @Query("DELETE FROM task_checklist_items WHERE taskId = :taskId")
    suspend fun deleteChecklistItemsForTask(taskId: String)

    @Query("DELETE FROM task_contact_methods WHERE taskId = :taskId")
    suspend fun deleteTaskContactMethods(taskId: String)

    @Query("DELETE FROM task_contact_snapshots WHERE taskId = :taskId")
    suspend fun deleteTaskContactSnapshot(taskId: String)

    @Query("DELETE FROM task_actions WHERE taskId = :taskId")
    suspend fun deleteTaskActions(taskId: String)

    @Query("DELETE FROM task_attachments WHERE taskId = :taskId")
    suspend fun deleteTaskAttachments(taskId: String)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Transaction
    suspend fun replaceChecklistItems(taskId: String, items: List<TaskChecklistItemEntity>) {
        deleteChecklistItemsForTask(taskId)
        if (items.isNotEmpty()) {
            insertChecklistItems(items)
        }
    }

    @Transaction
    suspend fun replaceTaskContact(
        taskId: String,
        snapshot: TaskContactSnapshotEntity?,
        methods: List<TaskContactMethodEntity>
    ) {
        deleteTaskContactMethods(taskId)
        deleteTaskContactSnapshot(taskId)
        if (snapshot != null) {
            upsertTaskContactSnapshot(snapshot)
        }
        if (methods.isNotEmpty()) {
            insertTaskContactMethods(methods)
        }
    }

    @Transaction
    suspend fun replaceTaskActions(taskId: String, items: List<TaskActionEntity>) {
        deleteTaskActions(taskId)
        if (items.isNotEmpty()) {
            insertTaskActions(items)
        }
    }

    @Transaction
    suspend fun replaceTaskAttachments(taskId: String, items: List<TaskAttachmentEntity>) {
        deleteTaskAttachments(taskId)
        if (items.isNotEmpty()) {
            insertTaskAttachments(items)
        }
    }
}
