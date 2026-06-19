package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.runtime.Immutable

@Immutable
data class DailyReview(
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val completedBlocks: Int
)
