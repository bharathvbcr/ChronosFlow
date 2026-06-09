package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.repository.MedicationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordMedicationWidgetActionUseCaseTest {
    private val medicationRepository: MedicationRepository = mockk()
    private val useCase = RecordMedicationWidgetActionUseCase(medicationRepository)

    @Test
    fun `increments missed count when widget action is not taken`() = runTest {
        coEvery { medicationRepository.getMedicationPlanById("medication-1") } returns
            UseCaseTestFixtures.medicationPlan(id = "medication-1", missedCount = 2)
        coEvery { medicationRepository.saveMedicationPlan(any()) } returns Unit

        useCase("medication-1", taken = false)

        coVerify(exactly = 1) {
            medicationRepository.saveMedicationPlan(
                match { it.id == "medication-1" && it.missedCount == 3 }
            )
        }
    }

    @Test
    fun `taken action does not create a partial write`() = runTest {
        useCase("medication-1", taken = true)

        coVerify(exactly = 0) { medicationRepository.getMedicationPlanById(any()) }
        coVerify(exactly = 0) { medicationRepository.saveMedicationPlan(any()) }
    }
}
