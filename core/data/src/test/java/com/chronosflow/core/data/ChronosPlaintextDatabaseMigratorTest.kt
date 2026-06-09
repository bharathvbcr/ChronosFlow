package com.chronosflow.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosPlaintextDatabaseMigratorTest {
    @Test
    fun `migration table list includes planner and recurring task tables`() {
        assertTrue(ChronosPlaintextDatabaseMigrator.TABLE_NAMES.contains("mood_energy_check_ins"))
        assertTrue(ChronosPlaintextDatabaseMigrator.TABLE_NAMES.contains("task_schedules"))
        assertTrue(ChronosPlaintextDatabaseMigrator.TABLE_NAMES.contains("task_reminder_rules"))
        assertTrue(ChronosPlaintextDatabaseMigrator.TABLE_NAMES.contains("habit_schedules"))
        assertEquals(25, ChronosPlaintextDatabaseMigrator.TABLE_NAMES.size)
    }
}
