package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "mood_energy_check_ins",
    indices = [Index("checkInDate"), Index("blockId")]
)
data class MoodEnergyCheckInEntity(
    @PrimaryKey val id: String,
    val checkInDate: LocalDate,
    val recordedAt: Instant,
    val blockId: String?,
    val moodScore: Int,
    val stressScore: Int,
    val energyScore: Int,
    val focusScore: Int,
    val notes: String?
)
