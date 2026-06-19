package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class CreateBlockUseCaseTest {
    private val plannerService: PlannerService = mockk()
    private val useCase = CreateBlockUseCase(plannerService)

    @Test
    fun `delegates block creation to planner service`() = runTest {
        val block = UseCaseTestFixtures.timeBlock()
        val result = PlannerOperationResult.Applied("created", block.id, block.startMinuteOfDay)
        coEvery { plannerService.createBlock(block) } returns result

        assertSame(result, useCase(block))
        coVerify(exactly = 1) { plannerService.createBlock(block) }
    }
}
