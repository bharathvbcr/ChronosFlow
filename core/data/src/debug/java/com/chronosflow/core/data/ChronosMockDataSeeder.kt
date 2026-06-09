package com.chronosflow.core.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ChronosMockDataSeeder : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        if (db.query("SELECT COUNT(*) FROM time_blocks").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            } > 0L
        ) {
            return
        }

        val today = LocalDate.now()
        seedDay(db, today.minusDays(1), yesterdayBlocks())
        seedDay(db, today, todayBlocks())
        seedDay(db, today.plusDays(1), tomorrowBlocks())
    }

    private fun seedDay(db: SupportSQLiteDatabase, date: LocalDate, blocks: List<MockTimeBlock>) {
        val timezone = ZoneId.systemDefault().id
        val now = Instant.now().toEpochMilli()
        blocks.forEachIndexed { index, block ->
            db.execSQL(
                """
                INSERT INTO time_blocks (
                    id,
                    date,
                    title,
                    category,
                    startMinuteOfDay,
                    durationMinutes,
                    timezone,
                    source,
                    provenance,
                    flexibility,
                    energyLevel,
                    taskId,
                    calendarEventId,
                    medicationPlanId,
                    habitId,
                    isLocked,
                    isProtected,
                    recurrenceRuleId,
                    actualStartMinuteOfDay,
                    actualEndMinuteOfDay,
                    createdAt,
                    updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "mock-${date}-$index",
                    date.toString(),
                    block.title,
                    block.category,
                    block.startMinuteOfDay,
                    block.durationMinutes,
                    timezone,
                    block.source,
                    block.provenance,
                    block.flexibility,
                    block.energyLevel,
                    block.taskId,
                    block.calendarEventId,
                    block.medicationPlanId,
                    block.habitId,
                    if (block.isLocked) 1 else 0,
                    if (block.isProtected) 1 else 0,
                    block.recurrenceRuleId,
                    block.actualStartMinuteOfDay,
                    block.actualEndMinuteOfDay,
                    now,
                    now
                )
            )
        }
    }

    private fun yesterdayBlocks() = listOf(
        MockTimeBlock(
            title = "Morning planning review",
            category = "PLANNING",
            startMinuteOfDay = 8 * 60,
            durationMinutes = 30,
            provenance = "USER_CREATED",
            flexibility = "MOVABLE",
            actualStartMinuteOfDay = 8 * 60 + 5,
            actualEndMinuteOfDay = 8 * 60 + 30
        ),
        MockTimeBlock(
            title = "Design prototype pass",
            category = "WORK",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 120,
            provenance = "TASK_CONVERTED",
            flexibility = "RESIZABLE",
            energyLevel = 4,
            taskId = "mock-task-design",
            actualStartMinuteOfDay = 9 * 60,
            actualEndMinuteOfDay = 11 * 60 + 10
        ),
        MockTimeBlock(
            title = "Skipped admin catch-up",
            category = "ADMIN",
            startMinuteOfDay = 15 * 60,
            durationMinutes = 45,
            provenance = "USER_CREATED",
            flexibility = "OPTIONAL"
        )
    )

    private fun todayBlocks() = listOf(
        MockTimeBlock(
            title = "Morning routine",
            category = "ROUTINE",
            startMinuteOfDay = 7 * 60,
            durationMinutes = 45,
            provenance = "SYSTEM_GENERATED",
            flexibility = "FIXED",
            energyLevel = 2,
            isProtected = true,
            actualStartMinuteOfDay = 7 * 60,
            actualEndMinuteOfDay = 7 * 60 + 40
        ),
        MockTimeBlock(
            title = "Deep work: product spec",
            category = "WORK",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 110,
            provenance = "AI_SUGGESTED",
            flexibility = "RESIZABLE",
            energyLevel = 4,
            source = "AI",
            actualStartMinuteOfDay = 9 * 60 + 10,
            actualEndMinuteOfDay = 10 * 60 + 45
        ),
        MockTimeBlock(
            title = "Stakeholder sync",
            category = "MEETING",
            startMinuteOfDay = 10 * 60 + 30,
            durationMinutes = 60,
            provenance = "CALENDAR_IMPORTED",
            flexibility = "FIXED",
            energyLevel = 3,
            source = "CALENDAR",
            calendarEventId = 240501L,
            isLocked = true
        ),
        MockTimeBlock(
            title = "Lunch and walk",
            category = "RECOVERY",
            startMinuteOfDay = 12 * 60 + 15,
            durationMinutes = 45,
            provenance = "USER_CREATED",
            flexibility = "MOVABLE",
            energyLevel = 1
        ),
        MockTimeBlock(
            title = "Focus sprint: implementation",
            category = "FOCUS",
            startMinuteOfDay = 14 * 60,
            durationMinutes = 90,
            provenance = "TASK_CONVERTED",
            flexibility = "RESIZABLE",
            energyLevel = 5,
            taskId = "mock-task-implementation"
        ),
        MockTimeBlock(
            title = "Medication reminder",
            category = "HEALTH",
            startMinuteOfDay = 18 * 60,
            durationMinutes = 10,
            provenance = "SYSTEM_GENERATED",
            flexibility = "FIXED",
            energyLevel = 1,
            medicationPlanId = "mock-med-evening",
            isProtected = true
        )
    )

    private fun tomorrowBlocks() = listOf(
        MockTimeBlock(
            title = "Weekly planning",
            category = "PLANNING",
            startMinuteOfDay = 8 * 60 + 30,
            durationMinutes = 60,
            provenance = "AI_SUGGESTED",
            flexibility = "MOVABLE",
            source = "AI"
        ),
        MockTimeBlock(
            title = "Calendar hold: demo prep",
            category = "MEETING",
            startMinuteOfDay = 11 * 60,
            durationMinutes = 45,
            provenance = "CALENDAR_IMPORTED",
            flexibility = "FIXED",
            source = "CALENDAR",
            calendarEventId = 240502L,
            isLocked = true
        ),
        MockTimeBlock(
            title = "Habit block: language practice",
            category = "HABIT",
            startMinuteOfDay = 17 * 60,
            durationMinutes = 30,
            provenance = "SYSTEM_GENERATED",
            flexibility = "OPTIONAL",
            habitId = "mock-habit-language"
        )
    )

    private data class MockTimeBlock(
        val title: String,
        val category: String,
        val startMinuteOfDay: Int,
        val durationMinutes: Int,
        val provenance: String,
        val flexibility: String,
        val energyLevel: Int = 2,
        val source: String = "MOCK",
        val taskId: String? = null,
        val calendarEventId: Long? = null,
        val medicationPlanId: String? = null,
        val habitId: String? = null,
        val isLocked: Boolean = false,
        val isProtected: Boolean = false,
        val recurrenceRuleId: String? = null,
        val actualStartMinuteOfDay: Int? = null,
        val actualEndMinuteOfDay: Int? = null
    )
}
