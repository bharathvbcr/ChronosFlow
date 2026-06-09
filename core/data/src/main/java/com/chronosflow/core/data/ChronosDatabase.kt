package com.chronosflow.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chronosflow.core.data.dao.CalendarEventDao
import com.chronosflow.core.data.dao.DayPlanDao
import com.chronosflow.core.data.dao.AlarmDao
import com.chronosflow.core.data.dao.FocusSessionDao
import com.chronosflow.core.data.dao.HabitEventDao
import com.chronosflow.core.data.dao.HabitDao
import com.chronosflow.core.data.dao.HabitScheduleDao
import com.chronosflow.core.data.dao.MedicationDoseEventDao
import com.chronosflow.core.data.dao.MedicationDao
import com.chronosflow.core.data.dao.MedicationSafetyProfileDao
import com.chronosflow.core.data.dao.MedicationScheduleDao
import com.chronosflow.core.data.dao.MoodEnergyCheckInDao
import com.chronosflow.core.data.dao.RecurrenceRuleDao
import com.chronosflow.core.data.dao.ReviewDao
import com.chronosflow.core.data.dao.TaskDao
import com.chronosflow.core.data.dao.TaskScheduleDao
import com.chronosflow.core.data.dao.TimeBlockDao
import com.chronosflow.core.data.model.ActualTimeSegmentEntity
import com.chronosflow.core.data.model.AlarmRequestEntity
import com.chronosflow.core.data.model.CalendarEventEntity
import com.chronosflow.core.data.model.DailyReviewEntity
import com.chronosflow.core.data.model.DayPlanEntity
import com.chronosflow.core.data.model.FocusSessionEntity
import com.chronosflow.core.data.model.HabitEventEntity
import com.chronosflow.core.data.model.HabitEntity
import com.chronosflow.core.data.model.HabitScheduleEntity
import com.chronosflow.core.data.model.MedicationDoseEventEntity
import com.chronosflow.core.data.model.MedicationPlanEntity
import com.chronosflow.core.data.model.MedicationSafetyProfileEntity
import com.chronosflow.core.data.model.MedicationScheduleEntity
import com.chronosflow.core.data.model.MoodEnergyCheckInEntity
import com.chronosflow.core.data.model.RecurrenceRuleEntity
import com.chronosflow.core.data.model.ReviewInsightEntity
import com.chronosflow.core.data.model.TaskEntity
import com.chronosflow.core.data.model.TaskActionEntity
import com.chronosflow.core.data.model.TaskAttachmentEntity
import com.chronosflow.core.data.model.TaskChecklistItemEntity
import com.chronosflow.core.data.model.TaskContactMethodEntity
import com.chronosflow.core.data.model.TaskContactSnapshotEntity
import com.chronosflow.core.data.model.TaskReminderRuleEntity
import com.chronosflow.core.data.model.TaskScheduleEntity
import com.chronosflow.core.data.model.TimeBlockEntity
import com.chronosflow.core.data.util.Converters

@Database(
    entities = [
        TaskEntity::class,
        TimeBlockEntity::class,
        DayPlanEntity::class,
        RecurrenceRuleEntity::class,
        HabitEntity::class,
        HabitScheduleEntity::class,
        HabitEventEntity::class,
        MedicationPlanEntity::class,
        MedicationScheduleEntity::class,
        MedicationDoseEventEntity::class,
        MedicationSafetyProfileEntity::class,
        DailyReviewEntity::class,
        ReviewInsightEntity::class,
        ActualTimeSegmentEntity::class,
        AlarmRequestEntity::class,
        FocusSessionEntity::class,
        CalendarEventEntity::class,
        MoodEnergyCheckInEntity::class,
        TaskChecklistItemEntity::class,
        TaskContactSnapshotEntity::class,
        TaskContactMethodEntity::class,
        TaskActionEntity::class,
        TaskAttachmentEntity::class,
        TaskScheduleEntity::class,
        TaskReminderRuleEntity::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ChronosDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskScheduleDao(): TaskScheduleDao
    abstract fun timeBlockDao(): TimeBlockDao
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
    abstract fun habitDao(): HabitDao
    abstract fun habitScheduleDao(): HabitScheduleDao
    abstract fun habitEventDao(): HabitEventDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationScheduleDao(): MedicationScheduleDao
    abstract fun medicationDoseEventDao(): MedicationDoseEventDao
    abstract fun medicationSafetyProfileDao(): MedicationSafetyProfileDao
    abstract fun reviewDao(): ReviewDao
    abstract fun alarmDao(): AlarmDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun moodEnergyCheckInDao(): MoodEnergyCheckInDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN provenance TEXT NOT NULL DEFAULT 'USER_CREATED'")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN flexibility TEXT NOT NULL DEFAULT 'MOVABLE'")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN energyLevel INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN recurrenceRuleId TEXT")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN actualStartMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN actualEndMinuteOfDay INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurrence_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        blockId TEXT NOT NULL,
                        pattern TEXT NOT NULL,
                        intervalWeeks INTEGER NOT NULL,
                        startsOn TEXT,
                        endsOn TEXT,
                        maxOccurrences INTEGER,
                        weekdays TEXT,
                        createdAt INTEGER,
                        updatedAt INTEGER
                    )
                    """
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habits (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        cadence TEXT NOT NULL,
                        windowStartMinute INTEGER NOT NULL,
                        windowEndMinute INTEGER NOT NULL,
                        difficulty INTEGER NOT NULL,
                        isBundled INTEGER NOT NULL,
                        streakCount INTEGER NOT NULL,
                        lastCompletedDate TEXT,
                        isActive INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_isActive ON habits(isActive)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_windowStartMinute ON habits(windowStartMinute)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_windowEndMinute ON habits(windowEndMinute)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_plans (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        dosage TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        notes TEXT,
                        startAt TEXT,
                        endAt TEXT,
                        reminderMinuteOfDay INTEGER NOT NULL,
                        takeWithFood INTEGER NOT NULL,
                        missedCount INTEGER NOT NULL,
                        refillNeededAfterDoses INTEGER,
                        isActive INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_plans_isActive ON medication_plans(isActive)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_plans_reminderMinuteOfDay ON medication_plans(reminderMinuteOfDay)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_reviews (
                        date TEXT NOT NULL PRIMARY KEY,
                        plannedMinutes INTEGER NOT NULL,
                        actualMinutes INTEGER NOT NULL,
                        missedMinutes INTEGER NOT NULL,
                        driftMinutes INTEGER NOT NULL,
                        completedBlockCount INTEGER NOT NULL,
                        missedBlockCount INTEGER NOT NULL,
                        insightsJson TEXT NOT NULL
                    )
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS actual_time_segments (
                        id TEXT NOT NULL PRIMARY KEY,
                        blockId TEXT,
                        date TEXT NOT NULL,
                        startInstant INTEGER NOT NULL,
                        endInstant INTEGER,
                        source TEXT NOT NULL,
                        confidence REAL NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_actual_time_segments_date ON actual_time_segments(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_actual_time_segments_blockId ON actual_time_segments(blockId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alarm_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        scheduledFor INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        medicationPlanId TEXT,
                        blockId TEXT,
                        reliability TEXT NOT NULL,
                        deliveryState TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deliveredAt INTEGER,
                        failureReason TEXT
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_requests_type ON alarm_requests(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_requests_scheduledFor ON alarm_requests(scheduledFor)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_requests_deliveryState ON alarm_requests(deliveryState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_requests_medicationPlanId ON alarm_requests(medicationPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_requests_blockId ON alarm_requests(blockId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        blockId TEXT,
                        state TEXT NOT NULL,
                        startedAt INTEGER,
                        plannedEndAt INTEGER,
                        pausedAt INTEGER,
                        completedAt INTEGER,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_blockId ON focus_sessions(blockId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_state ON focus_sessions(state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_startedAt ON focus_sessions(startedAt)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS review_insights (
                        id TEXT NOT NULL PRIMARY KEY,
                        date TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        relatedBlockId TEXT,
                        severity TEXT NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_insights_date ON review_insights(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_insights_type ON review_insights(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_insights_severity ON review_insights(severity)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_insights_relatedBlockId ON review_insights(relatedBlockId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS calendar_events (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT,
                        startAt INTEGER NOT NULL,
                        endAt INTEGER NOT NULL,
                        timezone TEXT NOT NULL,
                        location TEXT,
                        externalId TEXT,
                        isAllDay INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_startAt ON calendar_events(startAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_endAt ON calendar_events(endAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_externalId ON calendar_events(externalId)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mood_energy_check_ins (
                        id TEXT NOT NULL PRIMARY KEY,
                        checkInDate TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        blockId TEXT,
                        moodScore INTEGER NOT NULL,
                        stressScore INTEGER NOT NULL,
                        energyScore INTEGER NOT NULL,
                        focusScore INTEGER NOT NULL,
                        notes TEXT
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_energy_check_ins_checkInDate ON mood_energy_check_ins(checkInDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_energy_check_ins_blockId ON mood_energy_check_ins(blockId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN preferredDurationMinutes INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN preferredStartMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN targetDate TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_checklist_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_checklist_items_taskId ON task_checklist_items(taskId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN totalSeconds INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_contact_snapshots (
                        taskId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        lookupKey TEXT,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_task_contact_snapshots_taskId ON task_contact_snapshots(taskId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_contact_methods (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        label TEXT,
                        value TEXT NOT NULL,
                        normalizedValue TEXT,
                        isPrimary INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_contact_methods_taskId ON task_contact_methods(taskId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_actions (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        value TEXT NOT NULL,
                        isPrimary INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_actions_taskId ON task_actions(taskId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val nowExpression = "(CAST(strftime('%s','now') AS INTEGER) * 1000)"
                val reminder2Expression = """
                    CASE
                        WHEN notes LIKE '%[[reminder2:%]]%' THEN CAST(
                            substr(
                                notes,
                                instr(notes, '[[reminder2:') + 12,
                                instr(substr(notes, instr(notes, '[[reminder2:') + 12), ']]') - 1
                            ) AS INTEGER
                        )
                        ELSE NULL
                    END
                """.trimIndent()
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_schedules (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        recurrenceType TEXT NOT NULL,
                        intervalCount INTEGER NOT NULL,
                        weekdaysCsv TEXT,
                        targetStartMinute INTEGER NOT NULL,
                        targetEndMinute INTEGER NOT NULL,
                        plannerVisible INTEGER NOT NULL,
                        pausedUntil TEXT,
                        skipDate TEXT,
                        deferUntilMinuteOfDay INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_schedules_habitId ON habit_schedules(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_schedules_pausedUntil ON habit_schedules(pausedUntil)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_schedules_skipDate ON habit_schedules(skipDate)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        reason TEXT,
                        startMinuteOfDay INTEGER,
                        endMinuteOfDay INTEGER
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_events_habitId ON habit_events(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_events_eventDate ON habit_events(eventDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_events_habitId_eventDate ON habit_events(habitId, eventDate)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_schedules (
                        id TEXT NOT NULL PRIMARY KEY,
                        medicationPlanId TEXT NOT NULL,
                        recurrenceType TEXT NOT NULL,
                        intervalCount INTEGER NOT NULL,
                        weekdaysCsv TEXT,
                        doseTimesCsv TEXT NOT NULL,
                        plannerVisible INTEGER NOT NULL,
                        pausedUntil TEXT,
                        windowMinutes INTEGER NOT NULL,
                        isPrn INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_medication_schedules_medicationPlanId ON medication_schedules(medicationPlanId)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_schedules_pausedUntil ON medication_schedules(pausedUntil)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_dose_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        medicationPlanId TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        scheduledMinuteOfDay INTEGER,
                        reason TEXT,
                        doseAmount TEXT
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_dose_events_medicationPlanId ON medication_dose_events(medicationPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_dose_events_eventDate ON medication_dose_events(eventDate)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medication_dose_events_medicationPlanId_eventDate ON medication_dose_events(medicationPlanId, eventDate)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medication_safety_profiles (
                        medicationPlanId TEXT NOT NULL PRIMARY KEY,
                        form TEXT NOT NULL,
                        route TEXT NOT NULL,
                        strength TEXT,
                        instructions TEXT,
                        mealTiming TEXT NOT NULL,
                        supplyRemaining INTEGER,
                        refillThreshold INTEGER,
                        pharmacyName TEXT,
                        prescriberName TEXT,
                        cautionsCsv TEXT
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO habit_schedules (
                        id,
                        habitId,
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
                        updatedAt
                    )
                    SELECT
                        'schedule-' || id,
                        id,
                        CASE LOWER(cadence)
                            WHEN 'weekdays' THEN 'WEEKDAYS'
                            WHEN 'weekends' THEN 'WEEKENDS'
                            WHEN 'weekly' THEN 'WEEKLY_INTERVAL'
                            ELSE 'DAILY'
                        END,
                        1,
                        CASE LOWER(cadence)
                            WHEN 'weekdays' THEN 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY'
                            WHEN 'weekends' THEN 'SATURDAY,SUNDAY'
                            ELSE NULL
                        END,
                        windowStartMinute,
                        windowEndMinute,
                        isBundled,
                        NULL,
                        NULL,
                        NULL,
                        $nowExpression,
                        $nowExpression
                    FROM habits
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO medication_schedules (
                        id,
                        medicationPlanId,
                        recurrenceType,
                        intervalCount,
                        weekdaysCsv,
                        doseTimesCsv,
                        plannerVisible,
                        pausedUntil,
                        windowMinutes,
                        isPrn,
                        createdAt,
                        updatedAt
                    )
                    SELECT
                        'schedule-' || id,
                        id,
                        CASE
                            WHEN $reminder2Expression IS NOT NULL THEN 'MULTIPLE_TIMES_DAILY'
                            ELSE 'DAILY'
                        END,
                        1,
                        NULL,
                        CASE
                            WHEN $reminder2Expression IS NOT NULL THEN CAST(reminderMinuteOfDay AS TEXT) || ',' || CAST($reminder2Expression AS TEXT)
                            ELSE CAST(reminderMinuteOfDay AS TEXT)
                        END,
                        isActive,
                        NULL,
                        15,
                        0,
                        $nowExpression,
                        $nowExpression
                    FROM medication_plans
                    """
                )
                db.execSQL(
                    """
                    INSERT INTO medication_safety_profiles (
                        medicationPlanId,
                        form,
                        route,
                        strength,
                        instructions,
                        mealTiming,
                        supplyRemaining,
                        refillThreshold,
                        pharmacyName,
                        prescriberName,
                        cautionsCsv
                    )
                    SELECT
                        id,
                        CASE LOWER(unit)
                            WHEN 'tablet' THEN 'tablet'
                            WHEN 'capsule' THEN 'capsule'
                            WHEN 'liquid' THEN 'liquid'
                            WHEN 'ml' THEN 'liquid'
                            WHEN 'drop' THEN 'drop'
                            WHEN 'inhaler' THEN 'inhaler'
                            WHEN 'injection' THEN 'injection'
                            WHEN 'patch' THEN 'patch'
                            WHEN 'powder' THEN 'powder'
                            ELSE 'tablet'
                        END,
                        CASE LOWER(unit)
                            WHEN 'inhaler' THEN 'inhaled'
                            ELSE 'oral'
                        END,
                        NULL,
                        trim(notes),
                        CASE
                            WHEN takeWithFood = 1 THEN 'With food'
                            WHEN notes LIKE '%[[timing:before_bed]]%' THEN 'Before bed'
                            WHEN notes LIKE '%[[timing:anytime]]%' THEN 'Anytime'
                            ELSE 'Anytime'
                        END,
                        refillNeededAfterDoses,
                        refillNeededAfterDoses,
                        NULL,
                        NULL,
                        NULL
                    FROM medication_plans
                    """
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_attachments (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT,
                        sizeBytes INTEGER,
                        kind TEXT NOT NULL,
                        storageMode TEXT NOT NULL,
                        reference TEXT NOT NULL,
                        persistedUriPermission INTEGER NOT NULL,
                        isFeaturedImage INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_attachments_taskId ON task_attachments(taskId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE time_blocks ADD COLUMN taskOccurrenceDate TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_schedules (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        recurrenceType TEXT NOT NULL,
                        intervalCount INTEGER NOT NULL,
                        weekdaysCsv TEXT,
                        dayOfMonth INTEGER,
                        ordinalInMonth INTEGER,
                        weekdayInMonth TEXT,
                        startsOn TEXT NOT NULL,
                        endsOn TEXT,
                        maxOccurrences INTEGER,
                        occurrenceMinuteOfDay INTEGER,
                        nextOccurrenceDate TEXT,
                        lastCompletedOccurrenceDate TEXT,
                        generatedThroughDate TEXT,
                        isPaused INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_reminder_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        taskScheduleId TEXT NOT NULL,
                        trigger TEXT NOT NULL,
                        minuteOfDay INTEGER,
                        offsetMinutesBefore INTEGER,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(taskScheduleId) REFERENCES task_schedules(id) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_time_blocks_taskOccurrenceDate ON time_blocks(taskOccurrenceDate)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_schedules_taskId ON task_schedules(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_schedules_nextOccurrenceDate ON task_schedules(nextOccurrenceDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_schedules_generatedThroughDate ON task_schedules(generatedThroughDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_reminder_rules_taskScheduleId ON task_reminder_rules(taskScheduleId)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_schedules ADD COLUMN recurrenceRuleKind TEXT")
                db.execSQL("ALTER TABLE habit_schedules ADD COLUMN quotaTargetCompletions INTEGER")
                db.execSQL("ALTER TABLE habit_schedules ADD COLUMN quotaPeriodUnit TEXT")
                db.execSQL(
                    """
                    UPDATE habit_schedules
                    SET recurrenceRuleKind = 'SCHEDULED'
                    WHERE recurrenceRuleKind IS NULL
                    """
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN launchAppLabel TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN launchAppValue TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_insights ADD COLUMN assistSource TEXT")
            }
        }
    }
}
