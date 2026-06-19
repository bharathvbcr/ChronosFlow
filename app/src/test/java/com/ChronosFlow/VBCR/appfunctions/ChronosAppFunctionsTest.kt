package com.ChronosFlow.VBCR.appfunctions

import androidx.appfunctions.AppFunctionContext
import com.ChronosFlow.VBCR.core.data.focus.ManualMissedBlockRegistry
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.CalendarEventRepository
import com.ChronosFlow.VBCR.core.domain.repository.GoalRepository
import com.ChronosFlow.VBCR.core.domain.repository.FocusSessionRepository
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
import com.ChronosFlow.VBCR.core.domain.repository.JournalRepository
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import com.ChronosFlow.VBCR.core.domain.repository.RoutineRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepTrackRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.ApplyRoutineToDateUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.RecordSleepUseCase
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosAppFunctionsTest {

    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val habitRepository: HabitRepository = mockk(relaxed = true)
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val moodEnergyRepository: MoodEnergyRepository = mockk(relaxed = true)
    private val timeBlockRepository: TimeBlockRepository = mockk(relaxed = true)
    private val reviewRepository: ReviewRepository = mockk(relaxed = true)
    private val calendarEventRepository: CalendarEventRepository = mockk(relaxed = true)
    private val goalRepository: GoalRepository = mockk(relaxed = true)
    private val journalRepository: JournalRepository = mockk(relaxed = true)
    private val routineRepository: RoutineRepository = mockk(relaxed = true)
    private val sleepTrackRepository: SleepTrackRepository = mockk(relaxed = true)
    private val recordSleepUseCase: RecordSleepUseCase = mockk(relaxed = true)
    private val applyRoutineToDateUseCase: ApplyRoutineToDateUseCase = mockk(relaxed = true)
    private val plannerService: PlannerService = mockk()
    private val focusSessionRepository: FocusSessionRepository = mockk(relaxed = true)
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry = mockk(relaxed = true)
    private val timeBlockCompletionHandler: TimeBlockCompletionHandler = mockk(relaxed = true)

    private val appFunctionContext: AppFunctionContext = mockk(relaxed = true)

    private fun buildAppFunctions(
        featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
    ) = ChronosAppFunctions(
        taskRepository = taskRepository,
        habitRepository = habitRepository,
        medicationRepository = medicationRepository,
        moodEnergyRepository = moodEnergyRepository,
        timeBlockRepository = timeBlockRepository,
        reviewRepository = reviewRepository,
        calendarEventRepository = calendarEventRepository,
        goalRepository = goalRepository,
        journalRepository = journalRepository,
        routineRepository = routineRepository,
        sleepTrackRepository = sleepTrackRepository,
        recordSleepUseCase = recordSleepUseCase,
        applyRoutineToDateUseCase = applyRoutineToDateUseCase,
        timeBlockCompletionHandler = timeBlockCompletionHandler,
        plannerService = plannerService,
        focusSessionRepository = focusSessionRepository,
        manualMissedBlockRegistry = manualMissedBlockRegistry,
        featureFlagsSource = ChronosFeatureFlagsSource { featureFlags }
    )

    private val appFunctions = buildAppFunctions()

    @Test
    fun createTaskCallsSaveTask() = runTest {
        coEvery { taskRepository.saveTask(any()) } returns Unit

        val result = appFunctions.createTask(
            appFunctionContext = appFunctionContext,
            title = "Test Task",
            description = "Test Description",
            priority = 3,
            preferredDurationMinutes = 45
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            taskRepository.saveTask(match {
                it.title == "Test Task" &&
                it.description == "Test Description" &&
                it.priority == 3 &&
                it.preferredDurationMinutes == 45
            })
        }
    }

    @Test
    fun createTaskReturnsFalseWithoutTitle() = runTest {
        val result = appFunctions.createTask(
            appFunctionContext = appFunctionContext,
            title = " "
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            taskRepository.saveTask(any())
        }
    }

    @Test
    fun logHabitCompletedCallsAddHabitEvent() = runTest {
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        val result = appFunctions.logHabitCompleted(
            appFunctionContext = appFunctionContext,
            habitId = "habit-123",
            reason = "Completed successfully"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            habitRepository.addHabitEvent(match {
                it.habitId == "habit-123" &&
                it.reason == "Completed successfully"
            })
        }
    }

    @Test
    fun logHabitCompletedReturnsFalseWithoutHabitId() = runTest {
        val result = appFunctions.logHabitCompleted(
            appFunctionContext = appFunctionContext,
            habitId = null
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            habitRepository.addHabitEvent(any())
        }
    }

    @Test
    fun logHabitSkippedCallsAddHabitEvent() = runTest {
        coEvery { habitRepository.addHabitEvent(any()) } returns Unit

        val result = appFunctions.logHabitSkipped(
            appFunctionContext = appFunctionContext,
            habitId = "habit-456",
            reason = "Too tired"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            habitRepository.addHabitEvent(match {
                it.habitId == "habit-456" &&
                it.reason == "Too tired"
            })
        }
    }

    @Test
    fun logMedicationTakenCallsAddMedicationDoseEvent() = runTest {
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit

        val result = appFunctions.logMedicationTaken(
            appFunctionContext = appFunctionContext,
            medicationPlanId = "plan-789",
            doseAmount = "1 tablet",
            reason = "With water"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            medicationRepository.addMedicationDoseEvent(match {
                it.medicationPlanId == "plan-789" &&
                it.doseAmount == "1 tablet" &&
                it.reason == "With water"
            })
        }
    }

    @Test
    fun logMedicationTakenReturnsFalseWithoutPlanId() = runTest {
        val result = appFunctions.logMedicationTaken(
            appFunctionContext = appFunctionContext,
            medicationPlanId = ""
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            medicationRepository.addMedicationDoseEvent(any())
        }
    }

    @Test
    fun logMedicationSkippedCallsAddMedicationDoseEvent() = runTest {
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit

        val result = appFunctions.logMedicationSkipped(
            appFunctionContext = appFunctionContext,
            medicationPlanId = "plan-999",
            reason = "Out of supply"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            medicationRepository.addMedicationDoseEvent(match {
                it.medicationPlanId == "plan-999" &&
                it.reason == "Out of supply"
            })
        }
    }

    @Test
    fun logMoodEnergyCheckInCallsSaveCheckIn() = runTest {
        coEvery { moodEnergyRepository.save(any()) } returns Unit

        val result = appFunctions.logMoodEnergyCheckIn(
            appFunctionContext = appFunctionContext,
            moodScore = 8,
            energyScore = 7,
            stressScore = 3,
            focusScore = 9,
            notes = "Feeling great"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            moodEnergyRepository.save(match {
                it.moodScore == 8 &&
                it.energyScore == 7 &&
                it.stressScore == 3 &&
                it.focusScore == 9 &&
                it.notes == "Feeling great"
            })
        }
    }

    @Test
    fun logMoodEnergyCheckInReturnsFalseForInvalidScore() = runTest {
        val result = appFunctions.logMoodEnergyCheckIn(
            appFunctionContext = appFunctionContext,
            moodScore = 11,
            energyScore = 7,
            stressScore = 3,
            focusScore = 9
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            moodEnergyRepository.save(any())
        }
    }

    @Test
    fun addTimeBlockCallsSaveTimeBlock() = runTest {
        coEvery { timeBlockRepository.saveTimeBlock(any()) } returns Unit

        val result = appFunctions.addTimeBlock(
            appFunctionContext = appFunctionContext,
            title = "Focus Time",
            category = "work",
            startMinuteOfDay = 540,
            durationMinutes = 90,
            flexibilityOptional = "FIXED",
            energyLevelOptional = 4
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            timeBlockRepository.saveTimeBlock(match {
                it.title == "Focus Time" &&
                it.category == "work" &&
                it.startMinuteOfDay == 540 &&
                it.durationMinutes == 90
            })
        }
    }

    @Test
    fun addTimeBlockReturnsFalseWithoutRequiredTiming() = runTest {
        val result = appFunctions.addTimeBlock(
            appFunctionContext = appFunctionContext,
            title = "Focus Time",
            category = "work",
            startMinuteOfDay = null,
            durationMinutes = 90
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            timeBlockRepository.saveTimeBlock(any())
        }
    }

    @Test
    fun logActualTimeSegmentCallsSaveActualTimeSegment() = runTest {
        coEvery { reviewRepository.saveActualTimeSegment(any()) } returns Unit

        val result = appFunctions.logActualTimeSegment(
            appFunctionContext = appFunctionContext,
            activityTitle = "Studying",
            startInstantIso = "2026-05-28T15:00:00Z",
            endInstantIso = "2026-05-28T16:30:00Z"
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            reviewRepository.saveActualTimeSegment(match {
                it.source.name == "MANUAL_ENTRY"
            })
        }
    }

    @Test
    fun logActualTimeSegmentReturnsFalseWhenEndIsBeforeStart() = runTest {
        val result = appFunctions.logActualTimeSegment(
            appFunctionContext = appFunctionContext,
            activityTitle = "Studying",
            startInstantIso = "2026-05-28T16:30:00Z",
            endInstantIso = "2026-05-28T15:00:00Z"
        )

        assertFalse(result)
        coVerify(exactly = 0) {
            reviewRepository.saveActualTimeSegment(any())
        }
    }

    @Test
    fun completeTimeBlockDelegatesToHandler() = runTest {
        val block = timeBlock("block-1", "Deep Work", "work", start = 600, duration = 50)
        coEvery { timeBlockRepository.getTimeBlockById("block-1") } returns block

        val result = appFunctions.completeTimeBlock(
            appFunctionContext = appFunctionContext,
            blockId = "block-1"
        )

        assertTrue(result)
        // The full completion (log + recurring advance + clear missed) is owned by the shared
        // handler, covered by TimeBlockCompletionHandlerTest. The AppFunction resolves + delegates.
        coVerify(exactly = 1) { timeBlockCompletionHandler.complete(match { it.id == "block-1" }) }
    }

    @Test
    fun completeTimeBlockReturnsFalseForUnknownBlock() = runTest {
        coEvery { timeBlockRepository.getTimeBlockById(any()) } returns null

        val result = appFunctions.completeTimeBlock(
            appFunctionContext = appFunctionContext,
            blockId = "missing"
        )

        assertFalse(result)
        coVerify(exactly = 0) { timeBlockCompletionHandler.complete(any()) }
    }

    @Test
    fun markTimeBlockMissedFlagsBlock() = runTest {
        val block = timeBlock("block-1", "Deep Work", "work", start = 600, duration = 50)
        coEvery { timeBlockRepository.getTimeBlockById("block-1") } returns block

        val result = appFunctions.markTimeBlockMissed(
            appFunctionContext = appFunctionContext,
            blockId = "block-1"
        )

        assertTrue(result)
        verify(exactly = 1) { manualMissedBlockRegistry.markMissed("block-1", any()) }
    }

    @Test
    fun markTimeBlockMissedReturnsFalseForUnknownBlock() = runTest {
        coEvery { timeBlockRepository.getTimeBlockById(any()) } returns null

        val result = appFunctions.markTimeBlockMissed(
            appFunctionContext = appFunctionContext,
            blockId = "missing"
        )

        assertFalse(result)
        verify(exactly = 0) { manualMissedBlockRegistry.markMissed(any(), any()) }
    }

    @Test
    fun completeTimeBlockResolvesByTitleWhenNoId() = runTest {
        val block = timeBlock("block-1", "Deep Work", "work", start = 600, duration = 50)
        every { timeBlockRepository.getTimeBlocksByDate(any()) } returns flowOf(listOf(block))

        val result = appFunctions.completeTimeBlock(
            appFunctionContext = appFunctionContext,
            blockTitle = "deep work"
        )

        assertTrue(result)
        coVerify(exactly = 1) { timeBlockCompletionHandler.complete(match { it.id == "block-1" }) }
    }

    @Test
    fun completeTimeBlockPrefersIncompleteBlockOnTitleMatch() = runTest {
        val done = timeBlock("done", "Study", "study", start = 540, duration = 60)
            .copy(actualStartMinuteOfDay = 540, actualEndMinuteOfDay = 600)
        val pending = timeBlock("pending", "Study", "study", start = 800, duration = 60)
        every { timeBlockRepository.getTimeBlocksByDate(any()) } returns flowOf(listOf(done, pending))

        val result = appFunctions.completeTimeBlock(
            appFunctionContext = appFunctionContext,
            blockTitle = "Study"
        )

        assertTrue(result)
        // The not-yet-done block is targeted, not the already-completed duplicate.
        coVerify(exactly = 1) { timeBlockCompletionHandler.complete(match { it.id == "pending" }) }
    }

    @Test
    fun completeTimeBlockReturnsFalseWithoutIdentifier() = runTest {
        val result = appFunctions.completeTimeBlock(appFunctionContext = appFunctionContext)

        assertFalse(result)
        coVerify(exactly = 0) { timeBlockCompletionHandler.complete(any()) }
    }

    @Test
    fun markTimeBlockMissedResolvesByTitle() = runTest {
        val block = timeBlock("block-1", "Workout", "fitness", start = 700, duration = 30)
        every { timeBlockRepository.getTimeBlocksByDate(any()) } returns flowOf(listOf(block))

        val result = appFunctions.markTimeBlockMissed(
            appFunctionContext = appFunctionContext,
            blockTitle = "workout"
        )

        assertTrue(result)
        verify(exactly = 1) { manualMissedBlockRegistry.markMissed("block-1", any()) }
    }

    @Test
    fun syncCalendarEventsCallsSyncFromDeviceCalendar() = runTest {
        coEvery { calendarEventRepository.syncFromDeviceCalendar(any(), any()) } returns Unit

        val result = appFunctions.syncCalendarEvents(
            appFunctionContext = appFunctionContext
        )

        assertTrue(result)
        coVerify(exactly = 1) {
            calendarEventRepository.syncFromDeviceCalendar(any(), any())
        }
    }

    @Test
    fun reflowRemainingDayReflowsTodayFromNow() = runTest {
        coEvery { plannerService.rebalanceDay(any(), any()) } returns
            PlannerOperationResult.Applied("Day rebalance complete", "", null, listOf("b1"))

        val result = appFunctions.reflowRemainingDay(
            appFunctionContext = appFunctionContext
        )

        assertTrue(result)
        // today is reflowed from the current minute, not from midnight
        coVerify(exactly = 1) {
            plannerService.rebalanceDay(LocalDate.now(), match { it != 0 })
        }
    }

    @Test
    fun reflowRemainingDayReturnsFalseWhenNothingFlexible() = runTest {
        coEvery { plannerService.rebalanceDay(any(), any()) } returns
            PlannerOperationResult.Rejected("No flexible blocks to rebalance", "")

        val result = appFunctions.reflowRemainingDay(
            appFunctionContext = appFunctionContext
        )

        assertFalse(result)
    }

    @Test
    fun getTodayScheduleReturnsBlocksSortedByStart() = runTest {
        every { timeBlockRepository.getTimeBlocksByDate(any()) } returns flowOf(
            listOf(
                timeBlock(id = "b2", title = "Lunch", category = "meals", start = 720, duration = 60)
                    .copy(actualStartMinuteOfDay = 720, actualEndMinuteOfDay = 780),
                timeBlock(id = "b1", title = "Deep Work", category = "work", start = 540, duration = 90)
            )
        )
        every { manualMissedBlockRegistry.ids } returns MutableStateFlow(emptySet())
        every { manualMissedBlockRegistry.missedIdsForDate(any(), any()) } returns setOf("b1")

        val result = appFunctions.getTodaySchedule(appFunctionContext)

        assertEquals(listOf("b1", "b2"), result.map { it.id })
        assertEquals("Deep Work", result.first().title)
        assertEquals(540, result.first().startMinuteOfDay)
        assertEquals(90, result.first().durationMinutes)
        // completed + missed let an agent route each block correctly.
        assertFalse(result.first { it.id == "b1" }.completed)
        assertTrue(result.first { it.id == "b2" }.completed)
        assertTrue(result.first { it.id == "b1" }.missed)
        assertFalse(result.first { it.id == "b2" }.missed)
    }

    @Test
    fun listOpenTasksExcludesCompletedAndSortsByPriority() = runTest {
        every { taskRepository.getAllTasks() } returns flowOf(
            listOf(
                task(id = "t1", title = "Low", completed = false, priority = 1, durationMinutes = null),
                task(id = "t2", title = "Done", completed = true, priority = 5, durationMinutes = 30),
                task(id = "t3", title = "High", completed = false, priority = 4, durationMinutes = 15)
            )
        )

        val result = appFunctions.listOpenTasks(appFunctionContext)

        assertEquals(listOf("t3", "t1"), result.map { it.id })
        assertEquals(4, result.first().priority)
        assertEquals(15, result.first().preferredDurationMinutes)
        // Null preferred duration is normalized to 0.
        assertEquals(0, result.last().preferredDurationMinutes)
    }

    @Test
    fun listHabitsReturnsOnlyActive() = runTest {
        every { habitRepository.observeHabits() } returns flowOf(
            listOf(
                habit(id = "h1", title = "Read", cadence = "daily", streak = 3, active = true),
                habit(id = "h2", title = "Retired", cadence = "weekly", streak = 0, active = false)
            )
        )

        val result = appFunctions.listHabits(appFunctionContext)

        assertEquals(listOf("h1"), result.map { it.id })
        assertEquals("Read", result.first().title)
        assertEquals(3, result.first().streakCount)
    }

    @Test
    fun listMedicationsReturnsOnlyActiveWithCombinedDosage() = runTest {
        every { medicationRepository.observeMedicationPlans() } returns flowOf(
            listOf(
                medication(id = "m1", name = "Vitamin D", dosage = "1", unit = "tablet", reminder = 480, active = true),
                medication(id = "m2", name = "Retired", dosage = "2", unit = "ml", reminder = 600, active = false)
            )
        )

        val result = appFunctions.listMedications(appFunctionContext)

        assertEquals(listOf("m1"), result.map { it.id })
        assertEquals("1 tablet", result.first().dosage)
        assertEquals(480, result.first().reminderMinuteOfDay)
    }

    @Test
    fun createGoalIsBlockedWhenGoalsFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(goalsEnabled = false)).createGoal(
            appFunctionContext = appFunctionContext,
            title = "Run a marathon"
        )

        assertFalse(result)
        coVerify(exactly = 0) { goalRepository.saveGoal(any()) }
    }

    @Test
    fun addJournalEntryIsBlockedWhenJournalFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(journalEnabled = false)).addJournalEntry(
            appFunctionContext = appFunctionContext,
            text = "A quiet evening."
        )

        assertFalse(result)
        coVerify(exactly = 0) { journalRepository.save(any()) }
    }

    @Test
    fun logSleepIsBlockedWhenSleepFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(sleepEnabled = false)).logSleep(
            appFunctionContext = appFunctionContext,
            quality = 4
        )

        assertFalse(result)
        coVerify(exactly = 0) { recordSleepUseCase(any()) }
    }

    @Test
    fun logHabitCompletedIsBlockedWhenHabitsFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(habitsEnabled = false)).logHabitCompleted(
            appFunctionContext = appFunctionContext,
            habitId = "h1"
        )

        assertFalse(result)
        coVerify(exactly = 0) { habitRepository.addHabitEvent(any()) }
    }

    @Test
    fun logMedicationTakenIsBlockedWhenMedicationFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(medicationEnabled = false)).logMedicationTaken(
            appFunctionContext = appFunctionContext,
            medicationPlanId = "m1"
        )

        assertFalse(result)
        coVerify(exactly = 0) { medicationRepository.addMedicationDoseEvent(any()) }
    }

    @Test
    fun listHabitsIsEmptyWhenHabitsFeatureDisabled() = runTest {
        val result = buildAppFunctions(ChronosFeatureFlags(habitsEnabled = false))
            .listHabits(appFunctionContext)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { habitRepository.observeHabits() }
    }

    private fun timeBlock(
        id: String,
        title: String,
        category: String,
        start: Int,
        duration: Int
    ): TimeBlock = TimeBlock(
        id = id,
        date = LocalDate.now(),
        title = title,
        category = category,
        startMinuteOfDay = start,
        durationMinutes = duration,
        timezone = "UTC",
        provenance = BlockProvenance.USER_CREATED,
        flexibility = BlockFlexibility.MOVABLE,
        energyLevel = EnergyIntensity.fromLevel(2),
        source = "test",
        taskId = null,
        calendarEventId = null,
        medicationPlanId = null,
        habitId = null,
        isLocked = false,
        isProtected = false,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun task(
        id: String,
        title: String,
        completed: Boolean,
        priority: Int,
        durationMinutes: Int?
    ): Task = Task(
        id = id,
        title = title,
        description = null,
        isCompleted = completed,
        priority = priority,
        dueDate = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        preferredDurationMinutes = durationMinutes
    )

    private fun habit(
        id: String,
        title: String,
        cadence: String,
        streak: Int,
        active: Boolean
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = cadence,
        windowStartMinute = 0,
        windowEndMinute = 60,
        difficulty = 1,
        isBundled = false,
        streakCount = streak,
        lastCompletedDate = null,
        isActive = active
    )

    private fun medication(
        id: String,
        name: String,
        dosage: String,
        unit: String,
        reminder: Int,
        active: Boolean
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = dosage,
        unit = unit,
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = reminder,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = active
    )
}
