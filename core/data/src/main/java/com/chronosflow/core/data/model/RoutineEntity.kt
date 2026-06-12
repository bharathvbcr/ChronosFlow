package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "routines",
    indices = [Index("isActive")]
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isActive: Boolean,
    val lastCompletedDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineStepEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val title: String,
    val category: String,
    val offsetMinute: Int,
    val durationMinutes: Int,
    val energyLevel: Int,
    val sortOrder: Int
)
