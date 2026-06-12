package com.chronosflow.appfunctions

import androidx.appfunctions.AppFunctionContext
import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.HabitEvent
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MoodEnergyCheckIn
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.GoalRepository
import com.chronosflow.core.domain.repository.HabitRepository
import com.chronosflow.core.domain.repository.JournalRepository
import com.chronosflow.core.domain.repository.MedicationRepository
import com.chronosflow.core.domain.repository.MoodEnergyRepository
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.RoutineRepository
import com.chronosflow.core.domain.repository.SleepTrackRepository
import com.chronosflow.core.domain.repository.TaskRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.ApplyRoutineToDateUseCase
import com.chronosflow.core.domain.usecase.RecordSleepUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
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

    private val appFunctionContext: AppFunctionContext = mockk(relaxed = true)

    private val appFunctions = ChronosAppFunctions(
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
        plannerService = plannerService
    )

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
            plannerService.rebalanceDay(LocalDate.now(), match { it != null && it != 0 })
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
}
