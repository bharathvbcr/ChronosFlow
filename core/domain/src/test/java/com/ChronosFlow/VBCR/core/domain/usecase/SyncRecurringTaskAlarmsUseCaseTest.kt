package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderRule
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderTrigger
import com.ChronosFlow.VBCR.core.domain.model.TaskSchedule
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SyncRecurringTaskAlarmsUseCaseTest {

    private val alarmRequestRepository: AlarmRequestRepository = mockk()
    private lateinit var useCase: SyncRecurringTaskAlarmsUseCase
    private val resolver = ResolveNextTaskOccurrenceUseCase()
    private val now = Instant.parse("2026-05-25T12:00:00Z")

    @Before
    fun setup() {
        useCase = SyncRecurringTaskAlarmsUseCase(alarmRequestRepository, resolver)
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK) } returns flowOf(emptyList())
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
    }

    @Test
    fun `creates one alarm request per recurring reminder rule`() = runTest {
        val task = task()
        val schedule = schedule(
            nextOccurrenceDate = LocalDate.of(2026, 5, 26),
            occurrenceMinuteOfDay = 9 * 60,
            reminderRules = listOf(
                TaskReminderRule(
                    id = "r1",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 9 * 60
                ),
                TaskReminderRule(
                    id = "r2",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.BEFORE_OCCURRENCE,
                    offsetMinutesBefore = 30
                )
            )
        )

        useCase(task, schedule, now = now)

        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match { it.id == "task:task-1:2026-05-26:r1" && it.blockId == "task-1" }
            )
        }
        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match { it.id == "task:task-1:2026-05-26:r2" && it.blockId == "task-1" }
            )
        }
    }

    @Test
    fun `creates bounded future exact alarm requests through generated date`() = runTest {
        val task = task()
        val schedule = schedule(
            nextOccurrenceDate = LocalDate.of(2026, 5, 26),
            occurrenceMinuteOfDay = 9 * 60,
            generatedThroughDate = LocalDate.of(2026, 5, 28),
            reminderRules = listOf(
                TaskReminderRule(
                    id = "r1",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 9 * 60
                )
            )
        )

        useCase(task, schedule, now = now)

        coVerify(exactly = 3) { alarmRequestRepository.saveAlarmRequest(any()) }
        coVerify {
            alarmRequestRepository.saveAlarmRequest(match { it.id == "task:task-1:2026-05-26:r1" })
            alarmRequestRepository.saveAlarmRequest(match { it.id == "task:task-1:2026-05-27:r1" })
            alarmRequestRepository.saveAlarmRequest(match { it.id == "task:task-1:2026-05-28:r1" })
        }
        coVerify(exactly = 0) {
            alarmRequestRepository.saveAlarmRequest(match { it.id == "task:task-1:2026-05-29:r1" })
        }
    }

    @Test
    fun `cancels stale recurring alarm requests that are no longer desired`() = runTest {
        val existing = AlarmRequest(
            id = "task:task-1:2026-05-25:old",
            type = AlarmRequestType.URGENT_TASK,
            scheduledFor = now.plusSeconds(3600),
            title = "Write proposal",
            message = "Due soon",
            medicationPlanId = null,
            blockId = "task-1",
            reliability = AlarmReliability.EXACT,
            deliveryState = AlarmDeliveryState.SCHEDULED,
            createdAt = now,
            updatedAt = now
        )
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK) } returns flowOf(listOf(existing))
        val task = task()
        val schedule = schedule(
            nextOccurrenceDate = LocalDate.of(2026, 5, 26),
            occurrenceMinuteOfDay = 9 * 60,
            reminderRules = listOf(
                TaskReminderRule(
                    id = "r1",
                    taskScheduleId = "schedule-1",
                    trigger = TaskReminderTrigger.AT_TIME,
                    minuteOfDay = 9 * 60
                )
            )
        )

        useCase(task, schedule, now = now)

        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match { it.id == existing.id && it.deliveryState == AlarmDeliveryState.CANCELLED }
            )
        }
    }

    private fun task(): Task = Task(
        id = "task-1",
        title = "Write proposal",
        description = "Draft the proposal",
        isCompleted = false,
        priority = 1,
        dueDate = null,
        createdAt = now,
        updatedAt = now
    )

    private fun schedule(
        nextOccurrenceDate: LocalDate,
        occurrenceMinuteOfDay: Int,
        generatedThroughDate: LocalDate? = nextOccurrenceDate,
        reminderRules: List<TaskReminderRule>
    ): TaskSchedule = TaskSchedule(
        id = "schedule-1",
        taskId = "task-1",
        recurrenceRule = TaskRecurrenceRule.Daily(intervalDays = 1, startsOn = LocalDate.of(2026, 5, 25)),
        occurrenceMinuteOfDay = occurrenceMinuteOfDay,
        nextOccurrenceDate = nextOccurrenceDate,
        generatedThroughDate = generatedThroughDate,
        reminderRules = reminderRules,
        createdAt = now,
        updatedAt = now
    )
}
