package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecordMedicationWidgetActionUseCaseTest {
    private val medicationRepository: MedicationRepository = mockk()
    private val useCase = RecordMedicationWidgetActionUseCase(medicationRepository)

    @Test
    fun `taken action records a TAKEN dose event without touching the plan`() = runTest {
        coEvery { medicationRepository.getMedicationPlanById("medication-1") } returns
            UseCaseTestFixtures.medicationPlan(id = "medication-1", missedCount = 2)
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit

        useCase("medication-1", taken = true, today = UseCaseTestFixtures.date, now = UseCaseTestFixtures.now)

        coVerify(exactly = 1) {
            medicationRepository.addMedicationDoseEvent(
                match {
                    it.medicationPlanId == "medication-1" &&
                        it.type == MedicationDoseEventType.TAKEN &&
                        it.eventDate == UseCaseTestFixtures.date &&
                        it.doseAmount == "1"
                }
            )
        }
        coVerify(exactly = 0) { medicationRepository.saveMedicationPlan(any()) }
    }

    @Test
    fun `missed action records a MISSED dose event and increments missed count`() = runTest {
        coEvery { medicationRepository.getMedicationPlanById("medication-1") } returns
            UseCaseTestFixtures.medicationPlan(id = "medication-1", missedCount = 2)
        coEvery { medicationRepository.addMedicationDoseEvent(any()) } returns Unit
        coEvery { medicationRepository.saveMedicationPlan(any()) } returns Unit

        useCase("medication-1", taken = false, today = UseCaseTestFixtures.date, now = UseCaseTestFixtures.now)

        coVerify(exactly = 1) {
            medicationRepository.addMedicationDoseEvent(
                match { it.type == MedicationDoseEventType.MISSED && it.doseAmount == null }
            )
        }
        coVerify(exactly = 1) {
            medicationRepository.saveMedicationPlan(
                match { it.id == "medication-1" && it.missedCount == 3 }
            )
        }
    }

    @Test
    fun `unknown plan id is a no-op`() = runTest {
        coEvery { medicationRepository.getMedicationPlanById("missing") } returns null

        useCase("missing", taken = true)

        coVerify(exactly = 0) { medicationRepository.addMedicationDoseEvent(any()) }
        coVerify(exactly = 0) { medicationRepository.saveMedicationPlan(any()) }
    }
}
