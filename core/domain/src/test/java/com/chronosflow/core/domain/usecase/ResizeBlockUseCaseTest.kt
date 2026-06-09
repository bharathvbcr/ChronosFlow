package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class ResizeBlockUseCaseTest {
    private val plannerService: PlannerService = mockk()
    private val useCase = ResizeBlockUseCase(plannerService)

    @Test
    fun `delegates duration resize to planner service`() = runTest {
        val result = PlannerOperationResult.Applied("resized", "block-1", 9 * 60)
        coEvery { plannerService.resizeBlock("block-1", 75) } returns result

        assertSame(result, useCase("block-1", 75))
        coVerify(exactly = 1) { plannerService.resizeBlock("block-1", 75) }
    }

    @Test
    fun `delegates window resize to planner service`() = runTest {
        val result = PlannerOperationResult.Applied("resized", "block-1", 9 * 60 + 15)
        coEvery { plannerService.resizeBlockWindow("block-1", 9 * 60 + 15, 45) } returns result

        assertSame(result, useCase("block-1", 9 * 60 + 15, 45))
        coVerify(exactly = 1) { plannerService.resizeBlockWindow("block-1", 9 * 60 + 15, 45) }
    }
}
