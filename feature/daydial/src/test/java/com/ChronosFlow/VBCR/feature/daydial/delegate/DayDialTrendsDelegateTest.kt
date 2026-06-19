package com.ChronosFlow.VBCR.feature.daydial.delegate

import app.cash.turbine.test
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.model.HabitDailyCompletion
import com.ChronosFlow.VBCR.core.domain.model.JournalEntry
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyTrends
import com.ChronosFlow.VBCR.core.domain.model.SleepTrendNight
import com.ChronosFlow.VBCR.core.domain.model.SleepTrends
import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncManager
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveAppUsageTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveHabitCompletionTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMedicationAdherenceTrendUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveMoodEnergyTrendsUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveRecentJournalEntriesUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveSleepTrendUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DayDialTrendsDelegateTest {
    private val observeMood: ObserveMoodEnergyTrendsUseCase = mockk()
    private val observeHabit: ObserveHabitCompletionTrendUseCase = mockk()
    private val observeMedication: ObserveMedicationAdherenceTrendUseCase = mockk()
    private val observeSleep: ObserveSleepTrendUseCase = mockk()
    private val observeJournal: ObserveRecentJournalEntriesUseCase = mockk()
    private val observeAppUsage: ObserveAppUsageTrendUseCase = mockk()
    private val screenTimeSyncManager: ScreenTimeSyncManager = mockk()
    private val delegate = DayDialTrendsDelegate(
        observeMood,
        observeHabit,
        observeMedication,
        observeSleep,
        observeJournal,
        observeAppUsage,
        screenTimeSyncManager
    )

    init {
        every { screenTimeSyncManager.observeFocusGoalMinutes() } returns flowOf(0)
        every { screenTimeSyncManager.observeTopDistractingApps(any(), any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun `combines all five reactive sources into one snapshot`() = runTest {
        every { observeMood(14, any()) } returns flowOf(MoodEnergyTrends())
        every { observeHabit(14, any()) } returns flowOf(listOf(habitDay()))
        every { observeMedication(14, any()) } returns flowOf(emptyList())
        every { observeSleep(14, any()) } returns flowOf(loggedSleep())
        every { observeJournal(14, any()) } returns flowOf(listOf(journalEntry("j1")))
        every { observeAppUsage(14, any()) } returns flowOf(listOf(screenDay()))

        delegate.observeTrends(14).test {
            val sections = awaitItem()
            assertEquals(listOf("j1"), sections.journalHistory.map { it.id })
            assertEquals(1, sections.habitTrend.size)
            assertFalse(sections.sleepTrend.isEmpty)
            assertEquals(120, sections.screenTimeTrend.sumOf { it.productiveMinutes })
            awaitComplete()
        }
    }

    @Test
    fun `re-emits when a reactive source updates`() = runTest {
        val sleepFlow = MutableStateFlow(SleepTrends())
        every { observeMood(14, any()) } returns flowOf(MoodEnergyTrends())
        every { observeHabit(14, any()) } returns flowOf(emptyList())
        every { observeMedication(14, any()) } returns flowOf(emptyList())
        every { observeSleep(14, any()) } returns sleepFlow
        every { observeJournal(14, any()) } returns flowOf(emptyList())
        every { observeAppUsage(14, any()) } returns flowOf(emptyList())

        delegate.observeTrends(14).test {
            assertTrue(awaitItem().sleepTrend.isEmpty)
            // Simulate a background Health Connect import landing while the tab is open.
            sleepFlow.value = loggedSleep()
            assertFalse(awaitItem().sleepTrend.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun loggedSleep() = SleepTrends(
        nights = listOf(
            SleepTrendNight(date = LocalDate.of(2026, 6, 11), quality = 4, durationMinutes = 480)
        )
    )

    private fun screenDay() = AppUsageDay(
        date = LocalDate.of(2026, 6, 11),
        productiveMinutes = 120,
        distractingMinutes = 45,
        neutralMinutes = 30
    )

    private fun habitDay() = HabitDailyCompletion(
        date = LocalDate.of(2026, 6, 11),
        completedCount = 1,
        missedCount = 0
    )

    private fun journalEntry(id: String) = JournalEntry(
        id = id,
        entryDate = LocalDate.of(2026, 6, 11),
        createdAt = Instant.parse("2026-06-11T19:00:00Z"),
        updatedAt = Instant.parse("2026-06-11T19:00:00Z"),
        body = "reflection"
    )
}
