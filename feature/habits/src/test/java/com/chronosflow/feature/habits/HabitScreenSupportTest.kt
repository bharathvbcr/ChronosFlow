package com.chronosflow.feature.habits

import com.chronosflow.core.domain.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitScreenSupportTest {
    @Test
    fun `archive confirmation label uses habit title when available`() {
        val habit = Habit(
            id = "habit-id",
            title = "Morning walk",
            cadence = "Daily",
            windowStartMinute = 8 * 60,
            windowEndMinute = 9 * 60,
            difficulty = 2,
            isBundled = false,
            streakCount = 0,
            lastCompletedDate = null,
            isActive = true
        )

        assertEquals(
            "Archive \"Morning walk\"?",
            habitArchiveConfirmTitle(habit)
        )
    }

    @Test
    fun `archive confirmation label falls back when habit title is blank`() {
        val habit = Habit(
            id = "habit-id",
            title = " ",
            cadence = "Daily",
            windowStartMinute = 8 * 60,
            windowEndMinute = 9 * 60,
            difficulty = 2,
            isBundled = false,
            streakCount = 0,
            lastCompletedDate = null,
            isActive = true
        )

        assertEquals("Archive \"this habit\"?", habitArchiveConfirmTitle(habit))
    }

    @Test
    fun `habit sheet target carries expected payloads`() {
        val add = HabitSheetTarget.Add("Morning walk")
        val emptyAdd = HabitSheetTarget.Add()
        val habit = Habit(
            id = "habit-id",
            title = "Evening stretch",
            cadence = "Daily",
            windowStartMinute = 8 * 60,
            windowEndMinute = 9 * 60,
            difficulty = 2,
            isBundled = false,
            streakCount = 0,
            lastCompletedDate = null,
            isActive = true
        )
        val edit = HabitSheetTarget.Edit(habit)

        assertEquals("Morning walk", (add as HabitSheetTarget.Add).prefillTitle)
        assertEquals(null, emptyAdd.prefillTitle)
        assertEquals("habit-id", (edit as HabitSheetTarget.Edit).habit.id)
    }
}
