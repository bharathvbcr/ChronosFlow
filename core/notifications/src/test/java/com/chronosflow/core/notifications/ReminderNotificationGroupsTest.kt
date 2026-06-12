package com.chronosflow.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationGroupsTest {
    @Test
    fun `category label prefers medication then habit then task`() {
        assertEquals(
            "Medication",
            ReminderNotificationGroups.categoryLabel(isMedication = true, isHabit = true, isTask = true)
        )
        assertEquals(
            "Habit",
            ReminderNotificationGroups.categoryLabel(isMedication = false, isHabit = true, isTask = true)
        )
        assertEquals(
            "Task",
            ReminderNotificationGroups.categoryLabel(isMedication = false, isHabit = false, isTask = true)
        )
    }

    @Test
    fun `category label falls back to generic reminder`() {
        assertEquals(
            "Reminder",
            ReminderNotificationGroups.categoryLabel(isMedication = false, isHabit = false, isTask = false)
        )
    }
}
