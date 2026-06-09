package com.chronosflow.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class TaskRecurrenceType {
    DAILY,
    WEEKLY,
    MONTHLY_DAY_OF_MONTH,
    MONTHLY_ORDINAL_WEEKDAY
}

sealed interface TaskRecurrenceRule {
    val type: TaskRecurrenceType
    val startsOn: LocalDate
    val endsOn: LocalDate?
    val maxOccurrences: Int?

    data class Daily(
        val intervalDays: Int = 1,
        override val startsOn: LocalDate,
        override val endsOn: LocalDate? = null,
        override val maxOccurrences: Int? = null
    ) : TaskRecurrenceRule {
        override val type: TaskRecurrenceType = TaskRecurrenceType.DAILY
    }

    data class Weekly(
        val intervalWeeks: Int = 1,
        val weekdays: Set<DayOfWeek>,
        override val startsOn: LocalDate,
        override val endsOn: LocalDate? = null,
        override val maxOccurrences: Int? = null
    ) : TaskRecurrenceRule {
        override val type: TaskRecurrenceType = TaskRecurrenceType.WEEKLY
    }

    data class MonthlyByDayOfMonth(
        val intervalMonths: Int = 1,
        val dayOfMonth: Int,
        override val startsOn: LocalDate,
        override val endsOn: LocalDate? = null,
        override val maxOccurrences: Int? = null
    ) : TaskRecurrenceRule {
        override val type: TaskRecurrenceType = TaskRecurrenceType.MONTHLY_DAY_OF_MONTH
    }

    data class MonthlyByOrdinalWeekday(
        val intervalMonths: Int = 1,
        val ordinal: Int,
        val weekday: DayOfWeek,
        override val startsOn: LocalDate,
        override val endsOn: LocalDate? = null,
        override val maxOccurrences: Int? = null
    ) : TaskRecurrenceRule {
        override val type: TaskRecurrenceType = TaskRecurrenceType.MONTHLY_ORDINAL_WEEKDAY
    }
}
