package com.chronosflow.core.data.repository

import app.cash.turbine.test
import com.chronosflow.core.data.dao.TaskScheduleDao
import com.chronosflow.core.data.model.TaskReminderRuleEntity
import com.chronosflow.core.data.model.TaskScheduleEntity
import com.chronosflow.core.data.model.TaskScheduleWithReminderRules
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskReminderRule
import com.chronosflow.core.domain.model.TaskReminderTrigger
import com.chronosflow.core.domain.model.TaskSchedule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskScheduleRepositoryImplTest {

    private val taskScheduleDao: TaskScheduleDao = mockk()
    private lateinit var repository: TaskScheduleRepositoryImpl

    @Before
    fun setup() {
        repository = TaskScheduleRepositoryImpl(taskScheduleDao)
    }

    @Test
    fun `observeTaskSchedule maps monthly ordinal schedule and reminders`() = runTest {
        val now = Instant.parse("2026-05-25T15:00:00Z")
        every { taskScheduleDao.observeScheduleForTask("task-1") } returns flowOf(
            TaskScheduleWithReminderRules(
                schedule = TaskScheduleEntity(
                    id = "schedule-1",
                    taskId = "task-1",
                    recurrenceType = "MONTHLY_ORDINAL_WEEKDAY",
                    intervalCount = 2,
                    weekdaysCsv = null,
                    dayOfMonth = null,
                    ordinalInMonth = -1,
                    weekdayInMonth = DayOfWeek.FRIDAY.name,
                    startsOn = LocalDate.of(2026, 5, 30),
                    endsOn = LocalDate.of(2026, 12, 31),
                    maxOccurrences = 8,
                    occurrenceMinuteOfDay = 9 * 60,
                    nextOccurrenceDate = LocalDate.of(2026, 6, 26),
                    lastCompletedOccurrenceDate = LocalDate.of(2026, 5, 29),
                    generatedThroughDate = LocalDate.of(2026, 6, 30),
                    isPaused = false,
                    createdAt = now,
                    updatedAt = now
                ),
                reminderRules = listOf(
                    TaskReminderRuleEntity(
                        id = "reminder-1",
                        taskScheduleId = "schedule-1",
                        trigger = "AT_TIME",
                        minuteOfDay = 9 * 60,
                        offsetMinutesBefore = null,
                        sortOrder = 0
                    ),
                    TaskReminderRuleEntity(
                        id = "reminder-2",
                        taskScheduleId = "schedule-1",
                        trigger = "BEFORE_OCCURRENCE",
                        minuteOfDay = null,
                        offsetMinutesBefore = 30,
                        sortOrder = 1
                    )
                )
            )
        )

        repository.observeTaskSchedule("task-1").test {
            val result = awaitItem()
            assertTrue(result?.recurrenceRule is TaskRecurrenceRule.MonthlyByOrdinalWeekday)
            val recurrence = result?.recurrenceRule as TaskRecurrenceRule.MonthlyByOrdinalWeekday
            assertEquals(-1, recurrence.ordinal)
            assertEquals(DayOfWeek.FRIDAY, recurrence.weekday)
            assertEquals(LocalDate.of(2026, 6, 26), result.nextOccurrenceDate)
            assertEquals(2, result.reminderRules.size)
            assertEquals(
                listOf(TaskReminderTrigger.AT_TIME, TaskReminderTrigger.BEFORE_OCCURRENCE),
                result.reminderRules.map(TaskReminderRule::trigger)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saveTaskSchedule upserts schedule and replaces reminder rules`() = runTest {
        val now = Instant.parse("2026-05-25T15:00:00Z")
        val schedule = TaskSchedule(
            id = "schedule-1",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.Weekly(
                intervalWeeks = 1,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                startsOn = LocalDate.of(2026, 5, 25),
                endsOn = null,
                maxOccurrences = null
            ),
            occurrenceMinuteOfDay = 8 * 60 + 30,
            nextOccurrenceDate = LocalDate.of(2026, 5, 28),
            lastCompletedOccurrenceDate = null,
            generatedThroughDate = null,
            isPaused = false,
            reminderRules = listOf(
                TaskReminderRule(
                    id = "reminder-1",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 8 * 60 + 30,
                    offsetMinutesBefore = null
                ),
                TaskReminderRule(
                    id = "reminder-2",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.BEFORE_OCCURRENCE,
                    minuteOfDay = null,
                    offsetMinutesBefore = 45
                )
            ),
            createdAt = now,
            updatedAt = now
        )
        coEvery { taskScheduleDao.upsertSchedule(any()) } returns Unit
        coEvery { taskScheduleDao.replaceReminderRules(any(), any()) } returns Unit

        repository.saveTaskSchedule(schedule)

        coVerify {
            taskScheduleDao.upsertSchedule(
                match {
                    it.id == "schedule-1" &&
                        it.taskId == "task-1" &&
                        it.recurrenceType == "WEEKLY" &&
                        it.intervalCount == 1 &&
                        it.weekdaysCsv == "MONDAY,THURSDAY" &&
                        it.occurrenceMinuteOfDay == 8 * 60 + 30 &&
                        it.nextOccurrenceDate == LocalDate.of(2026, 5, 28)
                }
            )
        }
        coVerify {
            taskScheduleDao.replaceReminderRules(
                "schedule-1",
                match { rules ->
                    rules.size == 2 &&
                        rules[0].trigger == "AT_TIME" &&
                        rules[1].offsetMinutesBefore == 45
                }
            )
        }
    }
}
