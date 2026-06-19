package com.ChronosFlow.VBCR.core.data.mapper

import com.ChronosFlow.VBCR.core.data.model.TaskReminderRuleEntity
import com.ChronosFlow.VBCR.core.data.model.TaskScheduleEntity
import com.ChronosFlow.VBCR.core.data.model.TaskScheduleWithReminderRules
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderRule
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderTrigger
import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceType
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScheduleMappersTest {

    @Test
    fun `toDomain sorts reminders and maps recurrence from valid and invalid enum values`() {
        val schedule = TaskScheduleWithReminderRules(
            schedule = TaskScheduleEntity(
                id = "schedule-1",
                taskId = "task-1",
                recurrenceType = TaskRecurrenceType.WEEKLY.name,
                intervalCount = 2,
                weekdaysCsv = "MONDAY,INVALID,WEDNESDAY",
                dayOfMonth = null,
                ordinalInMonth = null,
                weekdayInMonth = null,
                startsOn = LocalDate.of(2026, 5, 1),
                endsOn = LocalDate.of(2026, 12, 31),
                maxOccurrences = 10,
                occurrenceMinuteOfDay = 480,
                nextOccurrenceDate = LocalDate.of(2026, 5, 2),
                lastCompletedOccurrenceDate = null,
                generatedThroughDate = null,
                isPaused = false,
                createdAt = Instant.parse("2026-05-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-05-02T10:00:00Z")
            ),
            reminderRules = listOf(
                TaskReminderRuleEntity("r1", "schedule-1", "BEFORE_OCCURRENCE", null, 15, 2),
                TaskReminderRuleEntity("r2", "schedule-1", "AT_TIME", 540, null, 1),
                TaskReminderRuleEntity("r3", "schedule-1", "UNKNOWN_TRIGGER", 600, null, 3)
            )
        )

        val domain = schedule.toDomain()

        assertEquals("schedule-1", domain.id)
        assertEquals("task-1", domain.taskId)
        assertEquals(TaskRecurrenceType.WEEKLY, domain.recurrenceRule.type)
        val weekly = domain.recurrenceRule as TaskRecurrenceRule.Weekly
        assertEquals(2, weekly.intervalWeeks)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), weekly.weekdays)
        assertEquals(LocalDate.of(2026, 12, 31), weekly.endsOn)
        assertEquals(listOf("r2", "r1", "r3"), domain.reminderRules.map { it.id })
        assertEquals(TaskReminderTrigger.AT_TIME, domain.reminderRules.first().trigger)
        assertEquals(TaskReminderTrigger.BEFORE_OCCURRENCE, domain.reminderRules[1].trigger)
        assertEquals(TaskReminderTrigger.AT_TIME, domain.reminderRules[2].trigger)
    }

    @Test
    fun `toDomain falls back to safe defaults on malformed recurrence data`() {
        val schedule = TaskScheduleWithReminderRules(
            schedule = TaskScheduleEntity(
                id = "schedule-2",
                taskId = "task-2",
                recurrenceType = "BROKEN_TYPE",
                intervalCount = 5,
                weekdaysCsv = null,
                dayOfMonth = null,
                ordinalInMonth = null,
                weekdayInMonth = null,
                startsOn = LocalDate.of(2026, 6, 2),
                endsOn = null,
                maxOccurrences = null,
                occurrenceMinuteOfDay = null,
                nextOccurrenceDate = null,
                lastCompletedOccurrenceDate = null,
                generatedThroughDate = null,
                isPaused = false,
                createdAt = Instant.parse("2026-06-02T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-02T10:00:00Z")
            ),
            reminderRules = emptyList()
        )

        val domain = schedule.toDomain()
        assertTrue(domain.recurrenceRule is TaskRecurrenceRule.Daily)
        assertEquals(5, (domain.recurrenceRule as TaskRecurrenceRule.Daily).intervalDays)

        val monthlyByDayOfMonth = TaskScheduleWithReminderRules(
            schedule = TaskScheduleEntity(
                id = "schedule-3",
                taskId = "task-3",
                recurrenceType = TaskRecurrenceType.MONTHLY_DAY_OF_MONTH.name,
                intervalCount = 1,
                weekdaysCsv = null,
                dayOfMonth = null,
                ordinalInMonth = null,
                weekdayInMonth = null,
                startsOn = LocalDate.of(2026, 7, 16),
                endsOn = null,
                maxOccurrences = null,
                occurrenceMinuteOfDay = null,
                nextOccurrenceDate = null,
                lastCompletedOccurrenceDate = null,
                generatedThroughDate = null,
                isPaused = false,
                createdAt = Instant.parse("2026-07-16T10:00:00Z"),
                updatedAt = Instant.parse("2026-07-16T10:00:00Z")
            ),
            reminderRules = emptyList()
        ).toDomain()

        val monthlyByOrdinal = TaskScheduleWithReminderRules(
            schedule = TaskScheduleEntity(
                id = "schedule-4",
                taskId = "task-4",
                recurrenceType = TaskRecurrenceType.MONTHLY_ORDINAL_WEEKDAY.name,
                intervalCount = 1,
                weekdaysCsv = null,
                dayOfMonth = null,
                ordinalInMonth = null,
                weekdayInMonth = "BOGUS_WEEKDAY",
                startsOn = LocalDate.of(2026, 8, 18),
                endsOn = null,
                maxOccurrences = null,
                occurrenceMinuteOfDay = null,
                nextOccurrenceDate = null,
                lastCompletedOccurrenceDate = null,
                generatedThroughDate = null,
                isPaused = false,
                createdAt = Instant.parse("2026-08-18T10:00:00Z"),
                updatedAt = Instant.parse("2026-08-18T10:00:00Z")
            ),
            reminderRules = emptyList()
        ).toDomain()

        assertTrue(monthlyByDayOfMonth.recurrenceRule is TaskRecurrenceRule.MonthlyByDayOfMonth)
        val byDayOfMonth = monthlyByDayOfMonth.recurrenceRule as TaskRecurrenceRule.MonthlyByDayOfMonth
        assertEquals(16, byDayOfMonth.dayOfMonth)

        assertTrue(monthlyByOrdinal.recurrenceRule is TaskRecurrenceRule.MonthlyByOrdinalWeekday)
        val byOrdinal = monthlyByOrdinal.recurrenceRule as TaskRecurrenceRule.MonthlyByOrdinalWeekday
        assertEquals(1, byOrdinal.ordinal)
        assertEquals(DayOfWeek.TUESDAY, byOrdinal.weekday)
        assertEquals(LocalDate.of(2026, 7, 16), (monthlyByDayOfMonth.recurrenceRule as TaskRecurrenceRule.MonthlyByDayOfMonth).startsOn)
    }

    @Test
    fun `toEntity preserves recurrence payloads and supports reminder round-trip`() {
        val schedule = TaskSchedule(
            id = "schedule-5",
            taskId = "task-5",
            recurrenceRule = TaskRecurrenceRule.Weekly(
                intervalWeeks = 3,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                startsOn = LocalDate.of(2026, 9, 1),
                maxOccurrences = 6
            ),
            occurrenceMinuteOfDay = 540,
            isPaused = true,
            reminderRules = listOf(
                TaskReminderRule(
                    id = "r1",
                    taskScheduleId = "schedule-5",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 540
                ),
                TaskReminderRule(
                    id = "r2",
                    taskScheduleId = "schedule-5",
                    trigger = TaskReminderTrigger.BEFORE_OCCURRENCE,
                    offsetMinutesBefore = 20
                )
            ),
            createdAt = Instant.parse("2026-09-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-09-01T11:00:00Z")
        )

        val entity = schedule.toEntity()

        assertEquals(TaskRecurrenceType.WEEKLY.name, entity.recurrenceType)
        assertEquals(3, entity.intervalCount)
        assertEquals(setOf("MONDAY", "WEDNESDAY"), entity.weekdaysCsv.toCsvSet())
        assertEquals(540, entity.occurrenceMinuteOfDay)
        assertEquals(true, entity.isPaused)

        val reminders = schedule.reminderRules.mapIndexed { index, rule -> rule.toEntity(index) }
        val roundTripped = TaskScheduleWithReminderRules(
            schedule = entity,
            reminderRules = reminders
        ).toDomain()

        assertEquals(schedule.id, roundTripped.id)
        assertEquals(schedule.taskId, roundTripped.taskId)
        assertEquals(schedule.occurrenceMinuteOfDay, roundTripped.occurrenceMinuteOfDay)
        assertEquals(2, roundTripped.reminderRules.size)
        assertEquals("r1", roundTripped.reminderRules.first().id)
        assertEquals("r2", roundTripped.reminderRules[1].id)
    }
}

private fun String?.toCsvSet(): Set<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
