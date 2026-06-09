package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.planner.PlannerOperationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyAiPlanUseCaseTest {
    private val createBlockUseCase: CreateBlockUseCase = mockk()
    private val useCase = ApplyAiPlanUseCase(createBlockUseCase)

    @Test
    fun `counts only suggestions that planner accepts`() = runTest {
        val accepted = UseCaseTestFixtures.timeBlock(id = "accepted")
        val rejected = UseCaseTestFixtures.timeBlock(id = "rejected")
        coEvery { createBlockUseCase(accepted) } returns PlannerOperationResult.Applied(
            message = "accepted",
            blockId = "accepted",
            snappedToMinute = accepted.startMinuteOfDay
        )
        coEvery { createBlockUseCase(rejected) } returns PlannerOperationResult.Rejected(
            message = "conflict",
            blockId = "rejected"
        )

        val count = useCase(listOf(accepted, rejected))

        assertEquals(1, count)
        coVerify(exactly = 1) { createBlockUseCase(accepted) }
        coVerify(exactly = 1) { createBlockUseCase(rejected) }
    }
}
