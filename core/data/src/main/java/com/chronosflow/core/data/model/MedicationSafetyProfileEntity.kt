package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_safety_profiles")
data class MedicationSafetyProfileEntity(
    @PrimaryKey val medicationPlanId: String,
    val form: String,
    val route: String,
    val strength: String?,
    val instructions: String?,
    val mealTiming: String,
    val supplyRemaining: Int?,
    val refillThreshold: Int?,
    val pharmacyName: String?,
    val prescriberName: String?,
    val cautionsCsv: String?
)
