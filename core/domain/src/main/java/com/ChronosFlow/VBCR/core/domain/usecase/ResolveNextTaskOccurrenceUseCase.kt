package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class ResolveNextTaskOccurrenceUseCase @Inject constructor() {
    operator fun invoke(
        schedule: TaskSchedule,
        afterDate: LocalDate = schedule.lastCompletedOccurrenceDate ?: schedule.recurrenceRule.startsOn.minusDays(1)
    ): LocalDate? {
        val rule = schedule.recurrenceRule
        val candidateStart = maxOf(rule.startsOn, afterDate.plusDays(1))
        val hardEnd = rule.endsOn
        val maxOccurrences = rule.maxOccurrences
        var candidate = rule.startsOn
        var matchedOccurrences = 0
        val safetyBound = hardEnd ?: candidateStart.plusYears(5)
        while (!candidate.isAfter(safetyBound)) {
            if (rule.matches(candidate)) {
                matchedOccurrences += 1
                if (maxOccurrences != null && matchedOccurrences > maxOccurrences) {
                    return null
                }
                if (!candidate.isBefore(candidateStart)) {
                    return candidate
                }
            }
            candidate = candidate.plusDays(1)
        }
        return null
    }

    fun occurrencesThrough(
        schedule: TaskSchedule,
        throughDate: LocalDate,
        afterDate: LocalDate = schedule.lastCompletedOccurrenceDate ?: schedule.recurrenceRule.startsOn.minusDays(1),
        maxOccurrences: Int = DEFAULT_MAX_OCCURRENCES
    ): List<LocalDate> {
        if (maxOccurrences <= 0) return emptyList()
        val occurrences = mutableListOf<LocalDate>()
        var cursor = afterDate
        while (occurrences.size < maxOccurrences) {
            val next = invoke(schedule, afterDate = cursor) ?: break
            if (next.isAfter(throughDate)) break
            occurrences += next
            cursor = next
        }
        return occurrences
    }

    private fun TaskRecurrenceRule.matches(date: LocalDate): Boolean {
        if (date.isBefore(startsOn)) return false
        if (endsOn != null && date.isAfter(endsOn)) return false
        return when (this) {
            is TaskRecurrenceRule.Daily -> {
                val daysBetween = ChronoUnit.DAYS.between(startsOn, date)
                daysBetween % intervalDays.toLong() == 0L
            }
            is TaskRecurrenceRule.Weekly -> {
                if (date.dayOfWeek !in weekdays) return false
                val anchorWeek = startsOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val candidateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeksBetween = ChronoUnit.WEEKS.between(anchorWeek, candidateWeek)
                weeksBetween % intervalWeeks.toLong() == 0L
            }
            is TaskRecurrenceRule.MonthlyByDayOfMonth -> {
                val monthsBetween = ChronoUnit.MONTHS.between(
                    YearMonth.from(startsOn),
                    YearMonth.from(date)
                )
                monthsBetween >= 0 &&
                    monthsBetween % intervalMonths.toLong() == 0L &&
                    date.dayOfMonth == minOf(dayOfMonth, date.lengthOfMonth())
            }
            is TaskRecurrenceRule.MonthlyByOrdinalWeekday -> {
                val monthsBetween = ChronoUnit.MONTHS.between(
                    YearMonth.from(startsOn),
                    YearMonth.from(date)
                )
                monthsBetween >= 0 &&
                    monthsBetween % intervalMonths.toLong() == 0L &&
                    date == resolveOrdinalWeekdayDate(YearMonth.from(date), ordinal, weekday)
            }
        }
    }

    private fun resolveOrdinalWeekdayDate(
        month: YearMonth,
        ordinal: Int,
        weekday: DayOfWeek
    ): LocalDate {
        val firstDay = month.atDay(1)
        return if (ordinal > 0) {
            firstDay.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, weekday))
        } else {
            // ordinal == -1: last; ordinal == -2: second-to-last; etc. (iCal BYDAY semantics)
            firstDay.with(TemporalAdjusters.lastInMonth(weekday)).minusWeeks((-ordinal - 1).toLong())
        }
    }

    private companion object {
        const val DEFAULT_MAX_OCCURRENCES = 32
    }
}
