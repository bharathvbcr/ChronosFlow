package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "app_usage_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class AppUsageDayEntity(
    @PrimaryKey val date: LocalDate,
    val productiveMinutes: Int,
    val distractingMinutes: Int,
    val neutralMinutes: Int
)
