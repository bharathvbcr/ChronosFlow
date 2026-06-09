package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "medication_plans",
    indices = [Index("isActive"), Index("reminderMinuteOfDay")]
)
data class MedicationPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String,
    val unit: String,
    val notes: String?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val reminderMinuteOfDay: Int,
    val takeWithFood: Boolean,
    val missedCount: Int,
    val refillNeededAfterDoses: Int?,
    val isActive: Boolean
)
