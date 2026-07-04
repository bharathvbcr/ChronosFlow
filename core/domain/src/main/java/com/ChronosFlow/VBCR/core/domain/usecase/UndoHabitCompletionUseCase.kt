package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reverses a habit completion recorded for [date] — the wrist/widget "undo" for an accidental
 * check-off. It removes today's COMPLETED event(s) and restores the habit's [streakCount] and
 * [lastCompletedDate] by recomputing them from the remaining completion history via
 * [habitStreakFromHistory]. The streak can't be reversed from the stored count alone: a completion
 * whose gap exceeded the streak window reset it to 1 and discarded the prior value, so the true
 * prior streak is only recoverable from history.
 *
 * Idempotent and safe: a no-op unless the habit is actually marked complete for [date], so a stale
 * mirror or a double-fire can't corrupt streak state.
 */
class UndoHabitCompletionUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(habitId: String, date: LocalDate = LocalDate.now()) {
        val habit = habitRepository.getHabitById(habitId) ?: return
        if (habit.lastCompletedDate != date) return

        val completions = habitRepository.getHabitEvents(habitId)
            .filter { it.type == HabitEventType.COMPLETED }
        completions.filter { it.eventDate == date }
            .forEach { habitRepository.deleteHabitEvent(it.id) }

        val remaining = completions.filter { it.eventDate != date }
            .map { it.eventDate }
            .distinct()
            .sorted()
        habitRepository.saveHabit(
            habit.copy(
                streakCount = habitStreakFromHistory(remaining, habitStreakWindowDays(habit)),
                lastCompletedDate = remaining.lastOrNull()
            )
        )
    }
}
