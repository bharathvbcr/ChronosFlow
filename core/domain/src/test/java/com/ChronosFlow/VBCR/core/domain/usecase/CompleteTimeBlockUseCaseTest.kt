package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CompleteTimeBlockUseCaseTest {
    private val plannerService: PlannerService = mockk(relaxed = true)
    private val reviewRepository: ReviewRepository = mockk(relaxed = true)
    private val useCase = CompleteTimeBlockUseCase(plannerService, reviewRepository)

    @Test
    fun `logs the planned window as actual time for an incomplete block`() = runTest {
        coEvery { plannerService.logActualWindow(any(), any(), any()) } returns
            PlannerOperationResult.Applied("ok", "block-1", 600, listOf("block-1"))
        coEvery { reviewRepository.saveActualTimeSegment(any()) } returns Unit

        val didLog = useCase(sampleBlock(actualStart = null))

        assertTrue(didLog)
        coVerify(exactly = 1) { plannerService.logActualWindow("block-1", 600, 650) }
        coVerify(exactly = 1) {
            reviewRepository.saveActualTimeSegment(match { it.blockId == "block-1" })
        }
    }

    @Test
    fun `is a no-op for a block that already has actual time`() = runTest {
        val didLog = useCase(sampleBlock(actualStart = 600, actualEnd = 650))

        assertFalse(didLog)
        coVerify(exactly = 0) { plannerService.logActualWindow(any(), any(), any()) }
        coVerify(exactly = 0) { reviewRepository.saveActualTimeSegment(any()) }
    }

    private fun sampleBlock(actualStart: Int? = null, actualEnd: Int? = null): TimeBlock = TimeBlock(
        id = "block-1",
        date = LocalDate.of(2026, 5, 26),
        title = "Deep Work",
        category = "work",
        startMinuteOfDay = 600,
        durationMinutes = 50,
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
        actualStartMinuteOfDay = actualStart,
        actualEndMinuteOfDay = actualEnd,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
