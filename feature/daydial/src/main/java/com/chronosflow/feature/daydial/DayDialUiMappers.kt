package com.chronosflow.feature.daydial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.TimeBlock

fun TimeBlock.toDayDialUiModel(isSelected: Boolean = false): TimeBlockUiModel = TimeBlockUiModel(
    id = id,
    title = title,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    color = when (provenance) {
        BlockProvenance.USER_CREATED -> Color(0xFF4CAF50)
        BlockProvenance.AI_SUGGESTED -> Color(0xFF3F51B5)
        BlockProvenance.TASK_CONVERTED -> Color(0xFFFF9800)
        BlockProvenance.CALENDAR_IMPORTED -> Color(0xFF9E9E9E)
        BlockProvenance.SYSTEM_GENERATED -> Color(0xFF9C27B0)
    },
    isSelected = isSelected,
    provenance = provenance.name,
    flexibility = flexibility.name,
    isLocked = isLocked,
    isProtected = isProtected,
    actualStartMinuteOfDay = actualStartMinuteOfDay,
    actualEndMinuteOfDay = actualEndMinuteOfDay,
    calendarEventId = calendarEventId,
    taskId = taskId,
    habitId = habitId,
    medicationPlanId = medicationPlanId,
    routineId = routineId,
    category = category
)
