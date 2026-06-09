package com.chronosflow.feature.habits

import com.chronosflow.core.ai.missedForRepair
import com.chronosflow.core.domain.model.Habit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRepairSuggestionTest {
    private val today: LocalDate = LocalDate.of(2026, 5, 8)

    @Test
    fun statusLabelIncludesStreakWhenHabitWasCompletedToday() {
        val label = habit(
            id = "done",
            title = "Stretch",
            windowEndMinute = 9 * 60,
            lastCompletedDate = today,
            streakCount = 5
        ).statusLabel(nowMinute = 10 * 60, today = today)

        assertEquals("Done today · 5-day streak", label)
    }

    @Test
    fun missedForRepairReturnsPastDueUncompletedActiveHabits() {
        val habits = listOf(
            habit(id = "past", title = "Stretch", windowEndMinute = 9 * 60),
            habit(id = "future", title = "Read", windowEndMinute = 20 * 60),
            habit(id = "done", title = "Walk", windowEndMinute = 8 * 60, lastCompletedDate = today),
            habit(id = "archived", title = "Journal", windowEndMinute = 7 * 60, isActive = false)
        )

        val missed = habits.missedForRepair(today = today, currentMinute = 10 * 60)

        assertEquals(1, missed.size)
        assertEquals("past", missed.single().id)
    }

    @Test
    fun missedForRepairReturnsEmptyWhenNothingIsMissed() {
        val missed = listOf(
            habit(id = "current", title = "Hydrate", windowEndMinute = 12 * 60),
            habit(id = "done", title = "Move", windowEndMinute = 8 * 60, lastCompletedDate = today)
        ).missedForRepair(today = today, currentMinute = 9 * 60)

        assertTrue(missed.isEmpty())
    }

    private fun habit(
        id: String,
        title: String,
        windowEndMinute: Int,
        lastCompletedDate: LocalDate? = null,
        isActive: Boolean = true,
        streakCount: Int = 0
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = (windowEndMinute - 60).coerceAtLeast(0),
        windowEndMinute = windowEndMinute,
        difficulty = 2,
        isBundled = false,
        streakCount = streakCount,
        lastCompletedDate = lastCompletedDate,
        isActive = isActive
    )
}
