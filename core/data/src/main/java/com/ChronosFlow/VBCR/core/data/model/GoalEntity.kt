package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "goals",
    indices = [Index("isCompleted"), Index("targetDate")]
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val targetValue: Int,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val progressValue: Int,
    val isCompleted: Boolean
)
