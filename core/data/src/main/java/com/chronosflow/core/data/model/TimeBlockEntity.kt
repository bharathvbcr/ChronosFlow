package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "time_blocks",
    indices = [
        Index("date"),
        Index("taskId"),
        Index("taskOccurrenceDate"),
        Index("calendarEventId"),
        Index("medicationPlanId"),
        Index("habitId"),
        Index("category"),
        Index("provenance"),
        Index("recurrenceRuleId"),
        Index("goalId"),
        Index("routineId")
    ]
)
data class TimeBlockEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val timezone: String,
    val source: String,
    val provenance: String,
    val flexibility: String,
    val energyLevel: Int,
    val taskId: String?,
    val calendarEventId: Long?,
    val medicationPlanId: String?,
    val habitId: String?,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val recurrenceRuleId: String?,
    val taskOccurrenceDate: LocalDate? = null,
    val actualStartMinuteOfDay: Int?,
    val actualEndMinuteOfDay: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val goalId: String? = null,
    val routineId: String? = null
)

