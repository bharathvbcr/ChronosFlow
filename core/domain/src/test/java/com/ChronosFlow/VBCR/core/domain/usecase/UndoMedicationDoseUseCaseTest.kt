package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class UndoMedicationDoseUseCaseTest {
    private val repo: MedicationRepository = mockk(relaxed = true)
    private val useCase = UndoMedicationDoseUseCase(repo)
    private val today: LocalDate = UseCaseTestFixtures.date

    private fun doseEvent(
        id: String,
        type: MedicationDoseEventType,
        date: LocalDate = today,
        recordedAt: Instant = UseCaseTestFixtures.now
    ): MedicationDoseEvent = MedicationDoseEvent(
        id = id,
        medicationPlanId = "med-1",
        type = type,
        eventDate = date,
        recordedAt = recordedAt,
        scheduledMinuteOfDay = 8 * 60,
        reason = null,
        doseAmount = if (type == MedicationDoseEventType.TAKEN) "1" else null
    )

    @Test
    fun `deletes the most recent TAKEN event for today, leaving history otherwise intact`() = runTest {
        coEvery { repo.getDoseEventsForPlan("med-1") } returns listOf(
            doseEvent("earlier", MedicationDoseEventType.TAKEN, recordedAt = UseCaseTestFixtures.now.minusSeconds(600)),
            doseEvent("latest", MedicationDoseEventType.TAKEN, recordedAt = UseCaseTestFixtures.now)
        )

        useCase("med-1", today = today)

        coVerify(exactly = 1) { repo.deleteMedicationDoseEvent("latest") }
        coVerify(exactly = 0) { repo.deleteMedicationDoseEvent("earlier") }
    }

    @Test
    fun `ignores MISSED events and other days`() = runTest {
        coEvery { repo.getDoseEventsForPlan("med-1") } returns listOf(
            doseEvent("missed", MedicationDoseEventType.MISSED),
            doseEvent("yesterday", MedicationDoseEventType.TAKEN, date = today.minusDays(1))
        )

        useCase("med-1", today = today)

        coVerify(exactly = 0) { repo.deleteMedicationDoseEvent(any()) }
    }

    @Test
    fun `no taken dose today is a no-op`() = runTest {
        coEvery { repo.getDoseEventsForPlan("med-1") } returns emptyList()

        useCase("med-1", today = today)

        coVerify(exactly = 0) { repo.deleteMedicationDoseEvent(any()) }
    }
}
