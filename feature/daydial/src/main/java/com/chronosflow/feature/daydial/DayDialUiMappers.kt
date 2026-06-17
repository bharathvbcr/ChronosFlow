package com.chronosflow.feature.daydial

import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.ui.theme.ChronosColors

fun TimeBlock.toDayDialUiModel(isSelected: Boolean = false): TimeBlockUiModel = TimeBlockUiModel(
    id = id,
    title = title,
    startMinuteOfDay = startMinuteOfDay,
    durationMinutes = durationMinutes,
    color = when (provenance) {
        BlockProvenance.USER_CREATED -> ChronosColors.ProvenanceUserCreated
        BlockProvenance.AI_SUGGESTED -> ChronosColors.ProvenanceAiSuggested
        BlockProvenance.TASK_CONVERTED -> ChronosColors.ProvenanceTaskConverted
        BlockProvenance.CALENDAR_IMPORTED -> ChronosColors.ProvenanceCalendarImported
        BlockProvenance.SYSTEM_GENERATED -> ChronosColors.ProvenanceSystemGenerated
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
