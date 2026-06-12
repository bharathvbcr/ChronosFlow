package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.LogActualTimeUseCase
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import com.chronosflow.feature.daydial.model.FocusPhase
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import com.chronosflow.feature.daydial.testManualMissedBlockRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialFocusDelegatePhaseTest {

    private val pomodoroPhases = listOf(
        FocusPhase(FocusPhaseKind.FOCUS, 25),
        FocusPhase(FocusPhaseKind.BREAK, 5),
        FocusPhase(FocusPhaseKind.FOCUS, 25),
        FocusPhase(FocusPhaseKind.BREAK, 5)
    )

    @Test
    fun `startFocusSession with split config builds phases and starts on first interval`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val repository = mockk<TimeBlockRepository>()
        val block = sampleBlock("block-1", today, durationMinutes = 60)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit
        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        val delegate = DayDialFocusDelegate(repository, logActualTimeUseCase, testManualMissedBlockRegistry())

        delegate.startFocusSession(this, PlannerService(repository), "block-1", workMinutes = 25, breakMinutes = 5)
        advanceUntilIdle()

        val state = delegate.focusExecutionState.value
        assertTrue(state.isSplitSession)
        assertEquals(4, state.phases.size)
        assertEquals(0, state.currentPhaseIndex)
        assertEquals(25, state.plannedDurationMinutes)
        assertEquals(FocusExecutionStatus.RUNNING, state.status)
    }

    @Test
    fun `checkPhaseBoundary holds at a non-final boundary awaiting advance`() = runTest {
        val delegate = newDelegate()
        delegate.setFocusStateForTest(
            splitState(currentPhaseIndex = 0, plannedDurationMinutes = 25, elapsedPastBySeconds = 5)
        )

        delegate.checkPhaseBoundary(this, PlannerService(mockk()))

        val state = delegate.focusExecutionState.value
        assertEquals(FocusExecutionStatus.PAUSED, state.status)
        assertTrue(state.awaitingPhaseAdvance)
        assertEquals(0, state.currentPhaseIndex)
    }

    @Test
    fun `advancePhase starts the next phase and resets the clock`() = runTest {
        val delegate = newDelegate()
        delegate.setFocusStateForTest(
            splitState(currentPhaseIndex = 0, plannedDurationMinutes = 25, elapsedPastBySeconds = 5)
                .copy(status = FocusExecutionStatus.PAUSED, awaitingPhaseAdvance = true)
        )

        delegate.advancePhase()

        val state = delegate.focusExecutionState.value
        assertEquals(FocusExecutionStatus.RUNNING, state.status)
        assertFalse(state.awaitingPhaseAdvance)
        assertEquals(1, state.currentPhaseIndex)
        assertEquals(5, state.plannedDurationMinutes)
        assertTrue(state.isOnBreak)
        // Fresh clock for the new phase: almost the full break remains.
        assertTrue(delegate.focusRemainingSeconds(state) >= 290)
    }

    @Test
    fun `extending a split session lengthens only the current phase`() = runTest {
        val delegate = newDelegate()
        delegate.setFocusStateForTest(
            splitState(currentPhaseIndex = 0, plannedDurationMinutes = 25, elapsedPastBySeconds = -600)
        )

        delegate.extendFocusSession(5)

        val state = delegate.focusExecutionState.value
        assertEquals(30, state.plannedDurationMinutes)
        assertEquals(30, state.phases[0].durationMinutes)
        // Other phases untouched.
        assertEquals(5, state.phases[1].durationMinutes)
        assertEquals(25, state.phases[2].durationMinutes)
        assertEquals(5, state.phases[3].durationMinutes)
    }

    @Test
    fun `checkPhaseBoundary finishes the session on the final phase and logs focus minutes`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val repository = mockk<TimeBlockRepository>()
        val block = sampleBlock("block-1", today, durationMinutes = 60)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit
        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        coEvery { logActualTimeUseCase(any()) } returns Unit
        val delegate = DayDialFocusDelegate(repository, logActualTimeUseCase, testManualMissedBlockRegistry())

        delegate.setFocusStateForTest(
            splitState(currentPhaseIndex = 3, plannedDurationMinutes = 5, elapsedPastBySeconds = 5)
        )

        delegate.checkPhaseBoundary(this, PlannerService(repository))
        advanceUntilIdle()

        assertEquals(FocusExecutionStatus.FINISHED, delegate.focusExecutionState.value.status)
        // Two completed 25m focus intervals -> 50 focus minutes credited (650 = 600 + 50).
        coVerify(exactly = 1) {
            repository.saveTimeBlock(
                match { it.id == "block-1" && it.actualEndMinuteOfDay == 650 }
            )
        }
    }

    private fun newDelegate(): DayDialFocusDelegate {
        val repository = mockk<TimeBlockRepository>(relaxed = true)
        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        return DayDialFocusDelegate(repository, logActualTimeUseCase, testManualMissedBlockRegistry())
    }

    private fun splitState(
        currentPhaseIndex: Int,
        plannedDurationMinutes: Int,
        elapsedPastBySeconds: Int
    ): FocusExecutionState {
        val startedAt = System.currentTimeMillis() -
            (plannedDurationMinutes * 60L + elapsedPastBySeconds) * 1000L
        return FocusExecutionState(
            blockId = "block-1",
            blockStartMinute = 600,
            plannedDurationMinutes = plannedDurationMinutes,
            startedEpochMs = startedAt,
            status = FocusExecutionStatus.RUNNING,
            phases = pomodoroPhases,
            currentPhaseIndex = currentPhaseIndex
        )
    }

    private fun sampleBlock(id: String, date: LocalDate, durationMinutes: Int): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = 600,
            durationMinutes = durationMinutes,
            timezone = ZoneId.systemDefault().id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            taskOccurrenceDate = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
