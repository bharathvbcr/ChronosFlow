package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.HabitEvent
import com.ChronosFlow.VBCR.core.domain.model.HabitEventType
import com.ChronosFlow.VBCR.core.domain.repository.HabitRepository
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

class ObserveHabitCompletionTrendUseCaseTest {
    private val repository: HabitRepository = mockk()
    private val useCase = ObserveHabitCompletionTrendUseCase(repository)
    private val today = LocalDate.of(2026, 6, 11)

    @Test
    fun `maps observed events through deriveHabitCompletionTrend`() = runTest {
        val start = today.minusDays(2)
        every { repository.observeHabitEventsBetween(start, today) } returns flowOf(
            listOf(
                event(today, HabitEventType.COMPLETED),
                event(today, HabitEventType.MISSED),
                event(today.minusDays(2), HabitEventType.COMPLETED)
            )
        )

        val trend = useCase(windowDays = 3, today = today).first()

        assertEquals(3, trend.size)
        assertEquals(today.minusDays(2), trend.first().date)
        assertEquals(1, trend.first().completedCount)
        assertEquals(1, trend.last().completedCount)
        assertEquals(1, trend.last().missedCount)
    }

    @Test
    fun `non-positive window short-circuits to empty`() = runTest {
        val trend = useCase(windowDays = 0, today = today).first()
        assertTrue(trend.isEmpty())
    }

    private fun event(date: LocalDate, type: HabitEventType) = HabitEvent(
        id = "h-$date-$type",
        habitId = "habit-1",
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-06-11T00:00:00Z"),
        reason = null,
        startMinuteOfDay = null,
        endMinuteOfDay = null
    )
}
