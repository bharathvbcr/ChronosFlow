package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "habit_events",
    indices = [
        Index("habitId"),
        Index("eventDate"),
        Index(value = ["habitId", "eventDate"]),
        Index(value = ["habitId", "recordedAt"])
    ]
)
data class HabitEventEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val eventType: String,
    val eventDate: LocalDate,
    val recordedAt: Instant,
    val reason: String?,
    val startMinuteOfDay: Int?,
    val endMinuteOfDay: Int?
)
