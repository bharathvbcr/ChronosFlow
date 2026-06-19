package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.repository.MoodEnergyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ObserveMoodEnergyTrendsUseCaseTest {
    private val repository: MoodEnergyRepository = mockk()
    private val useCase = ObserveMoodEnergyTrendsUseCase(repository)
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `maps observed check-ins through deriveMoodEnergyTrends`() = runTest {
        val start = today.minusDays(2)
        every { repository.observeForDateRange(start, today) } returns flowOf(
            listOf(
                checkIn(date = today, hour = 9, mood = 4, energy = 5),
                checkIn(date = today, hour = 9, mood = 2, energy = 3)
            )
        )

        val trends = useCase(windowDays = 3, today = today).first()

        assertEquals(1, trends.dailyAverages.size)
        val todayAvg = trends.dailyAverages.first { it.date == today }
        assertEquals(3f, todayAvg.avgMood, 0.001f)
        assertEquals(4f, todayAvg.avgEnergy, 0.001f)
        assertEquals(2, todayAvg.sampleCount)
    }

    @Test
    fun `non-positive window short-circuits to empty`() = runTest {
        val trends = useCase(windowDays = 0, today = today).first()
        assertTrue(trends.isEmpty)
    }

    private fun checkIn(date: LocalDate, hour: Int, mood: Int, energy: Int) = MoodEnergyCheckIn(
        id = "c-$date-$hour-$mood",
        blockId = null,
        moodScore = mood,
        stressScore = 2,
        energyScore = energy,
        focusScore = 3,
        notes = null,
        recordedAt = LocalDateTime.of(date, LocalTime.of(hour, 0)),
        checkInDate = date
    )
}
