package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import com.ChronosFlow.VBCR.core.domain.model.buildLegacyHabitSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Shared habit "is this due today?" rules, extracted so the reminder scheduler ([HabitReminderScheduler])
// and the folded-reminder builders ([buildHabitFoldedReminders]) agree on which habits count — a single
// source of truth for recurrence / completion / skip / pause, no logic drift between the alarm pipeline
// and the live "now" notification fold.

/** How many days ahead the scheduler scans for the next due occurrence of a habit. */
internal const val MAX_HABIT_LOOKAHEAD_DAYS = 370

internal val weekdaySet = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

internal val weekendSet = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/**
 * Whether [this] habit's reminder is due on [date] — its recurrence lands on that day and it is not
 * already completed, skipped, or paused for it. [referenceDate] anchors interval recurrences (every-N-
 * days / weekly-interval); callers derive it the same way [nextHabitOccurrence] does (earliest recent
 * event date, else last completion, else today) so scheduling and folding stay in lockstep.
 */
internal fun Habit.isReminderDueOnDate(
    date: LocalDate,
    referenceDate: LocalDate
): Boolean {
    val schedule = this.schedule ?: buildLegacyHabitSchedule(
        habitId = id,
        cadence = cadence,
        windowStartMinute = windowStartMinute,
        windowEndMinute = windowEndMinute,
        plannerVisible = isBundled
    )
    val recurrenceDue = when (val rule = schedule.resolvedRecurrenceRule) {
        is HabitRecurrenceRule.Scheduled -> recurrenceOccursOn(
            date = date,
            recurrence = rule.recurrence,
            startDate = referenceDate
        )
        is HabitRecurrenceRule.Quota -> true
    }
    val completed = lastCompletedDate == date ||
        recentEvents.any { it.eventDate == date && it.type == HabitEventType.COMPLETED }
    val skipped = schedule.skipDate == date ||
        recentEvents.any { it.eventDate == date && it.type == HabitEventType.SKIPPED }
    val paused = schedule.pausedUntil?.let { !it.isBefore(date) } == true
    return recurrenceDue && !completed && !skipped && !paused
}

/** Whether a [recurrence] pattern lands on [date], anchored at [startDate] for interval-based rules. */
internal fun recurrenceOccursOn(
    date: LocalDate,
    recurrence: PlannerRecurrence,
    startDate: LocalDate
): Boolean = when (recurrence.type) {
    PlannerRecurrenceType.DAILY,
    PlannerRecurrenceType.MULTIPLE_TIMES_DAILY -> true
    PlannerRecurrenceType.WEEKDAYS -> date.dayOfWeek in weekdaySet
    PlannerRecurrenceType.WEEKENDS -> date.dayOfWeek in weekendSet
    PlannerRecurrenceType.SELECTED_WEEKDAYS ->
        recurrence.weekdays.isEmpty() || date.dayOfWeek in recurrence.weekdays
    PlannerRecurrenceType.EVERY_N_DAYS -> {
        val days = ChronoUnit.DAYS.between(startDate, date)
        days >= 0 && days % recurrence.interval.coerceAtLeast(1) == 0L
    }
    PlannerRecurrenceType.WEEKLY_INTERVAL -> {
        val allowedDays = recurrence.weekdays.ifEmpty { setOf(startDate.dayOfWeek) }
        val weeks = ChronoUnit.WEEKS.between(startDate.weekStart(), date.weekStart())
        weeks >= 0 && weeks % recurrence.interval.coerceAtLeast(1) == 0L &&
            date.dayOfWeek in allowedDays
    }
    PlannerRecurrenceType.PRN -> false
}

/** The habit's "reference date" for interval recurrences: earliest recent event, else last completion, else [today]. */
internal fun Habit.recurrenceReferenceDate(today: LocalDate): LocalDate =
    recentEvents.minOfOrNull { it.eventDate } ?: lastCompletedDate ?: today

internal fun LocalDate.weekStart(): LocalDate =
    minusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
