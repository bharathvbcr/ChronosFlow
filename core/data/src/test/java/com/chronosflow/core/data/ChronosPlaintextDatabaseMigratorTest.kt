package com.chronosflow.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosPlaintextDatabaseMigratorTest {
    @Test
    fun `user table filter keeps app tables including post-v17 companion tables`() {
        listOf(
            "tasks",
            "time_blocks",
            "mood_energy_check_ins",
            "task_schedules",
            "task_reminder_rules",
            "habit_schedules",
            "goals",
            "journal_entries",
            "sleep_tracks",
            "routines",
            "routine_steps"
        ).forEach { table ->
            assertTrue("expected $table to be copied", ChronosPlaintextDatabaseMigrator.isUserTable(table))
        }
    }

    @Test
    fun `user table filter drops SQLite and Room bookkeeping tables`() {
        listOf("sqlite_sequence", "sqlite_stat1", "android_metadata", "room_master_table").forEach { table ->
            assertFalse("expected $table to be skipped", ChronosPlaintextDatabaseMigrator.isUserTable(table))
        }
    }
}
