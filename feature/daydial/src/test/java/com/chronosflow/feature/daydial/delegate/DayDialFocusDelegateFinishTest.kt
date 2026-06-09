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
import com.chronosflow.feature.daydial.testManualMissedBlockRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialFocusDelegateFinishTest {
    @Test
    fun `finishFocusSession clears manual missed for block date`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val registry = testManualMissedBlockRegistry()
        registry.markMissed("block-1", today)

        val repository = mockk<TimeBlockRepository>()
        val block = sampleBlock("block-1", today)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        coEvery { logActualTimeUseCase(any()) } returns Unit

        val delegate = DayDialFocusDelegate(repository, logActualTimeUseCase, registry)
        delegate.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 25,
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        delegate.finishFocusSession(this, PlannerService(repository))
        advanceUntilIdle()

        assertFalse(registry.missedIdsForDate(today).contains("block-1"))
    }

    @Test
    fun `finishFocusSession stores completion exactly once`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val registry = testManualMissedBlockRegistry()
        val repository = mockk<TimeBlockRepository>()
        val block = sampleBlock("block-1", today)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit
        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        coEvery { logActualTimeUseCase(any()) } returns Unit
        val delegate = DayDialFocusDelegate(repository, logActualTimeUseCase, registry)
        val plannerService = PlannerService(repository)
        delegate.setFocusStateForTest(
            FocusExecutionState(
                blockId = "block-1",
                plannedDurationMinutes = 25,
                startedEpochMs = System.currentTimeMillis() - 60_000,
                status = FocusExecutionStatus.RUNNING
            )
        )

        delegate.finishFocusSession(this, plannerService)
        advanceUntilIdle()
        delegate.finishFocusSession(this, plannerService)
        advanceUntilIdle()

        coVerify(exactly = 1) { logActualTimeUseCase(any()) }
        coVerify(exactly = 1) {
            repository.saveTimeBlock(
                match {
                    it.id == "block-1" &&
                        it.actualStartMinuteOfDay == 600 &&
                        it.actualEndMinuteOfDay != null
                }
            )
        }
    }

    private fun sampleBlock(id: String, date: LocalDate): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = 600,
            durationMinutes = 25,
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
