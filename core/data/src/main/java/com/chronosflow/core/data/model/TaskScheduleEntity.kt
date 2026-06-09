package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "task_schedules",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"], unique = true),
        Index("nextOccurrenceDate"),
        Index("generatedThroughDate")
    ]
)
data class TaskScheduleEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val recurrenceType: String,
    val intervalCount: Int,
    val weekdaysCsv: String?,
    val dayOfMonth: Int?,
    val ordinalInMonth: Int?,
    val weekdayInMonth: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val maxOccurrences: Int?,
    val occurrenceMinuteOfDay: Int?,
    val nextOccurrenceDate: LocalDate?,
    val lastCompletedOccurrenceDate: LocalDate?,
    val generatedThroughDate: LocalDate?,
    val isPaused: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
