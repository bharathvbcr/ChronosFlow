package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.model.HabitRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import java.time.LocalDate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

class CompleteHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(habit: Habit, date: LocalDate = LocalDate.now()) {
        val nextStreak = when {
            habit.lastCompletedDate == null -> 1
            habit.lastCompletedDate == date -> habit.streakCount
            else -> {
                val gap = ChronoUnit.DAYS.between(habit.lastCompletedDate, date)
                val window = habitStreakWindowDays(habit)
                if (gap in 1L..window) habit.streakCount + 1 else 1
            }
        }
        habitRepository.saveHabit(
            habit.copy(
                streakCount = nextStreak,
                lastCompletedDate = date,
                isActive = true
            )
        )
        habitRepository.addHabitEvent(
            HabitEvent(
                id = UUID.randomUUID().toString(),
                habitId = habit.id,
                type = HabitEventType.COMPLETED,
                eventDate = date,
                recordedAt = Instant.now(),
                reason = null,
                startMinuteOfDay = habit.schedule?.targetStartMinute ?: habit.windowStartMinute,
                endMinuteOfDay = habit.schedule?.targetEndMinute ?: habit.windowEndMinute
            )
        )
    }
}

/**
 * Maximum day-gap between two consecutive completions that still extends the streak,
 * derived from the habit's recurrence rule. A daily habit breaks on any gap > 1 day;
 * a weekly habit on any gap > 7 days; weekday-only habits allow up to 3 days (Fri→Mon).
 */
internal fun habitStreakWindowDays(habit: Habit): Long {
    val rule = habit.schedule?.resolvedRecurrenceRule ?: return 1L
    return when (rule) {
        is HabitRecurrenceRule.Quota -> 31L  // quota habits: generous window, streak is less meaningful
        is HabitRecurrenceRule.Scheduled -> when (rule.recurrence.type) {
            PlannerRecurrenceType.DAILY,
            PlannerRecurrenceType.MULTIPLE_TIMES_DAILY -> 1L
            PlannerRecurrenceType.WEEKDAYS -> 3L   // Fri → Mon
            PlannerRecurrenceType.WEEKENDS -> 6L   // Sun → next Sat
            PlannerRecurrenceType.EVERY_N_DAYS -> rule.recurrence.interval.toLong().coerceAtLeast(1L)
            PlannerRecurrenceType.WEEKLY_INTERVAL -> (rule.recurrence.interval.toLong() * 7L).coerceAtLeast(7L)
            PlannerRecurrenceType.SELECTED_WEEKDAYS -> {
                // Max gap between any two consecutive selected days of the week (with wrap-around).
                val sorted = rule.recurrence.weekdays.map { it.value }.sorted()
                if (sorted.isEmpty()) 7L
                else {
                    val pairwiseGaps = sorted.zipWithNext { a, b -> (b - a).toLong() }
                    val wrapGap = (7L - sorted.last() + sorted.first())
                    (pairwiseGaps + wrapGap).max()
                }
            }
            PlannerRecurrenceType.PRN -> 0L  // as-needed: never extend streak
        }
    }
}

/**
 * The streak implied by a habit's [sortedCompletionDates] (ascending, distinct) under [windowDays]:
 * the count of consecutive completions ending at the latest date whose day-gaps all fall within the
 * window. This is the exact inverse of [CompleteHabitUseCase]'s forward increment, so it restores
 * the correct streak when a completion is undone (including the "gap too large → reset to 1" case
 * that the stored streakCount alone can't reconstruct). Empty history means a streak of 0.
 */
internal fun habitStreakFromHistory(sortedCompletionDates: List<LocalDate>, windowDays: Long): Int {
    if (sortedCompletionDates.isEmpty()) return 0
    var streak = 1
    var i = sortedCompletionDates.lastIndex
    while (i > 0) {
        val gap = ChronoUnit.DAYS.between(sortedCompletionDates[i - 1], sortedCompletionDates[i])
        if (gap in 1L..windowDays) {
            streak++
            i--
        } else {
            break
        }
    }
    return streak
}
