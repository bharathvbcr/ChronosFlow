package com.ChronosFlow.VBCR.core.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class MoodEnergyCheckIn(
    val id: String,
    val blockId: String?,
    val moodScore: Int,
    val stressScore: Int,
    val energyScore: Int,
    val focusScore: Int,
    val notes: String?,
    val recordedAt: LocalDateTime,
    val checkInDate: LocalDate
)
