package com.chronosflow.feature.tasks

import app.cash.turbine.test
import com.chronosflow.core.ai.TaskAssistPlanner
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.ai.genai.refreshAssistUiSnapshot
import com.chronosflow.core.ai.TaskAssistRequest
import com.chronosflow.core.ai.TaskAssistSchedulePayload
import com.chronosflow.core.ai.TaskAssistSource
import com.chronosflow.core.ai.TaskAssistSuggestion
import com.chronosflow.core.domain.model.AlarmDeliveryState
import com.chronosflow.core.domain.model.AlarmReliability
import com.chronosflow.core.domain.model.AlarmRequest
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.ContactMethodKind
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskChecklistItem
import com.chronosflow.core.domain.model.TaskContactMethod
import com.chronosflow.core.domain.model.TaskContactSnapshot
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.GoalRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.usecase.AddTaskUseCase
import com.chronosflow.core.domain.usecase.GetTasksUseCase
import com.chronosflow.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import com.chronosflow.core.domain.usecase.ScheduleTaskIntoDayUseCase
import com.chronosflow.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import com.chronosflow.core.notifications.AlarmCapabilityRefresher
import com.chronosflow.core.notifications.AlarmScheduleResult
import com.chronosflow.core.notifications.AlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    private val getTasksUseCase: GetTasksUseCase = mockk()
    private val addTaskUseCase: AddTaskUseCase = mockk()
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mockk()
    private val scheduleTaskIntoDayUseCase: ScheduleTaskIntoDayUseCase = mockk()
    private val taskRepository: TaskRepository = mockk()
    private val goalRepository: GoalRepository = mockk(relaxed = true)
    private val taskScheduleRepository: TaskScheduleRepository = mockk(relaxed = true)
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val resolveNextTaskOccurrenceUseCase: ResolveNextTaskOccurrenceUseCase = mockk(relaxed = true)
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase = mockk(relaxed = true)
    private val taskAssistPlanner: TaskAssistPlanner = mockk()
    private val genAiAssistCoordinator: GenAiAssistCoordinator = mockk()
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher = mockk()
    private lateinit var viewModel: TaskViewModel

    private val testDispatcher = UnconfinedTestDispatcher()
    private val alarmRequests = MutableStateFlow<List<AlarmRequest>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getTasksUseCase() } returns flowOf(emptyList())
        every {
            alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK)
        } returns alarmRequests
        every { taskScheduleRepository.observeTaskSchedule(any()) } returns flowOf(null)
        coEvery { taskScheduleRepository.getTaskSchedule(any()) } returns null
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        every { alarmScheduler.canPostReminders() } returns true
        every { alarmScheduler.canScheduleExactAlarms() } returns true
        every { alarmCapabilityRefresher.refreshes } returns MutableSharedFlow(extraBufferCapacity = 1)
        every { goalRepository.observeGoals() } returns flowOf(emptyList())
        viewModel = TaskViewModel(
            getTasksUseCase,
            addTaskUseCase,
            toggleTaskCompletionUseCase,
            scheduleTaskIntoDayUseCase,
            taskRepository,
            goalRepository,
            taskScheduleRepository,
            alarmRequestRepository,
            sleepScheduleRepository,
            alarmScheduler,
            resolveNextTaskOccurrenceUseCase,
            syncRecurringTaskAlarmsUseCase,
            taskAssistPlanner,
            genAiAssistCoordinator,
            alarmCapabilityRefresher
        )
        coEvery { genAiAssistCoordinator.refreshAssistUiSnapshot() } returns GenAiAssistUiSnapshot(
            bannerTitle = "Gemini Nano ready",
            bannerMessage = "On-device assist",
            privacyModeLabel = "Gemini Nano (on-device)",
            aiDisabled = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tasks state reflects use case flow`() = runTest {
        val tasks = listOf(
            Task("1", "Task 1", null, false, 0, null, Instant.now(), Instant.now())
        )
        every { getTasksUseCase() } returns flowOf(tasks)

        val vm = TaskViewModel(
            getTasksUseCase,
            addTaskUseCase,
            toggleTaskCompletionUseCase,
            scheduleTaskIntoDayUseCase,
            taskRepository,
            goalRepository,
            taskScheduleRepository,
            alarmRequestRepository,
            sleepScheduleRepository,
            alarmScheduler,
            resolveNextTaskOccurrenceUseCase,
            syncRecurringTaskAlarmsUseCase,
            taskAssistPlanner,
            genAiAssistCoordinator,
            alarmCapabilityRefresher
        )

        vm.tasks.test {
            assertEquals(tasks, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addTask calls use case`() = runTest {
        val created = Task("1", "New Task", "Desc", false, 0, null, Instant.now(), Instant.now())
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask("New Task", "Desc")

        coVerify { addTaskUseCase("New Task", "Desc", 0, null, null, null, null, emptyList(), null, emptyList(), emptyList()) }
    }

    @Test
    fun `addTask saves recurring schedule when recurrence is enabled`() = runTest {
        val created = Task("1", "Recurring task", null, false, 0, null, Instant.now(), Instant.now())
        val recurringConfig = TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.WEEKLY,
            interval = 1,
            weekdays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY),
            startsOn = LocalDate.of(2026, 5, 25),
            reminderDrafts = listOf(
                TaskReminderDraft(id = "r1", trigger = com.chronosflow.core.domain.model.TaskReminderTrigger.BEFORE_OCCURRENCE, offsetMinutesBefore = 30)
            )
        )
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created
        every { resolveNextTaskOccurrenceUseCase(any(), any()) } returns LocalDate.of(2026, 5, 26)
        coEvery { taskScheduleRepository.saveTaskSchedule(any()) } returns Unit
        coEvery { syncRecurringTaskAlarmsUseCase(any(), any(), any()) } returns Unit

        viewModel.addTask(
            title = "Recurring task",
            preferredStartMinuteOfDay = 9 * 60,
            recurringConfig = recurringConfig
        )

        coVerify {
            taskScheduleRepository.saveTaskSchedule(
                match {
                    it.taskId == "1" &&
                        it.nextOccurrenceDate == LocalDate.of(2026, 5, 26) &&
                        it.reminderRules.size == 1
                }
            )
        }
        coVerify { syncRecurringTaskAlarmsUseCase(created, any(), any()) }
    }

    @Test
    fun `addTask forwards checklist and schedule preferences`() = runTest {
        val created = Task("1", "Launch prep", "Desc", false, 2, null, Instant.now(), Instant.now())
        val checklist = listOf(
            TaskChecklistItem(id = "item-1", label = "Outline", isCompleted = false)
        )
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask(
            title = "Launch prep",
            description = "Desc",
            priority = 2,
            dueDate = null,
            preferredDurationMinutes = 60,
            preferredStartMinuteOfDay = 15 * 60,
            targetDate = java.time.LocalDate.of(2026, 5, 30),
            checklist = checklist
        )

        coVerify {
            addTaskUseCase(
                "Launch prep",
                "Desc",
                2,
                null,
                60,
                15 * 60,
                java.time.LocalDate.of(2026, 5, 30),
                checklist,
                null,
                emptyList(),
                emptyList()
            )
        }
    }

    @Test
    fun `addTask forwards linked contact and actions`() = runTest {
        val linkedContact = TaskContactSnapshot(
            displayName = "Alex Johnson",
            lookupKey = "lookup-1",
            methods = listOf(
                TaskContactMethod(
                    id = "method-1",
                    kind = ContactMethodKind.PHONE,
                    label = "mobile",
                    value = "+15551234567",
                    normalizedValue = "+15551234567",
                    isPrimary = true
                )
            )
        )
        val actions = listOf(
            TaskAction(
                id = "action-1",
                type = TaskActionType.WEBSITE,
                label = "Brief",
                value = "https://example.com/brief",
                isPrimary = true
            )
        )
        val created = Task(
            id = "1",
            title = "Follow up",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            linkedContact = linkedContact,
            actions = actions
        )
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask(
            title = "Follow up",
            linkedContact = linkedContact,
            actions = actions
        )

        coVerify {
            addTaskUseCase(
                "Follow up",
                null,
                0,
                null,
                null,
                null,
                null,
                emptyList(),
                linkedContact,
                actions,
                emptyList()
            )
        }
    }

    @Test
    fun `addTask forwards attachments`() = runTest {
        val attachments = listOf(
            TaskAttachment(
                id = "attachment-1",
                displayName = "brief.pdf",
                mimeType = "application/pdf",
                sizeBytes = 2_048,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                reference = "content://docs/brief.pdf",
                persistedUriPermission = true
            )
        )
        val created = Task(
            id = "1",
            title = "Follow up",
            description = null,
            isCompleted = false,
            priority = 0,
            dueDate = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            attachments = attachments
        )
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask(
            title = "Follow up",
            attachments = attachments
        )

        coVerify {
            addTaskUseCase(
                "Follow up",
                null,
                0,
                null,
                null,
                null,
                null,
                emptyList(),
                null,
                emptyList(),
                attachments
            )
        }
    }

    @Test
    fun `toggleTask calls use case`() = runTest {
        coEvery { toggleTaskCompletionUseCase(any()) } returns Unit
        coEvery { taskRepository.getTaskById("1") } returnsMany listOf(
            Task("1", "Task 1", null, false, 0, null, Instant.now(), Instant.now()),
            Task("1", "Task 1", null, true, 0, null, Instant.now(), Instant.now())
        )

        viewModel.toggleTask("1")

        coVerify { toggleTaskCompletionUseCase("1") }
        coVerify { alarmScheduler.cancelAlarm("task:1") }
    }

    @Test
    fun `updateTask persists edited task`() = runTest {
        val task = Task("1", "Task 1", "Old", false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.saveTask(any()) } returns Unit

        viewModel.updateTask(task, "Edited", "New desc", 2)

        coVerify {
            taskRepository.saveTask(
                match {
                    it.id == "1" &&
                        it.title == "Edited" &&
                        it.description == "New desc" &&
                        it.priority == 2
                }
            )
        }
    }

    @Test
    fun `updateTask deletes recurring schedule when recurrence is disabled`() = runTest {
        val task = Task("1", "Task 1", "Old", false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.saveTask(any()) } returns Unit
        coEvery { taskScheduleRepository.getTaskSchedule("1") } returns com.chronosflow.core.domain.model.TaskSchedule(
            id = "schedule-1",
            taskId = "1",
            recurrenceRule = com.chronosflow.core.domain.model.TaskRecurrenceRule.Daily(
                intervalDays = 1,
                startsOn = LocalDate.of(2026, 5, 25)
            ),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        coEvery { taskScheduleRepository.deleteTaskSchedule("1") } returns Unit

        viewModel.updateTask(
            task = task,
            title = "Edited",
            description = "New desc",
            priority = 2,
            recurringConfig = TaskRecurringConfig(enabled = false)
        )

        coVerify { taskScheduleRepository.deleteTaskSchedule("1") }
    }

    @Test
    fun `deleteTask deletes repository task`() = runTest {
        val task = Task("1", "Task 1", null, false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.deleteTask(task) } returns Unit

        viewModel.deleteTask(task)

        coVerify { taskRepository.deleteTask(task) }
        coVerify { alarmScheduler.cancelAlarm("task:1") }
    }

    @Test
    fun `scheduleTaskToday calls scheduler and exposes status`() = runTest {
        coEvery {
            scheduleTaskIntoDayUseCase("1", null, null, null, any(), any())
        } returns PlannerOperationResult.Applied(
            message = "Placement valid",
            blockId = "block-1",
            snappedToMinute = 540
        )

        viewModel.scheduleTaskToday("1")

        assertEquals("Placement valid", viewModel.scheduleStatus.value)
        coVerify { scheduleTaskIntoDayUseCase("1", null, null, null, any(), any()) }
    }

    @Test
    fun `urgent alarm flow exposes fallback state by task id`() = runTest {
        viewModel.urgentTaskAlarmStates.test {
            assertEquals(emptyMap<String, TaskAlarmUiState>(), awaitItem())

            alarmRequests.value = listOf(
                AlarmRequest(
                    id = "task:1",
                    type = AlarmRequestType.URGENT_TASK,
                    scheduledFor = Instant.parse("2026-05-30T18:00:00Z"),
                    title = "Launch prep",
                    message = "Due now",
                    medicationPlanId = null,
                    blockId = "1",
                    reliability = AlarmReliability.DEGRADED_WINDOW,
                    deliveryState = AlarmDeliveryState.DEGRADED,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            assertEquals(
                TaskAlarmStatus.DEGRADED_WINDOW,
                awaitItem()["1"]?.status
            )
        }
    }

    @Test
    fun `urgent alarms are skipped during sleep hours`() = runTest {
        val dueDate = Instant.parse("2026-05-26T12:00:00Z")
        val created = Task("1", "Sleepy task", null, false, 2, dueDate, Instant.now(), Instant.now())
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule(
            enabled = true,
            startMinute = 0,
            endMinute = 1439
        )
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask(
            title = "Sleepy task",
            priority = 2,
            dueDate = dueDate,
            alarmEnabled = true
        )

        coVerify {
            alarmRequestRepository.saveAlarmRequest(
                match {
                    it.id == "task:1" &&
                        it.deliveryState == AlarmDeliveryState.FAILED &&
                        it.failureReason == "Alarm falls inside the sleep schedule"
                }
            )
        }
        verify(exactly = 0) { alarmScheduler.scheduleAlarmRequest(any()) }
        assertEquals("Alarm skipped during sleep hours", viewModel.status.value)
    }

    @Test
    fun `toggleTask reschedules urgent alarm when task is reopened`() = runTest {
        val dueDate = Instant.now().plusSeconds(3600)
        coEvery { toggleTaskCompletionUseCase("1") } returns Unit
        coEvery { taskRepository.getTaskById("1") } returnsMany listOf(
            Task("1", "Task 1", null, true, 2, dueDate, Instant.now(), Instant.now()),
            Task("1", "Task 1", null, false, 2, dueDate, Instant.now(), Instant.now())
        )
        every { sleepScheduleRepository.getSleepSchedule() } returns SleepSchedule.default()
        every { alarmScheduler.scheduleAlarmRequest(any()) } returns AlarmScheduleResult.Scheduled("task:1", exact = true)
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit

        viewModel.toggleTask("1")

        verify { alarmScheduler.scheduleAlarmRequest(match { it.id == "task:1" && it.blockId == "1" }) }
    }

    @Test
    fun `duplicateTask regenerates action contact method and attachment ids`() = runTest {
        val original = Task(
            id = "1",
            title = "Task 1",
            description = null,
            isCompleted = false,
            priority = 1,
            dueDate = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            linkedContact = TaskContactSnapshot(
                displayName = "Alex",
                lookupKey = "lookup-1",
                methods = listOf(
                    TaskContactMethod(
                        id = "method-1",
                        kind = ContactMethodKind.PHONE,
                        label = "mobile",
                        value = "+15551234567",
                        normalizedValue = "+15551234567",
                        isPrimary = true
                    )
                )
            ),
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Docs",
                    value = "https://example.com",
                    isPrimary = true
                )
            ),
            attachments = listOf(
                TaskAttachment(
                    id = "attachment-1",
                    displayName = "mockup.png",
                    mimeType = "image/png",
                    sizeBytes = 4_096,
                    kind = TaskAttachmentKind.IMAGE,
                    storageMode = TaskAttachmentStorageMode.IMPORTED,
                    reference = "task_attachments/mockup.png",
                    isFeaturedImage = true
                )
            )
        )
        coEvery { taskRepository.saveTask(any()) } returns Unit

        viewModel.duplicateTask(original)

        coVerify {
            taskRepository.saveTask(
                match {
                    it.id != original.id &&
                        it.actions.single().id != original.actions.single().id &&
                        it.linkedContact?.methods?.single()?.id != original.linkedContact?.methods?.single()?.id &&
                        it.attachments.single().id != original.attachments.single().id
                }
            )
        }
    }

    @Test
    fun `requestTaskAssist exposes manual suggestions`() = runTest {
        val request = TaskAssistRequest(title = "Email launch team")
        val suggestions = listOf(
            TaskAssistSuggestion.Schedule(
                id = "s1",
                label = "30m block",
                reason = "Fits a quick follow-up",
                source = TaskAssistSource.LOCAL,
                payload = TaskAssistSchedulePayload(preferredDurationMinutes = 30)
            )
        )
        coEvery { taskAssistPlanner.suggest(request) } returns suggestions

        viewModel.requestTaskAssist(request)

        assertEquals(false, viewModel.assistState.value.isLoading)
        assertEquals(suggestions, viewModel.assistState.value.suggestions)
    }
}
