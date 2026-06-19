package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CompleteHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository
) {
    suspend operator fun invoke(habit: Habit, date: LocalDate = LocalDate.now()) {
        val nextStreak = when (habit.lastCompletedDate) {
            date -> habit.streakCount
            date.minusDays(1) -> habit.streakCount + 1
            else -> 1
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
