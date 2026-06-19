package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "habits",
    indices = [Index("isActive"), Index("windowStartMinute"), Index("windowEndMinute"), Index("goalId")]
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val cadence: String,
    val windowStartMinute: Int,
    val windowEndMinute: Int,
    val difficulty: Int,
    val isBundled: Boolean,
    val streakCount: Int,
    val lastCompletedDate: LocalDate?,
    val isActive: Boolean,
    val launchAppLabel: String? = null,
    val launchAppValue: String? = null,
    val goalId: String? = null
)
