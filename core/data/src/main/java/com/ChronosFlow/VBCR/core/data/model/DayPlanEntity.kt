package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val date: LocalDate,
    val timezone: String,
    val status: String, // DRAFT, AI_SUGGESTED, FINALIZED
    val lastAiSuggestionAt: Instant?,
    val totalPlannedMinutes: Int,
    val conflictCount: Int,
    val updatedAt: Instant
)
