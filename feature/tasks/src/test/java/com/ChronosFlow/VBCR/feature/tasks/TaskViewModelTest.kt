package com.ChronosFlow.VBCR.feature.tasks

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.ai.TaskAssistPlanner
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.genai.RewriteAssistUiState
import com.ChronosFlow.VBCR.core.ai.genai.RewriteStyle
import com.ChronosFlow.VBCR.core.ai.genai.refreshAssistUiSnapshot
import com.ChronosFlow.VBCR.core.ai.TaskAssistRequest
import com.ChronosFlow.VBCR.core.ai.TaskAssistSchedulePayload
import com.ChronosFlow.VBCR.core.ai.TaskAssistSource
import com.ChronosFlow.VBCR.core.ai.TaskAssistSuggestion
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskChecklistItem
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskScheduleRepository
import com.ChronosFlow.VBCR.core.domain.usecase.AddTaskUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.GetTasksUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ScheduleTaskIntoDayUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ToggleTaskCompletionUseCase
import com.ChronosFlow.VBCR.core.notifications.AlarmCapabilityRefresher
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduleResult
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
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
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `tasks state reflects use case flow`() = runTest(testDispatcher) {
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
    fun `isListLoading clears after first tasks emission`() = runTest(testDispatcher) {
        viewModel.isListLoading.test {
            assertEquals(false, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addTask calls use case`() = runTest(testDispatcher) {
        val created = Task("1", "New Task", "Desc", false, 0, null, Instant.now(), Instant.now())
        coEvery { addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns created

        viewModel.addTask("New Task", "Desc")

        coVerify { addTaskUseCase("New Task", "Desc", 0, null, null, null, null, emptyList(), null, emptyList(), emptyList()) }
    }

    @Test
    fun `addTask saves recurring schedule when recurrence is enabled`() = runTest(testDispatcher) {
        val created = Task("1", "Recurring task", null, false, 0, null, Instant.now(), Instant.now())
        val recurringConfig = TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.WEEKLY,
            interval = 1,
            weekdays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY),
            startsOn = LocalDate.of(2026, 5, 25),
            reminderDrafts = listOf(
                TaskReminderDraft(id = "r1", trigger = com.ChronosFlow.VBCR.core.domain.model.TaskReminderTrigger.BEFORE_OCCURRENCE, offsetMinutesBefore = 30)
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
    fun `addTask forwards checklist and schedule preferences`() = runTest(testDispatcher) {
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
    fun `addTask forwards linked contact and actions`() = runTest(testDispatcher) {
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
    fun `addTask forwards attachments`() = runTest(testDispatcher) {
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
    fun `toggleTask calls use case`() = runTest(testDispatcher) {
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
    fun `updateTask persists edited task`() = runTest(testDispatcher) {
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
    fun `updateTask deletes recurring schedule when recurrence is disabled`() = runTest(testDispatcher) {
        val task = Task("1", "Task 1", "Old", false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.saveTask(any()) } returns Unit
        coEvery { taskScheduleRepository.getTaskSchedule("1") } returns com.ChronosFlow.VBCR.core.domain.model.TaskSchedule(
            id = "schedule-1",
            taskId = "1",
            recurrenceRule = com.ChronosFlow.VBCR.core.domain.model.TaskRecurrenceRule.Daily(
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
    fun `deleteTask deletes repository task`() = runTest(testDispatcher) {
        val task = Task("1", "Task 1", null, false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.deleteTask(task) } returns Unit

        viewModel.deleteTask(task)

        coVerify { taskRepository.deleteTask(task) }
        coVerify { alarmScheduler.cancelAlarm("task:1") }
    }

    @Test
    fun `restoreDeletedTask reinserts repository task`() = runTest(testDispatcher) {
        val task = Task("1", "Task 1", null, false, 0, null, Instant.now(), Instant.now())
        coEvery { taskRepository.saveTask(task) } returns Unit

        viewModel.restoreDeletedTask(task)

        coVerify { taskRepository.saveTask(task) }
    }

    @Test
    fun `scheduleTaskToday calls scheduler and exposes status`() = runTest(testDispatcher) {
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
    fun `urgent alarm flow exposes fallback state by task id`() = runTest(testDispatcher) {
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
    fun `urgent alarms are skipped during sleep hours`() = runTest(testDispatcher) {
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
    fun `toggleTask reschedules urgent alarm when task is reopened`() = runTest(testDispatcher) {
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
    fun `duplicateTask regenerates action contact method and attachment ids`() = runTest(testDispatcher) {
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
    fun `requestTaskAssist exposes manual suggestions`() = runTest(testDispatcher) {
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

    @Test
    fun `rewriteTaskDescription publishes a preview the form applies explicitly`() = runTest(testDispatcher) {
        coEvery {
            taskAssistPlanner.rewriteText("Email the launch team about the rollout plan", RewriteStyle.SHORTEN)
        } returns "Email launch team re: rollout plan"

        viewModel.rewriteTaskDescription(
            text = "Email the launch team about the rollout plan",
            style = RewriteStyle.SHORTEN,
            styleLabel = "Shorten"
        )

        val state = viewModel.rewriteState.value
        assertEquals(false, state.isLoading)
        assertEquals("Shorten", state.styleLabel)
        assertEquals("Email the launch team about the rollout plan", state.original)
        assertEquals("Email launch team re: rollout plan", state.rewritten)
    }

    @Test
    fun `rewriteTaskDescription reports when the rewrite tool is unavailable`() = runTest(testDispatcher) {
        coEvery { taskAssistPlanner.rewriteText(any(), any()) } returns null

        viewModel.rewriteTaskDescription(
            text = "Email the launch team about the rollout plan",
            style = RewriteStyle.PROFESSIONAL,
            styleLabel = "Polish"
        )

        val state = viewModel.rewriteState.value
        assertEquals(null, state.rewritten)
        assertEquals(
            "Rewrite is unavailable on this device right now — your wording is unchanged.",
            state.message
        )

        viewModel.clearTaskRewrite()

        assertEquals(RewriteAssistUiState(), viewModel.rewriteState.value)
    }
}
