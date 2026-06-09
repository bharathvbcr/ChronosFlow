package com.chronosflow.core.domain.model

const val CALENDAR_EVENT_CATEGORY = "CALENDAR"
const val ALL_DAY_CALENDAR_EVENT_CATEGORY = "CALENDAR_ALL_DAY"
const val ALL_DAY_CALENDAR_MARKER_MINUTES = 15

fun TimeBlock.isAllDayCalendarImport(): Boolean {
    return provenance == BlockProvenance.CALENDAR_IMPORTED &&
        calendarEventId != null &&
        (
            category.equals(ALL_DAY_CALENDAR_EVENT_CATEGORY, ignoreCase = true) ||
                durationMinutes >= 1440
            )
}

fun TimeBlock.occupiesScheduleTime(): Boolean = !isAllDayCalendarImport()

fun TimeBlock.isFocusSuggestionCandidate(): Boolean {
    return occupiesScheduleTime() &&
        actualEndMinuteOfDay == null
}
