package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "tasks",
    indices = [
        Index("isCompleted"),
        Index("dueDate")
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val priority: Int,
    val dueDate: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val preferredDurationMinutes: Int? = null,
    val preferredStartMinuteOfDay: Int? = null,
    val targetDate: LocalDate? = null
)

@Entity(
    tableName = "task_checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("taskId")
    ]
)
data class TaskChecklistItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val label: String,
    val isCompleted: Boolean,
    val sortOrder: Int
)
