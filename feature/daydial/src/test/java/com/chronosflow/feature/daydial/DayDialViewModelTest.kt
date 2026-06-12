package com.chronosflow.feature.daydial

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.chronosflow.core.ai.ChronosAIPlanner
import com.chronosflow.core.ai.EnergyCorrelationEngine
import com.chronosflow.core.ai.DeepWorkAssistPlanner
import com.chronosflow.core.ai.InsightsRecommendationsPlanner
import com.chronosflow.core.ai.MoodEnergyCheckInAssistPlanner
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.data.backup.ChronosDataExportFile
import com.chronosflow.core.data.backup.ChronosDataExportRepository
import com.chronosflow.core.data.dao.FocusSessionDao
import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.data.privacy.AssistantPreferences
import com.chronosflow.core.domain.model.AlarmRequestType
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.domain.repository.AlarmRequestRepository
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TaskScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.ApplyAiPlanUseCase
import com.chronosflow.core.domain.usecase.CompleteDailyReviewUseCase
import com.chronosflow.core.domain.usecase.CompleteHabitUseCase
import com.chronosflow.core.domain.usecase.CompleteTaskOccurrenceUseCase
import com.chronosflow.core.domain.usecase.LogActualTimeUseCase
import com.chronosflow.core.domain.usecase.MoveBlockUseCase
import com.chronosflow.core.domain.usecase.ResizeBlockUseCase
import com.chronosflow.core.domain.usecase.SyncRecurringTaskAlarmsUseCase
import com.chronosflow.core.domain.usecase.ToggleTaskCompletionUseCase
import com.chronosflow.core.notifications.AlarmCapabilityRefresher
import com.chronosflow.core.notifications.AlarmScheduler
import com.chronosflow.core.notifications.HabitReminderScheduler
import com.chronosflow.feature.daydial.delegate.AppLockSettingsState
import com.chronosflow.feature.daydial.delegate.DayDialAiDelegate
import com.chronosflow.feature.daydial.delegate.DayDialAppLockDelegate
import com.chronosflow.feature.daydial.delegate.DayDialBlockDelegate
import com.chronosflow.feature.daydial.delegate.DayDialFocusDelegate
import com.chronosflow.feature.daydial.delegate.DayDialMoodEnergyDelegate
import com.chronosflow.feature.daydial.delegate.DayDialReminderDelegate
import com.chronosflow.feature.daydial.delegate.DayDialReviewDelegate
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialViewModelTest {

    private val repository: TimeBlockRepository = mockk()
    private val calendarEventRepository: CalendarEventRepository = mockk(relaxed = true)
    private val aiPlanner: ChronosAIPlanner = mockk()
    private val alarmScheduler: AlarmScheduler = mockk(relaxed = true)
    private val alarmRequestRepository: AlarmRequestRepository = mockk(relaxed = true)
    private val reviewRepository: ReviewRepository = mockk(relaxed = true)
    private val moodEnergyRepository: MoodEnergyRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val sleepScheduleRepository: SleepScheduleRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val taskScheduleRepository: TaskScheduleRepository = mockk(relaxed = true)
    private val moveBlockUseCase: MoveBlockUseCase = mockk()
    private val resizeBlockUseCase: ResizeBlockUseCase = mockk()
    private val applyAiPlanUseCase: ApplyAiPlanUseCase = mockk()
    private val assistantPreferences: AssistantPreferences = mockk(relaxed = true)
    private val completeDailyReviewUseCase: CompleteDailyReviewUseCase = mockk(relaxed = true)
    private val completeHabitUseCase: CompleteHabitUseCase = mockk(relaxed = true)
    private val completeTaskOccurrenceUseCase: CompleteTaskOccurrenceUseCase = mockk(relaxed = true)
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase = mockk(relaxed = true)
    private val logActualTimeUseCase: LogActualTimeUseCase = mockk()
    private val syncRecurringTaskAlarmsUseCase: SyncRecurringTaskAlarmsUseCase = mockk(relaxed = true)
    private val appLockDelegate: DayDialAppLockDelegate = mockk(relaxed = true)
    private val focusSessionDao: FocusSessionDao = mockk(relaxed = true)
    private val alarmCapabilityRefresher: AlarmCapabilityRefresher = mockk()
    private val habitReminderScheduler: HabitReminderScheduler = mockk(relaxed = true)
    private val reminderPreferencesReader: DayDialReminderPreferencesReader = mockk()
    private val dataExportRepository: ChronosDataExportRepository = mockk()
    private lateinit var viewModel: DayDialViewModel

    private val testDispatcher = UnconfinedTestDispatcher()
    private val timeBlocksFlow = MutableStateFlow<List<TimeBlock>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        timeBlocksFlow.value = emptyList()
        every { repository.getTimeBlocksByDate(any()) } returns timeBlocksFlow
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.MEDICATION) } returns flowOf(emptyList())
        every { reviewRepository.observeActualTimeSegments(any()) } returns flowOf(emptyList())
        every { reviewRepository.observeDailyReview(any()) } returns flowOf(null)
        every { moodEnergyRepository.observeForDate(any()) } returns flowOf(emptyList())
        every { habitRepository.observeHabits() } returns flowOf(emptyList())
        every { medicationRepository.observeMedicationPlans() } returns flowOf(emptyList())
        every { taskRepository.getAllTasks() } returns flowOf(emptyList())
        every { taskScheduleRepository.observeTaskSchedule(any()) } returns flowOf(null)
        every { sleepScheduleRepository.getSleepSchedule() } returns com.chronosflow.core.domain.model.SleepSchedule.default()
        coEvery { moodEnergyRepository.getForDateRange(any(), any()) } returns emptyList()
        coEvery { logActualTimeUseCase(any()) } returns Unit
        coEvery { alarmRequestRepository.saveAlarmRequest(any()) } returns Unit
        every { aiPlanner.genAiRuntimeStatus } returns MutableStateFlow(GenAiRuntimeStatus())
        coEvery { aiPlanner.refreshGenAiStatus() } returns GenAiRuntimeStatus()
        every { assistantPreferences.assistantPrivacyModeValue() } returns "ON_DEVICE_ONLY"
        every { assistantPreferences.preferPreviewNanoModel() } returns false
        every { appLockDelegate.settings } returns MutableStateFlow(AppLockSettingsState())
        every { appLockDelegate.sensitiveSession } returns MutableStateFlow(0)
        every { appLockDelegate.requiresSensitiveAuth(any()) } returns false
        coEvery { repository.saveTimeBlock(any()) } returns Unit
        every { focusSessionDao.observeRecoverableSession() } returns flowOf(null)
        every { alarmCapabilityRefresher.refreshes } returns MutableSharedFlow(extraBufferCapacity = 1)
        every { reminderPreferencesReader.read() } returns DayDialReminderSettings.DEFAULT
        val manualMissedBlockRegistry = testManualMissedBlockRegistry()
        viewModel = DayDialViewModel(
            repository = repository,
            taskRepository = taskRepository,
            taskScheduleRepository = taskScheduleRepository,
            habitRepository = habitRepository,
            medicationRepository = medicationRepository,
            toggleTaskCompletionUseCase = toggleTaskCompletionUseCase,
            completeHabitUseCase = completeHabitUseCase,
            calendarEventRepository = calendarEventRepository,
            focusSessionDao = focusSessionDao,
            focusDelegate = DayDialFocusDelegate(repository, logActualTimeUseCase, manualMissedBlockRegistry),
            blockDelegate = DayDialBlockDelegate(
                repository,
                sleepScheduleRepository,
                moveBlockUseCase,
                resizeBlockUseCase,
                calendarEventRepository
            ),
            aiDelegate = DayDialAiDelegate(
                repository,
                sleepScheduleRepository,
                aiPlanner,
                mockk(relaxed = true),
                mockk(relaxed = true),
                applyAiPlanUseCase,
                assistantPreferences
            ),
            reminderDelegate = DayDialReminderDelegate(repository, alarmScheduler, alarmRequestRepository, habitRepository),
            reviewDelegate = DayDialReviewDelegate(
                repository,
                reviewRepository,
                moodEnergyRepository,
                habitRepository,
                medicationRepository,
                taskRepository,
                taskScheduleRepository,
                completeDailyReviewUseCase,
                completeTaskOccurrenceUseCase,
                logActualTimeUseCase,
                syncRecurringTaskAlarmsUseCase,
                alarmScheduler,
                alarmRequestRepository,
                EnergyCorrelationEngine(mockk<GenAiAssistCoordinator>(relaxed = true)),
                InsightsRecommendationsPlanner(mockk<GenAiAssistCoordinator>(relaxed = true)),
                mockk(relaxed = true),
                mockk<GenAiAssistCoordinator>(relaxed = true),
                manualMissedBlockRegistry
            ),
            moodEnergyDelegate = DayDialMoodEnergyDelegate(moodEnergyRepository, mockk(relaxed = true)),
            journalDelegate = mockk(relaxed = true),
            trendsDelegate = mockk(relaxed = true),
            moodEnergyCheckInAssistPlanner = mockk<MoodEnergyCheckInAssistPlanner>(relaxed = true),
            focusNextBlockPlanner = mockk(relaxed = true),
            focusGuidancePlanner = mockk(relaxed = true),
            proactiveAssistCache = mockk(relaxed = true),
            appLockDelegate = appLockDelegate,
            alarmCapabilityRefresher = alarmCapabilityRefresher,
            habitReminderScheduler = habitReminderScheduler,
            reminderPreferencesReader = reminderPreferencesReader,
            manualMissedBlockRegistry = manualMissedBlockRegistry,
            focusMoodAccentCache = mockk(relaxed = true),
            dataExportRepository = dataExportRepository,
            routineRepository = mockk(relaxed = true),
            applyRoutineToDateUseCase = mockk(relaxed = true),
            completeRoutineForDateUseCase = mockk(relaxed = true)
        )
        viewModel.dataExportDispatcher = testDispatcher
        viewModel.cancelMinuteTickerForTest()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        assertEquals(LocalDate.now(), viewModel.selectedDate.value)
        assertEquals(null, viewModel.selectedBlockId.value)
    }

    @Test
    fun `createDataExport writes full data export and updates export state`() = runTest {
        val file = File.createTempFile("chronosflow-data-export", ".json")
        every { dataExportRepository.exportSnapshot() } returns ChronosDataExportFile(
            file = file,
            tableCount = 4,
            rowCount = 12,
            byteCount = 2048L,
            localStateFileCount = 3
        )
        var readyFile: File? = null

        viewModel.createDataExport { readyFile = it }
        advanceUntilIdle()

        assertEquals(file, readyFile)
        assertEquals(false, viewModel.dataExportState.value.isExporting)
        assertEquals(file.name, viewModel.dataExportState.value.lastFileName)
        assertEquals(
            "Exported 12 database rows from 4 tables plus 3 local settings files (2 KB)",
            viewModel.dataExportState.value.summary
        )
        file.delete()
    }

    @Test
    fun `selectDate updates selectedDate state`() = runTest {
        val newDate = LocalDate.now().plusDays(1)
        viewModel.selectDate(newDate)
        assertEquals(newDate, viewModel.selectedDate.value)
    }

    @Test
    fun `onBlockSelected updates selectedBlockId state`() = runTest {
        viewModel.onBlockSelected("block-1")
        assertEquals("block-1", viewModel.selectedBlockId.value)
    }

    @Test
    fun `onBlockSelected emits selected summary block in same scheduler turn`() = runTest {
        val block = timeBlock(
            id = "block-1",
            date = LocalDate.now(),
            startMinute = 9 * 60,
            durationMinutes = 30
        )
        timeBlocksFlow.value = listOf(block)

        viewModel.selectedBlock.test {
            assertEquals(null, awaitItem())

            viewModel.onBlockSelected("block-1")
            runCurrent()

            val selected = awaitItem()
            assertEquals("block-1", selected?.id)
            assertEquals("Deep work", selected?.title)
            assertTrue(selected?.isSelected == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timeBlocks emits scheduled task block from repository stream`() = runTest {
        val scheduledTaskBlock = timeBlock(
            id = "task-block-1",
            date = LocalDate.now(),
            startMinute = 13 * 60,
            durationMinutes = 45,
            taskId = "task-1"
        )

        viewModel.timeBlocks.test {
            assertEquals(emptyList<TimeBlockUiModel>(), awaitItem())

            timeBlocksFlow.value = listOf(scheduledTaskBlock)
            runCurrent()

            val blocks = awaitItem()
            assertEquals(1, blocks.size)
            assertEquals("task-block-1", blocks.single().id)
            assertEquals("task-1", blocks.single().taskId)
            assertEquals(13 * 60, blocks.single().startMinuteOfDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCompactModeToggled toggles compactMode state`() = runTest {
        val initial = viewModel.compactMode.value
        viewModel.onCompactModeToggled()
        assertEquals(!initial, viewModel.compactMode.value)
    }

    @Test
    fun `syncCalendar reports refreshed device calendar`() = runTest {
        val date = LocalDate.of(2026, 5, 25)
        viewModel.selectDate(date)
        coEvery { calendarEventRepository.syncFromDeviceCalendar(any(), any()) } returns Unit

        viewModel.syncCalendar()
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertFalse(state.isWorking)
        assertEquals("Device calendar refreshed for 2026-05-25", state.statusMessage)
        assertEquals("Device calendar refreshed for 2026-05-25", state.lastSuccessMessage)
    }

    @Test
    fun `syncCalendar reports failure when device calendar refresh fails`() = runTest {
        coEvery { calendarEventRepository.syncFromDeviceCalendar(any(), any()) } throws IllegalStateException("resolver unavailable")

        viewModel.syncCalendar()
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertFalse(state.isWorking)
        assertEquals("Calendar refresh failed: resolver unavailable", state.statusMessage)
    }

    @Test
    fun `endDayReview persists computed daily review`() = runTest {
        val date = LocalDate.now()
        val block = timeBlock(
            id = "block-1",
            date = date,
            startMinute = 9 * 60,
            durationMinutes = 30
        )
        every { repository.getTimeBlocksByDate(any()) } returns flowOf(listOf(block.copy(actualStartMinuteOfDay = 9 * 60, actualEndMinuteOfDay = 9 * 60 + 30)))
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        viewModel.endDayReview(markCompleted = listOf("block-1"))
        runCurrent()

        coVerify {
            completeDailyReviewUseCase(
                match<DailyReviewSummary> {
                    it.date == date &&
                        it.plannedMinutes == 30 &&
                        it.actualMinutes == 30 &&
                        it.completedBlockCount == 1 &&
                        it.missedBlockCount == 0
                }
            )
        }
    }

    @Test
    fun `exportBlockToCalendar links exported event id onto block`() = runTest {
        val block = timeBlock(
            id = "block-1",
            date = LocalDate.now(),
            startMinute = 9 * 60,
            durationMinutes = 30
        )
        val method = DayDialViewModel::class.java.methods.firstOrNull { it.name == "exportBlockToCalendar" }

        assertNotNull("DayDialViewModel should expose exportBlockToCalendar", method)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { calendarEventRepository.exportTimeBlock(block) } returns 88L
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        method!!.invoke(viewModel, "block-1")
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertEquals("Connected Deep work to device calendar", state.statusMessage)
        assertEquals("Connected Deep work to device calendar", state.lastSuccessMessage)
        coVerify {
            repository.saveTimeBlock(
                match {
                    it.id == "block-1" &&
                        it.calendarEventId == 88L
                }
            )
        }
    }

    @Test
    fun `refreshCalendarExport updates linked device event`() = runTest {
        val block = timeBlock(
            id = "block-1",
            date = LocalDate.now(),
            startMinute = 9 * 60,
            durationMinutes = 30
        ).copy(calendarEventId = 88L)
        val method = DayDialViewModel::class.java.methods.firstOrNull { it.name == "refreshCalendarExport" }

        assertNotNull("DayDialViewModel should expose refreshCalendarExport", method)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { calendarEventRepository.updateExportedTimeBlock(block) } returns true

        method!!.invoke(viewModel, "block-1")
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertEquals("Calendar export refreshed for Deep work", state.statusMessage)
        coVerify { calendarEventRepository.updateExportedTimeBlock(block) }
    }

    @Test
    fun `refreshCalendarExport reports failure when linked event update fails`() = runTest {
        val block = timeBlock(
            id = "block-1",
            date = LocalDate.now(),
            startMinute = 9 * 60,
            durationMinutes = 30
        ).copy(calendarEventId = 88L)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { calendarEventRepository.updateExportedTimeBlock(block) } returns false

        viewModel.refreshCalendarExport("block-1")
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertFalse(state.isWorking)
        assertEquals("Calendar export could not be refreshed for Deep work", state.statusMessage)
    }

    @Test
    fun `removeCalendarExport deletes linked event and clears block link`() = runTest {
        val block = timeBlock(
            id = "block-1",
            date = LocalDate.now(),
            startMinute = 9 * 60,
            durationMinutes = 30
        ).copy(calendarEventId = 88L)
        val method = DayDialViewModel::class.java.methods.firstOrNull { it.name == "removeCalendarExport" }

        assertNotNull("DayDialViewModel should expose removeCalendarExport", method)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { calendarEventRepository.deleteExportedTimeBlock(88L) } returns true
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        method!!.invoke(viewModel, "block-1")
        runCurrent()

        val state = viewModel.calendarConnectionState.value
        assertEquals("Calendar export removed for Deep work", state.statusMessage)
        coVerify { calendarEventRepository.deleteExportedTimeBlock(88L) }
        coVerify {
            repository.saveTimeBlock(
                match {
                    it.id == "block-1" &&
                        it.calendarEventId == null
                }
            )
        }
    }

    @Test
    fun `markBlockComplete advances recurring task occurrence`() = runTest {
        val occurrenceDate = LocalDate.of(2026, 5, 26)
        val block = timeBlock(
            id = "block-1",
            date = occurrenceDate,
            startMinute = 9 * 60,
            durationMinutes = 30,
            taskId = "task-1",
            taskOccurrenceDate = occurrenceDate
        )
        val task = Task(
            id = "task-1",
            title = "Write proposal",
            description = null,
            isCompleted = false,
            priority = 1,
            dueDate = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val existingSchedule = TaskSchedule(
            id = "schedule-1",
            taskId = "task-1",
            recurrenceRule = TaskRecurrenceRule.Daily(
                intervalDays = 1,
                startsOn = LocalDate.of(2026, 5, 25)
            ),
            nextOccurrenceDate = occurrenceDate,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val updatedSchedule = existingSchedule.copy(nextOccurrenceDate = occurrenceDate.plusDays(1))
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { taskRepository.getTaskById("task-1") } returns task
        coEvery { taskScheduleRepository.getTaskSchedule("task-1") } returns existingSchedule
        coEvery { completeTaskOccurrenceUseCase("task-1", occurrenceDate, any()) } returns updatedSchedule
        every { alarmRequestRepository.observeRequestsByType(AlarmRequestType.URGENT_TASK) } returns flowOf(emptyList())

        viewModel.markBlockComplete("block-1")
        runCurrent()

        coVerify { completeTaskOccurrenceUseCase("task-1", occurrenceDate, any()) }
        coVerify { syncRecurringTaskAlarmsUseCase(task, updatedSchedule, any()) }
    }

    @Test
    fun `completeDayQuickTask delegates to task completion use case`() = runTest {
        viewModel.completeDayQuickTask("task-1")
        runCurrent()

        coVerify { toggleTaskCompletionUseCase("task-1") }
    }

    @Test
    fun `completeDayQuickHabit delegates to habit completion for selected date`() = runTest {
        val date = LocalDate.of(2026, 5, 27)
        val habit = habit(id = "habit-1", title = "Stretch")
        coEvery { habitRepository.getHabitById("habit-1") } returns habit

        viewModel.selectDate(date)
        viewModel.completeDayQuickHabit("habit-1")
        runCurrent()

        coVerify { completeHabitUseCase(habit, date) }
    }

    @Test
    fun `markDayQuickMedicationTaken records dose and decrements supply`() = runTest {
        val date = LocalDate.of(2026, 5, 27)
        val plan = medicationPlan(id = "med-1", missedCount = 2, supplyRemaining = 4)
        coEvery { medicationRepository.getMedicationPlanById("med-1") } returns plan
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit
        coEvery { medicationRepository.saveMedicationPlan(any()) } returns Unit

        viewModel.selectDate(date)
        viewModel.markDayQuickMedicationTaken("med-1", 20 * 60)
        runCurrent()

        coVerify {
            medicationRepository.addMedicationDoseEvent(
                match {
                    it.medicationPlanId == "med-1" &&
                        it.type == MedicationDoseEventType.TAKEN &&
                        it.eventDate == date &&
                        it.scheduledMinuteOfDay == 20 * 60 &&
                        it.reason == null &&
                        it.doseAmount == "1"
                }
            )
        }
        coVerify {
            medicationRepository.saveMedicationPlan(
                match {
                    it.id == "med-1" &&
                        it.missedCount == 2 &&
                        it.safetyProfile?.supplyRemaining == 3
                }
            )
        }
    }

    @Test
    fun `markDayQuickMedicationMissed records dose and increments missed count`() = runTest {
        val date = LocalDate.of(2026, 5, 27)
        val plan = medicationPlan(id = "med-1", missedCount = 1, supplyRemaining = 4)
        coEvery { medicationRepository.getMedicationPlanById("med-1") } returns plan
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit
        coEvery { medicationRepository.saveMedicationPlan(any()) } returns Unit

        viewModel.selectDate(date)
        viewModel.markDayQuickMedicationMissed("med-1", null)
        runCurrent()

        coVerify {
            medicationRepository.addMedicationDoseEvent(
                match {
                    it.medicationPlanId == "med-1" &&
                        it.type == MedicationDoseEventType.MISSED &&
                        it.eventDate == date &&
                        it.scheduledMinuteOfDay == 8 * 60 &&
                        it.reason == "Marked missed" &&
                        it.doseAmount == null
                }
            )
        }
        coVerify {
            medicationRepository.saveMedicationPlan(
                match {
                    it.id == "med-1" &&
                        it.missedCount == 2 &&
                        it.safetyProfile?.supplyRemaining == 4
                }
            )
        }
    }

    private fun habit(id: String, title: String): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = 7 * 60,
        windowEndMinute = 7 * 60 + 15,
        difficulty = 1,
        isBundled = true,
        streakCount = 2,
        lastCompletedDate = null,
        isActive = true
    )

    private fun medicationPlan(
        id: String,
        missedCount: Int,
        supplyRemaining: Int?
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = "Vitamin D",
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        endAt = null,
        reminderMinuteOfDay = 8 * 60,
        takeWithFood = false,
        missedCount = missedCount,
        refillNeededAfterDoses = supplyRemaining,
        isActive = true,
        safetyProfile = supplyRemaining?.let {
            MedicationSafetyProfile(
                medicationPlanId = id,
                supplyRemaining = it,
                refillThreshold = 3
            )
        }
    )

    private fun timeBlock(
        id: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int,
        taskId: String? = null,
        taskOccurrenceDate: LocalDate? = null
    ): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Deep work",
            category = "FOCUS",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = ZoneId.systemDefault().id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = taskId,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            taskOccurrenceDate = taskOccurrenceDate,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
