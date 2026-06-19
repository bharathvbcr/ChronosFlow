package com.ChronosFlow.VBCR.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class FocusSession(
    val id: String,
    val blockId: String?,
    val date: LocalDate,
    val plannedDurationMinutes: Int,
    val actualDurationMinutes: Int?,
    val interruptions: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val notes: String?,
    val isCompleted: Boolean
)
