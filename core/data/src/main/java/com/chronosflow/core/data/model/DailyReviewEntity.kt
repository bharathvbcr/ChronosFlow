package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val date: LocalDate,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val driftMinutes: Int,
    val completedBlockCount: Int,
    val missedBlockCount: Int,
    val insightsJson: String
)
