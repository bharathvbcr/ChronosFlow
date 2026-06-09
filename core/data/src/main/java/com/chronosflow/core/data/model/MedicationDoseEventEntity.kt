package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "medication_dose_events",
    indices = [
        Index("medicationPlanId"),
        Index("eventDate"),
        Index(value = ["medicationPlanId", "eventDate"])
    ]
)
data class MedicationDoseEventEntity(
    @PrimaryKey val id: String,
    val medicationPlanId: String,
    val eventType: String,
    val eventDate: LocalDate,
    val recordedAt: Instant,
    val scheduledMinuteOfDay: Int?,
    val reason: String?,
    val doseAmount: String?
)
