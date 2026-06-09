package com.chronosflow.core.data.mapper

import com.chronosflow.core.data.model.HabitEntity
import com.chronosflow.core.data.model.HabitScheduleEntity
import java.time.LocalDate
import com.chronosflow.core.domain.model.HabitRecurrencePeriodUnit
import com.chronosflow.core.domain.model.HabitRecurrenceRule
import com.chronosflow.core.domain.model.HabitSchedule
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitScheduleMapperTest {

    @Test
    fun `quota recurrence rules round trip through persistence mapping`() {
        val schedule = HabitSchedule(
            id = "schedule-1",
            habitId = "habit-1",
            recurrenceRule = HabitRecurrenceRule.Quota(
                targetCompletions = 3,
                periodUnit = HabitRecurrencePeriodUnit.WEEK,
                interval = 2
            ),
            targetStartMinute = 8 * 60,
            targetEndMinute = 10 * 60,
            plannerVisible = true
        )

        val persistedAt = Instant.parse("2026-05-25T11:00:00Z")
        val roundTripped = schedule.toEntity(createdAt = persistedAt, updatedAt = persistedAt).toDomain()

        assertEquals(schedule, roundTripped)
    }

    @Test
    fun `legacy scheduled rows still resolve from planner recurrence fields`() {
        val entity = HabitScheduleEntity(
            id = "schedule-2",
            habitId = "habit-2",
            recurrenceType = PlannerRecurrenceType.SELECTED_WEEKDAYS.name,
            intervalCount = 1,
            weekdaysCsv = "MONDAY,WEDNESDAY,FRIDAY",
            targetStartMinute = 7 * 60,
            targetEndMinute = 9 * 60,
            plannerVisible = false,
            pausedUntil = null,
            skipDate = null,
            deferUntilMinuteOfDay = null,
            createdAt = Instant.parse("2026-05-25T11:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T11:30:00Z")
        )

        val schedule = entity.toDomain()

        assertEquals(PlannerRecurrenceType.SELECTED_WEEKDAYS, schedule.recurrence.type)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            schedule.recurrence.weekdays
        )
        assertNull(schedule.recurrenceRule)
        assertEquals(HabitRecurrenceRule.Scheduled(schedule.recurrence), schedule.resolvedRecurrenceRule)
    }

    @Test
    fun `habit launch target round trips through persistence mapping`() {
        val entity = HabitEntity(
            id = "habit-1",
            title = "Journal",
            cadence = "Daily",
            windowStartMinute = 20 * 60,
            windowEndMinute = 22 * 60,
            difficulty = 2,
            isBundled = true,
            streakCount = 3,
            lastCompletedDate = LocalDate.of(2026, 5, 24),
            isActive = true,
            launchAppLabel = "Open Journal",
            launchAppValue = "package:com.example.journal"
        )

        val habit = entity.toDomain()
        val roundTripped = habit.toEntity()

        assertEquals("Open Journal", habit.launchTarget?.label)
        assertEquals("com.example.journal", habit.launchTarget?.value)
        assertEquals("com.example.journal", roundTripped.launchAppValue)
    }
}
