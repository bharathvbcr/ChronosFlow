package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.repository.MedicationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ObserveMedicationAdherenceTrendUseCaseTest {
    private val repository: MedicationRepository = mockk()
    private val useCase = ObserveMedicationAdherenceTrendUseCase(repository)
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `maps observed events through deriveMedicationAdherenceTrend`() = runTest {
        val start = today.minusDays(1)
        every { repository.observeDoseEventsBetween(start, today) } returns flowOf(
            listOf(
                event(today, MedicationDoseEventType.TAKEN),
                event(today, MedicationDoseEventType.MISSED),
                event(today.minusDays(1), MedicationDoseEventType.TAKEN)
            )
        )

        val trend = useCase(windowDays = 2, today = today).first()

        assertEquals(2, trend.size)
        assertEquals(1, trend.first().takenCount)
        assertEquals(1, trend.last().takenCount)
        assertEquals(1, trend.last().missedCount)
    }

    @Test
    fun `non-positive window short-circuits to empty`() = runTest {
        val trend = useCase(windowDays = 0, today = today).first()
        assertTrue(trend.isEmpty())
    }

    private fun event(date: LocalDate, type: MedicationDoseEventType) = MedicationDoseEvent(
        id = "d-$date-$type",
        medicationPlanId = "plan-1",
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-06-11T00:00:00Z"),
        scheduledMinuteOfDay = null,
        reason = null,
        doseAmount = null
    )
}
