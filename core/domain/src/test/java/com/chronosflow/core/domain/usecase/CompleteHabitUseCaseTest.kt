package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.HabitEventType
import com.chronosflow.core.domain.repository.HabitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import org.junit.Test

class CompleteHabitUseCaseTest {

    private val repository: HabitRepository = mockk(relaxed = true)
    private val useCase = CompleteHabitUseCase(repository)

    @Test
    fun `records completion event and updates streak`() = kotlinx.coroutines.test.runTest {
        val date = LocalDate.of(2026, 5, 25)
        val habit = Habit(
            id = "habit-1",
            title = "Morning walk",
            cadence = "Daily",
            windowStartMinute = 7 * 60,
            windowEndMinute = 9 * 60,
            difficulty = 2,
            isBundled = true,
            streakCount = 2,
            lastCompletedDate = date.minusDays(1),
            isActive = true
        )
        coEvery { repository.saveHabit(any()) } returns Unit
        coEvery { repository.addHabitEvent(any()) } returns Unit

        useCase(habit, date)

        coVerify {
            repository.saveHabit(
                match {
                    it.id == "habit-1" &&
                        it.streakCount == 3 &&
                        it.lastCompletedDate == date
                }
            )
        }
        coVerify {
            repository.addHabitEvent(
                match {
                    it.habitId == "habit-1" &&
                        it.type == HabitEventType.COMPLETED &&
                        it.eventDate == date
                }
            )
        }
    }
}
