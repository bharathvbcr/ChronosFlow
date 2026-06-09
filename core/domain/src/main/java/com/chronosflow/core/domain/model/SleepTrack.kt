package com.chronosflow.core.domain.model

import java.time.LocalDate

data class SleepTrack(
    val id: String,
    val date: LocalDate,
    val plannedStartMinute: Int?,
    val plannedEndMinute: Int?,
    val actualStartMinute: Int?,
    val actualEndMinute: Int?,
    val sleepQuality: Int,
    val windDownNotes: String?,
    val interruptedCount: Int
)
