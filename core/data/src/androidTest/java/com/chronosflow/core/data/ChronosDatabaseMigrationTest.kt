package com.chronosflow.core.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChronosDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChronosDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate7To18ValidatesFullCheckedInSchemaChain() {
        helper.createDatabase(TEST_DB, 7).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            *AVAILABLE_SCHEMA_MIGRATIONS
        )
    }

    @Test
    fun migrate9To10BackfillsPlannerTables() {
        helper.createDatabase(TEST_DB, 9).apply {
            insert(
                "habits",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "habit-1")
                    put("title", "Morning walk")
                    put("cadence", "Weekdays")
                    put("windowStartMinute", 420)
                    put("windowEndMinute", 540)
                    put("difficulty", 2)
                    put("isBundled", 1)
                    put("streakCount", 3)
                    put("lastCompletedDate", "2026-05-24")
                    put("isActive", 1)
                }
            )
            insert(
                "medication_plans",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "med-1")
                    put("name", "Vitamin D")
                    put("dosage", "1")
                    put("unit", "tablet")
                    put("notes", "[[reminder2:1200]] [[timing:with_food]] after breakfast")
                    put("reminderMinuteOfDay", 480)
                    put("takeWithFood", 1)
                    put("missedCount", 1)
                    put("refillNeededAfterDoses", 5)
                    put("isActive", 1)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            ChronosDatabase.MIGRATION_9_10,
            ChronosDatabase.MIGRATION_10_11
        ).apply {
            query(
                "SELECT recurrenceType, weekdaysCsv, targetStartMinute, targetEndMinute, plannerVisible FROM habit_schedules WHERE habitId = 'habit-1'"
            ).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("WEEKDAYS", cursor.getString(0))
                org.junit.Assert.assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", cursor.getString(1))
                org.junit.Assert.assertEquals(420, cursor.getInt(2))
                org.junit.Assert.assertEquals(540, cursor.getInt(3))
                org.junit.Assert.assertEquals(1, cursor.getInt(4))
            }

            query(
                "SELECT recurrenceType, doseTimesCsv, plannerVisible FROM medication_schedules WHERE medicationPlanId = 'med-1'"
            ).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("MULTIPLE_TIMES_DAILY", cursor.getString(0))
                org.junit.Assert.assertEquals("480,1200", cursor.getString(1))
                org.junit.Assert.assertEquals(1, cursor.getInt(2))
            }

            query(
                "SELECT form, mealTiming, supplyRemaining, refillThreshold, instructions FROM medication_safety_profiles WHERE medicationPlanId = 'med-1'"
            ).use { cursor ->
                cursor.moveToFirst()
                org.junit.Assert.assertEquals("tablet", cursor.getString(0))
                org.junit.Assert.assertEquals("With food", cursor.getString(1))
                org.junit.Assert.assertEquals(5, cursor.getInt(2))
                org.junit.Assert.assertEquals(5, cursor.getInt(3))
                org.junit.Assert.assertTrue(cursor.getString(4).contains("after breakfast"))
            }
        }
    }

    @Test
    fun migrate11To12CreatesTaskAttachmentsTable() {
        helper.createDatabase(TEST_DB, 11).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            12,
            true,
            ChronosDatabase.MIGRATION_11_12
        ).apply {
            query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'task_attachments'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("task_attachments", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate12To14BackfillsAdvancedHabitRecurrenceColumns() {
        helper.createDatabase(TEST_DB, 12).apply {
            insert(
                "habit_schedules",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "schedule-habit-1")
                    put("habitId", "habit-1")
                    put("recurrenceType", "SELECTED_WEEKDAYS")
                    put("intervalCount", 2)
                    put("weekdaysCsv", "MONDAY,FRIDAY")
                    put("targetStartMinute", 420)
                    put("targetEndMinute", 540)
                    put("plannerVisible", 1)
                    put("pausedUntil", "2026-05-30")
                    put("skipDate", "2026-05-26")
                    put("deferUntilMinuteOfDay", 615)
                    put("createdAt", 1_716_630_000_000L)
                    put("updatedAt", 1_716_633_600_000L)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            ChronosDatabase.MIGRATION_12_13,
            ChronosDatabase.MIGRATION_13_14
        ).apply {
            query(
                """
                SELECT
                    recurrenceType,
                    intervalCount,
                    weekdaysCsv,
                    targetStartMinute,
                    targetEndMinute,
                    plannerVisible,
                    pausedUntil,
                    skipDate,
                    deferUntilMinuteOfDay,
                    createdAt,
                    updatedAt,
                    recurrenceRuleKind,
                    quotaTargetCompletions,
                    quotaPeriodUnit
                FROM habit_schedules
                WHERE habitId = 'habit-1'
                """.trimIndent()
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("SELECTED_WEEKDAYS", cursor.getString(0))
                org.junit.Assert.assertEquals(2, cursor.getInt(1))
                org.junit.Assert.assertEquals("MONDAY,FRIDAY", cursor.getString(2))
                org.junit.Assert.assertEquals(420, cursor.getInt(3))
                org.junit.Assert.assertEquals(540, cursor.getInt(4))
                org.junit.Assert.assertEquals(1, cursor.getInt(5))
                org.junit.Assert.assertEquals("2026-05-30", cursor.getString(6))
                org.junit.Assert.assertEquals("2026-05-26", cursor.getString(7))
                org.junit.Assert.assertEquals(615, cursor.getInt(8))
                org.junit.Assert.assertEquals(1_716_630_000_000L, cursor.getLong(9))
                org.junit.Assert.assertEquals(1_716_633_600_000L, cursor.getLong(10))
                org.junit.Assert.assertEquals("SCHEDULED", cursor.getString(11))
                org.junit.Assert.assertTrue(cursor.isNull(12))
                org.junit.Assert.assertTrue(cursor.isNull(13))
            }
        }
    }

    @Test
    fun migrate12To14CreatesRecurringTaskTablesAndOccurrenceColumn() {
        helper.createDatabase(TEST_DB, 12).apply {
            insert(
                "tasks",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "task-1")
                    put("title", "Write proposal")
                    put("description", "Draft the proposal")
                    put("isCompleted", 0)
                    put("priority", 1)
                    putNull("dueDate")
                    put("createdAt", 1_716_630_000_000L)
                    put("updatedAt", 1_716_633_600_000L)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            ChronosDatabase.MIGRATION_12_13,
            ChronosDatabase.MIGRATION_13_14
        ).apply {
            query("PRAGMA table_info(`time_blocks`)").use { cursor ->
                var foundOccurrenceColumn = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "taskOccurrenceDate") {
                        foundOccurrenceColumn = true
                        break
                    }
                }
                org.junit.Assert.assertTrue(foundOccurrenceColumn)
            }

            insert(
                "task_schedules",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "schedule-1")
                    put("taskId", "task-1")
                    put("recurrenceType", "WEEKLY")
                    put("intervalCount", 1)
                    put("weekdaysCsv", "MONDAY,THURSDAY")
                    putNull("dayOfMonth")
                    putNull("ordinalInMonth")
                    putNull("weekdayInMonth")
                    put("startsOn", "2026-05-25")
                    putNull("endsOn")
                    putNull("maxOccurrences")
                    put("occurrenceMinuteOfDay", 540)
                    put("nextOccurrenceDate", "2026-05-26")
                    putNull("lastCompletedOccurrenceDate")
                    putNull("generatedThroughDate")
                    put("isPaused", 0)
                    put("createdAt", 1_716_630_000_000L)
                    put("updatedAt", 1_716_633_600_000L)
                }
            )
            insert(
                "task_reminder_rules",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "reminder-1")
                    put("taskScheduleId", "schedule-1")
                    put("trigger", "BEFORE_OCCURRENCE")
                    putNull("minuteOfDay")
                    put("offsetMinutesBefore", 30)
                    put("sortOrder", 0)
                }
            )

            query(
                """
                SELECT
                    recurrenceType,
                    intervalCount,
                    weekdaysCsv,
                    occurrenceMinuteOfDay,
                    nextOccurrenceDate,
                    isPaused
                FROM task_schedules
                WHERE taskId = 'task-1'
                """.trimIndent()
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("WEEKLY", cursor.getString(0))
                org.junit.Assert.assertEquals(1, cursor.getInt(1))
                org.junit.Assert.assertEquals("MONDAY,THURSDAY", cursor.getString(2))
                org.junit.Assert.assertEquals(540, cursor.getInt(3))
                org.junit.Assert.assertEquals("2026-05-26", cursor.getString(4))
                org.junit.Assert.assertEquals(0, cursor.getInt(5))
            }

            query(
                """
                SELECT trigger, offsetMinutesBefore
                FROM task_reminder_rules
                WHERE taskScheduleId = 'schedule-1'
                """.trimIndent()
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("BEFORE_OCCURRENCE", cursor.getString(0))
                org.junit.Assert.assertEquals(30, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migrate14To15PreservesHabitsAndAddsLaunchTargetColumns() {
        helper.createDatabase(TEST_DB, 14).apply {
            insert(
                "habits",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "habit-launch")
                    put("title", "Morning walk")
                    put("cadence", "Daily")
                    put("windowStartMinute", 420)
                    put("windowEndMinute", 540)
                    put("difficulty", 2)
                    put("isBundled", 1)
                    put("streakCount", 3)
                    put("lastCompletedDate", "2026-05-24")
                    put("isActive", 1)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            15,
            true,
            ChronosDatabase.MIGRATION_14_15
        ).apply {
            query(
                "SELECT title, launchAppLabel, launchAppValue FROM habits WHERE id = 'habit-launch'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Morning walk", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
                org.junit.Assert.assertTrue(cursor.isNull(2))
            }
        }
    }

    @Test
    fun migrate15To16PreservesReviewInsightsAndAddsAssistSourceColumn() {
        helper.createDatabase(TEST_DB, 15).apply {
            insert(
                "review_insights",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "insight-1")
                    put("date", "2026-05-27")
                    put("type", "DRIFT")
                    put("title", "Late start")
                    put("detail", "Deep work started later than planned.")
                    putNull("relatedBlockId")
                    put("severity", "INFO")
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            16,
            true,
            ChronosDatabase.MIGRATION_15_16
        ).apply {
            query(
                "SELECT title, assistSource FROM review_insights WHERE id = 'insight-1'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Late start", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    fun migrate16To17AddsExpansionTablesAndLinkColumns() {
        helper.createDatabase(TEST_DB, 16).apply {
            insert(
                "tasks",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "task-goal")
                    put("title", "Draft chapter")
                    putNull("description")
                    put("isCompleted", 0)
                    put("priority", 1)
                    putNull("dueDate")
                    put("createdAt", 1_717_000_000_000L)
                    put("updatedAt", 1_717_000_000_000L)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            17,
            true,
            ChronosDatabase.MIGRATION_16_17
        ).apply {
            // Preserved row gains a null goal link.
            query("SELECT title, goalId FROM tasks WHERE id = 'task-goal'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Draft chapter", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
            // New tables are queryable.
            query("SELECT COUNT(*) FROM goals").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM journal_entries").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM sleep_tracks").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM routine_steps").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate17To18RecreatesCalendarEventsWithCompositeKey() {
        helper.createDatabase(TEST_DB, 17).apply {
            insert(
                "calendar_events",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", 42L)
                    put("title", "Planning review")
                    putNull("description")
                    put("startAt", 1_700_000_000_000L)
                    put("endAt", 1_700_003_600_000L)
                    put("timezone", "UTC")
                    putNull("location")
                    put("externalId", "device_event_42")
                    put("isAllDay", 0)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            18,
            true,
            ChronosDatabase.MIGRATION_17_18
        ).apply {
            // Snapshot table is dropped and recreated; two instances of the same
            // event id must now coexist.
            query("SELECT COUNT(*) FROM calendar_events").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(0, cursor.getInt(0))
            }
            execSQL(
                "INSERT INTO calendar_events (id, title, description, startAt, endAt, timezone, location, externalId, isAllDay) " +
                    "VALUES (42, 'Standup', NULL, 1, 2, 'UTC', NULL, 'device_event_42', 0)"
            )
            execSQL(
                "INSERT INTO calendar_events (id, title, description, startAt, endAt, timezone, location, externalId, isAllDay) " +
                    "VALUES (42, 'Standup', NULL, 3, 4, 'UTC', NULL, 'device_event_42', 0)"
            )
            query("SELECT COUNT(*) FROM calendar_events WHERE id = 42").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(2, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "chronos-migration-test"
        val AVAILABLE_SCHEMA_MIGRATIONS: Array<Migration> = arrayOf(
            ChronosDatabase.MIGRATION_7_8,
            ChronosDatabase.MIGRATION_8_9,
            ChronosDatabase.MIGRATION_9_10,
            ChronosDatabase.MIGRATION_10_11,
            ChronosDatabase.MIGRATION_11_12,
            ChronosDatabase.MIGRATION_12_13,
            ChronosDatabase.MIGRATION_13_14,
            ChronosDatabase.MIGRATION_14_15,
            ChronosDatabase.MIGRATION_15_16,
            ChronosDatabase.MIGRATION_16_17,
            ChronosDatabase.MIGRATION_17_18
        )
    }
}
