package com.chronosflow.core.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TaskScheduleWithReminderRules(
    @Embedded val schedule: TaskScheduleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskScheduleId"
    )
    val reminderRules: List<TaskReminderRuleEntity>
)
