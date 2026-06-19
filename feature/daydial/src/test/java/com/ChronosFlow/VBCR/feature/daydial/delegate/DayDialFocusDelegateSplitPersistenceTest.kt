package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.FocusSessionState
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.LogActualTimeUseCase
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionState
import com.ChronosFlow.VBCR.feature.daydial.FocusExecutionStatus
import com.ChronosFlow.VBCR.feature.daydial.model.FocusPhase
import com.ChronosFlow.VBCR.feature.daydial.model.FocusPhaseKind
import com.ChronosFlow.VBCR.feature.daydial.testManualMissedBlockRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialFocusDelegateSplitPersistenceTest {

    private class FakeSplitStore : FocusSplitSessionStore {
        var saved: FocusExecutionState? = null
        override fun save(state: FocusExecutionState) { saved = state }
        override fun load(): FocusExecutionState? = saved
        override fun clear() { saved = null }
    }

    private val pomodoroPhases = listOf(
        FocusPhase(FocusPhaseKind.FOCUS, 25),
        FocusPhase(FocusPhaseKind.BREAK, 5),
        FocusPhase(FocusPhaseKind.FOCUS, 25),
        FocusPhase(FocusPhaseKind.BREAK, 5)
    )

    @Test
    fun `starting a split session persists it and finishing clears it`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val repository = mockk<TimeBlockRepository>()
        coEvery { repository.getTimeBlockById("block-1") } returns sampleBlock("block-1", today)
        coEvery { repository.saveTimeBlock(any()) } returns Unit
        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        val store = FakeSplitStore()
        val delegate = DayDialFocusDelegate(
            repository, logActualTimeUseCase, testManualMissedBlockRegistry(), store
        )
        val plannerService = PlannerService(repository)

        delegate.startFocusSession(this, plannerService, "block-1", workMinutes = 25, breakMinutes = 5)
        advanceUntilIdle()

        assertNotNull(store.saved)
        assertTrue(store.saved!!.isSplitSession)

        delegate.finishFocusSession(this, plannerService)
        advanceUntilIdle()

        assertNull(store.saved)
    }

    @Test
    fun `restore prefers a persisted split snapshot for the same block`() = runTest {
        val store = FakeSplitStore()
        store.saved = FocusExecutionState(
            blockId = "block-1",
            blockStartMinute = 600,
            plannedDurationMinutes = 25,
            startedEpochMs = System.currentTimeMillis() - 60_000,
            status = FocusExecutionStatus.RUNNING,
            phases = pomodoroPhases,
            currentPhaseIndex = 0
        )
        val delegate = DayDialFocusDelegate(
            mockk(relaxed = true), mockk(relaxed = true), testManualMissedBlockRegistry(), store
        )

        delegate.restoreFromPersistedSession(runningSession(blockId = "block-1"))

        val state = delegate.focusExecutionState.value
        assertTrue(state.isSplitSession)
        assertEquals(4, state.phases.size)
    }

    @Test
    fun `restore falls back to flat block when the snapshot is for a different block`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val repository = mockk<TimeBlockRepository>()
        coEvery { repository.getTimeBlockById("block-1") } returns sampleBlock("block-1", today)
        val store = FakeSplitStore()
        store.saved = FocusExecutionState(
            blockId = "other-block",
            plannedDurationMinutes = 25,
            startedEpochMs = System.currentTimeMillis(),
            status = FocusExecutionStatus.RUNNING,
            phases = pomodoroPhases,
            currentPhaseIndex = 0
        )
        val delegate = DayDialFocusDelegate(
            repository, mockk(relaxed = true), testManualMissedBlockRegistry(), store
        )

        delegate.restoreFromPersistedSession(runningSession(blockId = "block-1"))

        assertFalse(delegate.focusExecutionState.value.isSplitSession)
        assertEquals("block-1", delegate.focusExecutionState.value.blockId)
    }

    private fun runningSession(blockId: String): FocusSessionState.Running {
        val now = Instant.now()
        return FocusSessionState.Running(
            sessionId = "session-1",
            blockId = blockId,
            startedAt = now.minusSeconds(300),
            plannedEndAt = now.plusSeconds(1200)
        )
    }

    private fun sampleBlock(id: String, date: LocalDate): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = 600,
            durationMinutes = 60,
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
