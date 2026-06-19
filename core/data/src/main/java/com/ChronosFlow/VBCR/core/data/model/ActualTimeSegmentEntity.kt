package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "actual_time_segments",
    indices = [Index("date"), Index("blockId")]
)
data class ActualTimeSegmentEntity(
    @PrimaryKey val id: String,
    val blockId: String?,
    val date: LocalDate,
    val startInstant: Instant,
    val endInstant: Instant?,
    val source: String,
    val confidence: Float
)
