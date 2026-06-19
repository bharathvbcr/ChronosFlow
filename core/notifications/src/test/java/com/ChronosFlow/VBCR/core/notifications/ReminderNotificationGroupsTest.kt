package com.ChronosFlow.VBCR.core.notifications

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

    @Test
    fun `category icon mirrors the label precedence medication then habit then task`() {
        assertEquals(
            R.drawable.ic_notif_medication,
            ReminderNotificationGroups.categoryIconRes(isMedication = true, isHabit = true, isTask = true)
        )
        assertEquals(
            R.drawable.ic_notif_habit,
            ReminderNotificationGroups.categoryIconRes(isMedication = false, isHabit = true, isTask = true)
        )
        assertEquals(
            R.drawable.ic_notif_task,
            ReminderNotificationGroups.categoryIconRes(isMedication = false, isHabit = false, isTask = true)
        )
    }

    @Test
    fun `category icon falls back to the generic alarm glyph`() {
        assertEquals(
            R.drawable.ic_chronos_alarm,
            ReminderNotificationGroups.categoryIconRes(isMedication = false, isHabit = false, isTask = false)
        )
    }
}
