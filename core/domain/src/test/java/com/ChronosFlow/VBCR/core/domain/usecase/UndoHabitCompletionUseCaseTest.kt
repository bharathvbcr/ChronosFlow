package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The habit undo hinges on recomputing the streak from completion history — the stored streakCount
 * alone can't be reversed once a completion whose gap exceeded the window reset it to 1. These tests
 * lock that recompute in. The [UseCaseTestFixtures.habit] has a null schedule, so the streak window
 * is 1 day (daily).
 */
class UndoHabitCompletionUseCaseTest {
    private val repo: HabitRepository = mockk(relaxed = true)
    private val useCase = UndoHabitCompletionUseCase(repo)
    private val today: LocalDate = UseCaseTestFixtures.date // 2026-05-27

    private fun completed(id: String, date: LocalDate): HabitEvent = HabitEvent(
        id = id,
        habitId = "h1",
        type = HabitEventType.COMPLETED,
        eventDate = date,
        recordedAt = UseCaseTestFixtures.now,
        reason = null,
        startMinuteOfDay = 7 * 60,
        endMinuteOfDay = 8 * 60
    )

    private fun stubHabit(streakCount: Int, lastCompletedDate: LocalDate?) {
        coEvery { repo.getHabitById("h1") } returns
            UseCaseTestFixtures.habit(
                id = "h1",
                title = "Meditate",
                streakCount = streakCount,
                lastCompletedDate = lastCompletedDate
            )
    }

    @Test
    fun `undoing today's completion in a consecutive run drops the streak by one`() = runTest {
        stubHabit(streakCount = 3, lastCompletedDate = today)
        coEvery { repo.getHabitEvents("h1") } returns listOf(
            completed("e1", today.minusDays(2)),
            completed("e2", today.minusDays(1)),
            completed("e3", today)
        )
        val saved = slot<Habit>()
        coEvery { repo.saveHabit(capture(saved)) } returns Unit

        useCase("h1", today)

        coVerify(exactly = 1) { repo.deleteHabitEvent("e3") }
        assertEquals(2, saved.captured.streakCount)
        assertEquals(today.minusDays(1), saved.captured.lastCompletedDate)
    }

    @Test
    fun `undoing a streak-resetting completion restores the prior run the stored count had lost`() = runTest {
        // Forward: May20=1, May21=2, then May27 (gap 6 > window 1) reset the streak to 1. The stored
        // streakCount (1) can't reveal the prior run of 2 — only the recompute from history can.
        stubHabit(streakCount = 1, lastCompletedDate = today)
        coEvery { repo.getHabitEvents("h1") } returns listOf(
            completed("a", today.minusDays(7)),
            completed("b", today.minusDays(6)),
            completed("c", today)
        )
        val saved = slot<Habit>()
        coEvery { repo.saveHabit(capture(saved)) } returns Unit

        useCase("h1", today)

        coVerify(exactly = 1) { repo.deleteHabitEvent("c") }
        assertEquals(2, saved.captured.streakCount)
        assertEquals(today.minusDays(6), saved.captured.lastCompletedDate)
    }

    @Test
    fun `undoing the only completion clears the streak`() = runTest {
        stubHabit(streakCount = 1, lastCompletedDate = today)
        coEvery { repo.getHabitEvents("h1") } returns listOf(completed("only", today))
        val saved = slot<Habit>()
        coEvery { repo.saveHabit(capture(saved)) } returns Unit

        useCase("h1", today)

        coVerify(exactly = 1) { repo.deleteHabitEvent("only") }
        assertEquals(0, saved.captured.streakCount)
        assertNull(saved.captured.lastCompletedDate)
    }

    @Test
    fun `a habit not completed today is left untouched`() = runTest {
        stubHabit(streakCount = 5, lastCompletedDate = today.minusDays(1))

        useCase("h1", today)

        coVerify(exactly = 0) { repo.deleteHabitEvent(any()) }
        coVerify(exactly = 0) { repo.saveHabit(any()) }
    }
}
