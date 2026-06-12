package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.SleepTrackRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecordSleepUseCaseTest {
    private val sleepTrackRepository: SleepTrackRepository = mockk(relaxed = true)
    private val sleepScheduleRepository: SleepScheduleRepository = mockk()
    private val useCase = RecordSleepUseCase(sleepTrackRepository, sleepScheduleRepository)

    @Test
    fun `prefills planned minutes from schedule when caller omits them`() = runTest {
        every { sleepScheduleRepository.getSleepSchedule() } returns
            SleepSchedule(enabled = true, startMinute = 1380, endMinute = 420)
        val captured = slot<SleepTrack>()
        coEvery { sleepTrackRepository.upsert(capture(captured)) } returns Unit

        useCase(track(plannedStart = null, plannedEnd = null))

        assertEquals(1380, captured.captured.plannedStartMinute)
        assertEquals(420, captured.captured.plannedEndMinute)
    }

    @Test
    fun `keeps caller-supplied planned minutes`() = runTest {
        val captured = slot<SleepTrack>()
        coEvery { sleepTrackRepository.upsert(capture(captured)) } returns Unit

        useCase(track(plannedStart = 1300, plannedEnd = 400))

        assertEquals(1300, captured.captured.plannedStartMinute)
        coVerify(exactly = 0) { sleepScheduleRepository.getSleepSchedule() }
    }

    private fun track(plannedStart: Int?, plannedEnd: Int?) = SleepTrack(
        id = "sleep-1",
        date = LocalDate.of(2026, 6, 11),
        plannedStartMinute = plannedStart,
        plannedEndMinute = plannedEnd,
        actualStartMinute = 1390,
        actualEndMinute = 410,
        sleepQuality = 4,
        windDownNotes = null,
        interruptedCount = 0
    )
}
