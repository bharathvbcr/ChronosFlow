package com.chronosflow.feature.tasks

import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskReminderRule
import com.chronosflow.core.domain.model.TaskReminderTrigger
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class TaskRecurringSupportTest {
    @Test
    fun `buildTaskScheduleFromConfig returns null when recurrence disabled`() {
        val schedule = buildTaskScheduleFromConfig(
            taskId = "task-1",
            existingSchedule = null,
            config = TaskRecurringConfig(enabled = false),
            occurrenceMinuteOfDay = 9 * 60,
            resolver = ResolveNextTaskOccurrenceUseCase(),
            now = Instant.parse("2026-05-25T15:00:00Z")
        )

        assertNull(schedule)
    }

    @Test
    fun `recurringSummary formats disabled config as null`() {
        assertNull(
            recurringSummary(
                config = TaskRecurringConfig(enabled = false),
                occurrenceMinuteOfDay = 10 * 60
            )
        )
    }

    @Test
    fun `recurringSummary formats daily and weekly cadence variants`() {
        val summary = recurringSummary(
            config = TaskRecurringConfig(enabled = true, cadence = TaskRecurringCadence.DAILY, interval = 2),
            occurrenceMinuteOfDay = 9 * 60
        )
        assertEquals("Every 2 days at 9:00 AM", summary)

        val weekly = recurringSummary(
            config = TaskRecurringConfig(
                enabled = true,
                cadence = TaskRecurringCadence.WEEKLY,
                interval = 3,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
            ),
            occurrenceMinuteOfDay = 18 * 60
        )
        assertEquals(
            "Mon + Wed at 6:00 PM, repeats every 3 weeks",
            weekly
        )
    }

    @Test
    fun `recurringSummary formats monthly variants`() {
        val monthlyByDay = recurringSummary(
            config = TaskRecurringConfig(
                enabled = true,
                cadence = TaskRecurringCadence.MONTHLY_DAY_OF_MONTH,
                interval = 2,
                dayOfMonth = 15,
                endsOn = LocalDate.of(2026, 12, 31),
                reminderDrafts = listOf(
                    TaskReminderDraft(
                        id = "r1",
                        trigger = TaskReminderTrigger.AT_TIME,
                        minuteOfDay = 9 * 60
                    ),
                    TaskReminderDraft(id = "r2", trigger = TaskReminderTrigger.BEFORE_OCCURRENCE, offsetMinutesBefore = 15)
                )
            ),
            occurrenceMinuteOfDay = 9 * 60
        )
        assertEquals("Day 15 at 9:00 AM, repeats every 2 months, 2 reminders, ends 2026-12-31", monthlyByDay)

        val monthlyByOrdinal = recurringSummary(
            config = TaskRecurringConfig(
                enabled = true,
                cadence = TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY,
                interval = 1,
                ordinal = 2,
                ordinalWeekday = DayOfWeek.WEDNESDAY
            ),
            occurrenceMinuteOfDay = 18 * 60
        )
        assertEquals("Second Wednesday at 6:00 PM, repeats monthly", monthlyByOrdinal)
    }

    @Test
    fun `toRecurringConfig translates all recurrence rule types`() {
        val common = Instant.parse("2026-05-25T09:00:00Z")
        val daily = TaskSchedule(
            id = "schedule-1",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.Daily(startsOn = LocalDate.parse("2026-05-25")),
            occurrenceMinuteOfDay = 420,
            reminderRules = listOf(
                TaskReminderRule(
                    id = "r1",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 8 * 60
                )
            ),
            createdAt = common,
            updatedAt = common
        )

        val weekly = TaskSchedule(
            id = "schedule-2",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.Weekly(
                intervalWeeks = 2,
                weekdays = setOf(DayOfWeek.MONDAY),
                startsOn = LocalDate.parse("2026-05-24")
            ),
            occurrenceMinuteOfDay = 450,
            createdAt = common,
            updatedAt = common
        )

        val monthlyByDay = TaskSchedule(
            id = "schedule-3",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.MonthlyByDayOfMonth(
                intervalMonths = 1,
                dayOfMonth = 12,
                startsOn = LocalDate.parse("2026-05-25")
            ),
            occurrenceMinuteOfDay = 520,
            createdAt = common,
            updatedAt = common
        )

        val monthlyByOrdinal = TaskSchedule(
            id = "schedule-4",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.MonthlyByOrdinalWeekday(
                intervalMonths = 1,
                ordinal = -1,
                weekday = DayOfWeek.FRIDAY,
                startsOn = LocalDate.parse("2026-05-25")
            ),
            occurrenceMinuteOfDay = null,
            createdAt = common,
            updatedAt = common
        )

        assertEquals(
            "Daily at 7:00 AM, 1 reminder",
            recurringSummary(daily.toRecurringConfig(), daily.occurrenceMinuteOfDay)
        )
        assertEquals(
            "Mon at 7:30 AM, repeats every 2 weeks",
            recurringSummary(weekly.toRecurringConfig(), weekly.occurrenceMinuteOfDay)
        )
        assertEquals(
            "Day 12 at 8:40 AM, repeats monthly",
            recurringSummary(monthlyByDay.toRecurringConfig(), monthlyByDay.occurrenceMinuteOfDay)
        )
        assertEquals(
            "Last Friday, repeats monthly",
            recurringSummary(monthlyByOrdinal.toRecurringConfig(), monthlyByOrdinal.occurrenceMinuteOfDay)
        )
    }

    @Test
    fun `displayOrdinalLabel handles defaults and suffixes`() {
        assertEquals("First", 1.displayOrdinalLabel())
        assertEquals("Second", 2.displayOrdinalLabel())
        assertEquals("Fourth", 4.displayOrdinalLabel())
        assertEquals("Last", (-1).displayOrdinalLabel())
        assertEquals("7th", 7.displayOrdinalLabel())
    }
}
