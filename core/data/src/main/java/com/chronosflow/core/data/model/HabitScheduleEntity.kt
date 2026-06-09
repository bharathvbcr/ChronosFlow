package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "habit_schedules",
    indices = [
        Index(value = ["habitId"], unique = true),
        Index("pausedUntil"),
        Index("skipDate")
    ]
)
data class HabitScheduleEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val recurrenceType: String,
    val intervalCount: Int,
    val weekdaysCsv: String?,
    val targetStartMinute: Int,
    val targetEndMinute: Int,
    val plannerVisible: Boolean,
    val pausedUntil: LocalDate?,
    val skipDate: LocalDate?,
    val deferUntilMinuteOfDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val recurrenceRuleKind: String? = null,
    val quotaTargetCompletions: Int? = null,
    val quotaPeriodUnit: String? = null
)
