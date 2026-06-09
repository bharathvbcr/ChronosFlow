package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "medication_schedules",
    indices = [
        Index(value = ["medicationPlanId"], unique = true),
        Index("pausedUntil")
    ]
)
data class MedicationScheduleEntity(
    @PrimaryKey val id: String,
    val medicationPlanId: String,
    val recurrenceType: String,
    val intervalCount: Int,
    val weekdaysCsv: String?,
    val doseTimesCsv: String,
    val plannerVisible: Boolean,
    val pausedUntil: LocalDate?,
    val windowMinutes: Int,
    val isPrn: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)
