package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "focus_sessions",
    indices = [Index("blockId"), Index("state"), Index("startedAt")]
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val blockId: String?,
    val state: String,
    val startedAt: Instant?,
    val plannedEndAt: Instant?,
    val pausedAt: Instant?,
    val completedAt: Instant?,
    val totalSeconds: Int?,
    val updatedAt: Instant
)
