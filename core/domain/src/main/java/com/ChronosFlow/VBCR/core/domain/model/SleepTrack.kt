package com.ChronosFlow.VBCR.core.domain.model

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
    val interruptedCount: Int,
    /** How refreshed the sleeper felt on waking, 1–5; null when not self-reported (e.g. Health Connect imports). */
    val refreshedRating: Int? = null,
    val source: SleepSource = SleepSource.MANUAL
)
