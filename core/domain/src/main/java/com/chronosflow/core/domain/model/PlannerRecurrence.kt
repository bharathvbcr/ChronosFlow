package com.chronosflow.core.domain.model

import java.time.DayOfWeek

enum class PlannerRecurrenceType {
    DAILY,
    WEEKDAYS,
    WEEKENDS,
    SELECTED_WEEKDAYS,
    EVERY_N_DAYS,
    WEEKLY_INTERVAL,
    MULTIPLE_TIMES_DAILY,
    PRN
}

data class PlannerRecurrence(
    val type: PlannerRecurrenceType = PlannerRecurrenceType.DAILY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val timesOfDayMinutes: List<Int> = emptyList()
) {
    val normalizedTimesOfDayMinutes: List<Int>
        get() = timesOfDayMinutes
            .map { ((it % 1440) + 1440) % 1440 }
            .distinct()
            .sorted()
}
