package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class MoveBlockUseCaseTest {
    private val plannerService: PlannerService = mockk()
    private val useCase = MoveBlockUseCase(plannerService)

    @Test
    fun `delegates move request to planner service`() = runTest {
        val result = PlannerOperationResult.Applied("moved", "block-1", 10 * 60)
        coEvery { plannerService.moveBlock("block-1", 10 * 60) } returns result

        assertSame(result, useCase("block-1", 10 * 60))
        coVerify(exactly = 1) { plannerService.moveBlock("block-1", 10 * 60) }
    }
}
