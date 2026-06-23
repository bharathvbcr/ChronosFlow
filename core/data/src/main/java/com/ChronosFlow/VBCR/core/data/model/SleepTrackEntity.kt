package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "sleep_tracks",
    indices = [Index(value = ["date"], unique = true)]
)
data class SleepTrackEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val plannedStartMinute: Int?,
    val plannedEndMinute: Int?,
    val actualStartMinute: Int?,
    val actualEndMinute: Int?,
    val sleepQuality: Int,
    val windDownNotes: String?,
    val interruptedCount: Int,
    val refreshedRating: Int? = null,
    val source: String = "MANUAL"
)
