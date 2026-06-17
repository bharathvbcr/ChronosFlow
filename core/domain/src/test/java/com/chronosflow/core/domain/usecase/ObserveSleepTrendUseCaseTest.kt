package com.chronosflow.core.domain.usecase

import com.chronosflow.core.domain.model.SleepSource
import com.chronosflow.core.domain.model.SleepTrack
import com.chronosflow.core.domain.repository.SleepTrackRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ObserveSleepTrendUseCaseTest {
    private val repository: SleepTrackRepository = mockk()
    private val useCase = ObserveSleepTrendUseCase(repository)
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `maps repository tracks through deriveSleepTrends`() = runTest {
        val start = today.minusDays(2)
        every { repository.observeForDateRange(start, today) } returns flowOf(
            // Slept 23:00 (1380) -> 07:00 (420), wraps midnight = 8h.
            listOf(track(today, quality = 4, start = 1380, end = 420))
        )

        val trends = useCase(windowDays = 3, today = today).first()

        assertEquals(3, trends.nights.size)
        assertEquals(1, trends.loggedNights.size)
        assertEquals(4, trends.nights.last().quality)
        assertEquals(8 * 60, trends.nights.last().durationMinutes)
    }

    @Test
    fun `non-positive window short-circuits to empty`() = runTest {
        val trends = useCase(windowDays = 0, today = today).first()
        assertTrue(trends.isEmpty)
    }

    private fun track(date: LocalDate, quality: Int, start: Int?, end: Int?) = SleepTrack(
        id = "s-$date",
        date = date,
        plannedStartMinute = null,
        plannedEndMinute = null,
        actualStartMinute = start,
        actualEndMinute = end,
        sleepQuality = quality,
        windDownNotes = null,
        interruptedCount = 0,
        source = SleepSource.MANUAL
    )
}
