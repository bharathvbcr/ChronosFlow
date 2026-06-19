package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.repository.AlarmRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScheduleMedicationReminderUseCaseTest {
    private val alarmRequestRepository: AlarmRequestRepository = mockk()
    private val useCase = ScheduleMedicationReminderUseCase(alarmRequestRepository)

    @Test
    fun `saves medication reminder requests`() = runTest {
        val request = UseCaseTestFixtures.medicationAlarmRequest()
        coEvery { alarmRequestRepository.saveAlarmRequest(request) } returns Unit

        useCase(request)

        coVerify(exactly = 1) { alarmRequestRepository.saveAlarmRequest(request) }
    }

    @Test
    fun `rejects reminder requests without medication plan id`() = runTest {
        val request = UseCaseTestFixtures.medicationAlarmRequest(medicationPlanId = null)
        var thrown: IllegalArgumentException? = null

        try {
            useCase(request)
        } catch (error: IllegalArgumentException) {
            thrown = error
        }

        assertNotNull(thrown)
        assertEquals("Medication reminders require medicationPlanId", thrown?.message)
        coVerify(exactly = 0) { alarmRequestRepository.saveAlarmRequest(any()) }
    }
}
