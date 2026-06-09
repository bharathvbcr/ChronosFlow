package com.chronosflow.core.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TaskContactWithMethods(
    @Embedded val snapshot: TaskContactSnapshotEntity,
    @Relation(
        parentColumn = "taskId",
        entityColumn = "taskId"
    )
    val methods: List<TaskContactMethodEntity>
)
