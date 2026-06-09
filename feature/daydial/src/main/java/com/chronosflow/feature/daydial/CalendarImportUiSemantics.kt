package com.chronosflow.feature.daydial

import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockProvenance

private const val DAY_IN_MINUTES = 1440

internal fun TimeBlockUiModel.isAllDayCalendarImport(): Boolean {
    return provenance == BlockProvenance.CALENDAR_IMPORTED.name &&
        calendarEventId != null &&
        (
            category.equals(ALL_DAY_CALENDAR_EVENT_CATEGORY, ignoreCase = true) ||
                durationMinutes >= DAY_IN_MINUTES
            )
}
