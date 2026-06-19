package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.ui.graphics.Color

data class TimeBlockUiModel(
    val id: String,
    val title: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val color: Color,
    val isSelected: Boolean = false,
    val provenance: String = "USER_CREATED",
    val flexibility: String = "MOVABLE",
    val isLocked: Boolean = false,
    val isProtected: Boolean = false,
    val actualStartMinuteOfDay: Int? = null,
    val actualEndMinuteOfDay: Int? = null,
    val calendarEventId: Long? = null,
    val taskId: String? = null,
    val habitId: String? = null,
    val medicationPlanId: String? = null,
    val routineId: String? = null,
    val category: String = "WORK"
)
