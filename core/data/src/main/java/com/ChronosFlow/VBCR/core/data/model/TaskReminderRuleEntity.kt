package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_reminder_rules",
    foreignKeys = [
        ForeignKey(
            entity = TaskScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskScheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskScheduleId")
    ]
)
data class TaskReminderRuleEntity(
    @PrimaryKey val id: String,
    val taskScheduleId: String,
    val trigger: String,
    val minuteOfDay: Int?,
    val offsetMinutesBefore: Int?,
    val sortOrder: Int
)
