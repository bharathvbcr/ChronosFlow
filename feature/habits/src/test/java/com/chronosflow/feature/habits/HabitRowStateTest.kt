package com.chronosflow.feature.habits

import com.chronosflow.core.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitRowStateTest {
    @Test
    fun `habit row action labels name the habit`() {
        val habit = habit(title = "Morning mobility")

        assertEquals("Open actions for Morning mobility", habitContextActionLabel(habit))
        assertEquals("More actions for Morning mobility", habitMoreActionLabel(habit))
        assertEquals("Edit Morning mobility", habitEditActionLabel(habit))
        assertEquals("Archive Morning mobility", habitArchiveActionLabel(habit))
    }

    @Test
    fun `habit completion label names completed state and habit`() {
        val openHabit = habit(title = "Morning mobility", completedToday = false)
        val completedHabit = habit(title = "Morning mobility", completedToday = true)

        assertEquals("Complete Morning mobility", habitCompleteActionLabel(openHabit))
        assertEquals("Morning mobility completed today", habitCompleteActionLabel(completedHabit))
    }

    @Test
    fun `habit context sheet action labels name the habit and outcome`() {
        val habit = habit(title = "Morning mobility")

        assertEquals("Defer Morning mobility for 1 hour", habitDeferActionLabel(habit))
        assertEquals("Skip Morning mobility today", habitSkipActionLabel(habit))
        assertEquals("Pause Morning mobility for 1 day", habitPauseActionLabel(habit))
        assertEquals("Resume Morning mobility", habitResumeActionLabel(habit))
    }

    @Test
    fun `repair suggestion complete label names the habit`() {
        val suggestion = HabitRepairSuggestion(
            habit = habit(title = "Evening stretch"),
            suggestedStartMinute = 18 * 60,
            suggestedEndMinute = 18 * 60 + 20,
            reason = "Missed earlier"
        )

        assertEquals("Complete repair for Evening stretch", habitRepairCompleteActionLabel(suggestion))
    }

    private fun habit(
        title: String,
        completedToday: Boolean = false
    ): Habit {
        return Habit(
            id = "habit-1",
            title = title,
            cadence = "Daily",
            windowStartMinute = 7 * 60,
            windowEndMinute = 8 * 60,
            difficulty = 2,
            isBundled = false,
            streakCount = 0,
            lastCompletedDate = if (completedToday) java.time.LocalDate.now() else null,
            isActive = true
        )
    }
}
