package com.ChronosFlow.VBCR.core.data

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
    fun migrate7To19ValidatesFullCheckedInSchemaChain() {
        helper.createDatabase(TEST_DB, 7).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            19,
            true,
            *AVAILABLE_SCHEMA_MIGRATIONS
        )
    }

    @Test
    fun migrate18To19AddsSleepSourceColumnDefaultingToManual() {
        helper.createDatabase(TEST_DB, 18).apply {
            insert(
                "sleep_tracks",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "sleep-1")
                    put("date", "2026-06-11")
                    putNull("plannedStartMinute")
                    putNull("plannedEndMinute")
                    put("actualStartMinute", 1380)
                    put("actualEndMinute", 420)
                    put("sleepQuality", 4)
                    put("windDownNotes", "Read before bed")
                    put("interruptedCount", 1)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            19,
            true,
            ChronosDatabase.MIGRATION_18_19
        ).apply {
            query("SELECT windDownNotes, source FROM sleep_tracks WHERE id = 'sleep-1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Read before bed", cursor.getString(0))
                org.junit.Assert.assertEquals("MANUAL", cursor.getString(1))
            }
        }
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
    fun migrate19To20CreatesAppUsageDaysTable() {
        helper.createDatabase(TEST_DB, 19).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            20,
            true,
            ChronosDatabase.MIGRATION_19_20
        ).apply {
            // New, additive table is present and queryable; existing data is untouched.
            execSQL(
                "INSERT INTO app_usage_days (date, productiveMinutes, distractingMinutes, neutralMinutes) " +
                    "VALUES ('2026-06-15', 120, 45, 30)"
            )
            query(
                "SELECT productiveMinutes, distractingMinutes, neutralMinutes FROM app_usage_days WHERE date = '2026-06-15'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals(120, cursor.getInt(0))
                org.junit.Assert.assertEquals(45, cursor.getInt(1))
                org.junit.Assert.assertEquals(30, cursor.getInt(2))
            }
        }
    }

    @Test
    fun migrate20To21CreatesAppUsageOverridesTable() {
        helper.createDatabase(TEST_DB, 20).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            21,
            true,
            ChronosDatabase.MIGRATION_20_21
        ).apply {
            execSQL(
                "INSERT INTO app_usage_overrides (packageName, category) VALUES ('com.example.social', 'PRODUCTIVE')"
            )
            query("SELECT category FROM app_usage_overrides WHERE packageName = 'com.example.social'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("PRODUCTIVE", cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrate21To22() {
        helper.createDatabase(TEST_DB, 21).apply {
            insert(
                "tasks",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "task-origin-1")
                    put("title", "Shared task")
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
            22,
            true,
            ChronosDatabase.MIGRATION_21_22
        ).apply {
            query("SELECT origin, externalId FROM tasks WHERE id = 'task-origin-1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertTrue(cursor.isNull(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    fun testMigrate22To23() {
        helper.createDatabase(TEST_DB, 22).apply {
            insert(
                "journal_entries",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "entry-rating-1")
                    put("entryDate", "2026-06-20")
                    put("createdAt", 1_717_000_000_000L)
                    put("updatedAt", 1_717_000_000_000L)
                    put("body", "Today was great")
                    putNull("promptType")
                    putNull("moodCheckInId")
                    put("isPrimary", 1)
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            23,
            true,
            ChronosDatabase.MIGRATION_22_23
        ).apply {
            query("SELECT dayRating FROM journal_entries WHERE id = 'entry-rating-1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertTrue(cursor.isNull(0))
            }
        }
    }

    @Test
    fun testMigrate23To24() {
        helper.createDatabase(TEST_DB, 23).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            ChronosDatabase.MIGRATION_23_24
        ).apply {
            query("PRAGMA table_info(`journal_entries`)").use { cursor ->
                var foundColumn = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "entryMinuteOfDay") {
                        foundColumn = true
                        break
                    }
                }
                org.junit.Assert.assertTrue(foundColumn)
            }
        }
    }

    @Test
    fun testMigrate24To25() {
        helper.createDatabase(TEST_DB, 24).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            ChronosDatabase.MIGRATION_24_25
        ).apply {
            query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'journal_attachments'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("journal_attachments", cursor.getString(0))
            }
            // Verify schema: id, journalEntryId, uri, mimeType, createdAt, sortOrder
            query("PRAGMA table_info(`journal_attachments`)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(1))
                }
                org.junit.Assert.assertTrue(columns.contains("id"))
                org.junit.Assert.assertTrue(columns.contains("journalEntryId"))
                org.junit.Assert.assertTrue(columns.contains("uri"))
                org.junit.Assert.assertTrue(columns.contains("mimeType"))
                org.junit.Assert.assertTrue(columns.contains("createdAt"))
                org.junit.Assert.assertTrue(columns.contains("sortOrder"))
            }
        }
    }

    @Test
    fun testMigrate25To26() {
        helper.createDatabase(TEST_DB, 25).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            ChronosDatabase.MIGRATION_25_26
        ).apply {
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_journal_entries_isPrimary'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("index_journal_entries_isPrimary", cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrate26To27() {
        helper.createDatabase(TEST_DB, 26).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            27,
            true,
            ChronosDatabase.MIGRATION_26_27
        ).apply {
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_time_blocks_date_category'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("index_time_blocks_date_category", cursor.getString(0))
            }
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_tasks_targetDate_isCompleted'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("index_tasks_targetDate_isCompleted", cursor.getString(0))
            }
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_habit_events_habitId_recordedAt'"
            ).use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("index_habit_events_habitId_recordedAt", cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrate27To28DropsOrphanRecordedAtIndexes() {
        // Reproduce the real on-device state: MIGRATION_25_26 created standalone recordedAt indexes
        // that the v27 entities no longer declare, and no migration dropped them — so a migrated
        // database carries orphan indexes that make Room abort at open. Inject them here, since the
        // exported v27 schema (generated from the entities) does not contain them.
        helper.createDatabase(TEST_DB, 27).apply {
            execSQL("CREATE INDEX IF NOT EXISTS index_habit_events_recordedAt ON habit_events(recordedAt)")
            execSQL("CREATE INDEX IF NOT EXISTS index_medication_dose_events_recordedAt ON medication_dose_events(recordedAt)")
            close()
        }

        // validateDroppedTables = true also validates the final schema matches v28; this would fail
        // if either orphan index survived.
        helper.runMigrationsAndValidate(
            TEST_DB,
            28,
            true,
            ChronosDatabase.MIGRATION_27_28
        ).apply {
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_habit_events_recordedAt'"
            ).use { cursor ->
                org.junit.Assert.assertFalse(cursor.moveToFirst())
            }
            query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_medication_dose_events_recordedAt'"
            ).use { cursor ->
                org.junit.Assert.assertFalse(cursor.moveToFirst())
            }
        }
    }

    @Test
    fun migrate28To29AddsRefreshedRatingColumnDefaultingToNull() {
        helper.createDatabase(TEST_DB, 28).apply {
            insert(
                "sleep_tracks",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("id", "sleep-refresh-1")
                    put("date", "2026-06-21")
                    putNull("plannedStartMinute")
                    putNull("plannedEndMinute")
                    put("actualStartMinute", 1380)
                    put("actualEndMinute", 420)
                    put("sleepQuality", 4)
                    put("windDownNotes", "Stretched")
                    put("interruptedCount", 0)
                    put("source", "MANUAL")
                }
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            29,
            true,
            ChronosDatabase.MIGRATION_28_29
        ).apply {
            query("SELECT windDownNotes, refreshedRating FROM sleep_tracks WHERE id = 'sleep-refresh-1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Stretched", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    fun migrate29To30CreatesReadingAndInboxTables() {
        helper.createDatabase(TEST_DB, 29).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            30,
            true,
            ChronosDatabase.MIGRATION_29_30
        ).apply {
            // Both additive tables exist and accept a row.
            execSQL(
                "INSERT INTO reading_items (id, url, title, domain, faviconPath, estimatedReadMinutes, " +
                    "wordCount, status, metadataState, notes, reminderAt, addedAt, updatedAt, lastOpenedAt, sortOrder) " +
                    "VALUES ('r1', 'https://example.com/a', 'Article A', 'example.com', NULL, 5, 900, " +
                    "'UNREAD', 'PENDING', NULL, NULL, 1, 1, NULL, 0)"
            )
            execSQL(
                "INSERT INTO inbox_items (id, text, url, source, createdAt, triaged, triagedTo, triagedRefId, sortOrder) " +
                    "VALUES ('i1', 'Call the dentist', NULL, 'MANUAL', 1, 0, NULL, NULL, 0)"
            )
            query("SELECT title, status FROM reading_items WHERE id = 'r1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Article A", cursor.getString(0))
                org.junit.Assert.assertEquals("UNREAD", cursor.getString(1))
            }
            query("SELECT text, triaged FROM inbox_items WHERE id = 'i1'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Call the dentist", cursor.getString(0))
                org.junit.Assert.assertEquals(0, cursor.getInt(1))
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
            ChronosDatabase.MIGRATION_17_18,
            ChronosDatabase.MIGRATION_18_19,
            ChronosDatabase.MIGRATION_19_20,
            ChronosDatabase.MIGRATION_20_21,
            ChronosDatabase.MIGRATION_21_22,
            ChronosDatabase.MIGRATION_22_23,
            ChronosDatabase.MIGRATION_23_24,
            ChronosDatabase.MIGRATION_24_25,
            ChronosDatabase.MIGRATION_25_26,
            ChronosDatabase.MIGRATION_26_27,
            ChronosDatabase.MIGRATION_27_28,
            ChronosDatabase.MIGRATION_28_29,
            ChronosDatabase.MIGRATION_29_30
        )
    }
}
