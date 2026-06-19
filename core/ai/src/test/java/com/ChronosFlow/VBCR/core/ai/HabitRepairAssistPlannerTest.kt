package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitSchedule
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRepairAssistPlannerTest {
    @Test
    fun `suggestRepairs parses AI windows`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "habit-1|660,690|Short evening recovery window",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = HabitRepairAssistPlanner(coordinator)
        val habit = missedHabit()

        val repairs = planner.suggestRepairs(listOf(habit), LocalDate.of(2026, 5, 25), currentMinute = 20 * 60)

        assertEquals(1, repairs.size)
        assertEquals(RoutineAssistSource.GEMINI_NANO, repairs.first().source)
        assertEquals(660, repairs.first().suggestedStartMinute)
    }

    @Test
    fun `suggestRepairs falls back locally when AI empty`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = HabitRepairAssistPlanner(coordinator)

        val repairs = planner.suggestRepairs(listOf(missedHabit()), LocalDate.of(2026, 5, 25), currentMinute = 20 * 60)

        assertEquals(RoutineAssistSource.LOCAL, repairs.first().source)
        assertTrue(repairs.first().reason.contains("Window closed"))
    }

    private fun missedHabit(): Habit = Habit(
        id = "habit-1",
        title = "Evening journal",
        cadence = "Daily",
        windowStartMinute = 18 * 60,
        windowEndMinute = 19 * 60,
        difficulty = 2,
        isBundled = false,
        streakCount = 0,
        lastCompletedDate = null,
        isActive = true,
        launchTarget = null,
        schedule = HabitSchedule(
            id = "schedule-1",
            habitId = "habit-1",
            targetStartMinute = 18 * 60,
            targetEndMinute = 19 * 60,
            plannerVisible = true,
            pausedUntil = null,
            skipDate = null
        )
    )
}
