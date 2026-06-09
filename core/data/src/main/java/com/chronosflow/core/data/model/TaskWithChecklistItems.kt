package com.chronosflow.core.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithChecklistItems(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val checklistItems: List<TaskChecklistItemEntity>,
    @Relation(
        entity = TaskContactSnapshotEntity::class,
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val contactSnapshot: TaskContactWithMethods?,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val taskActions: List<TaskActionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val taskAttachments: List<TaskAttachmentEntity>
)
