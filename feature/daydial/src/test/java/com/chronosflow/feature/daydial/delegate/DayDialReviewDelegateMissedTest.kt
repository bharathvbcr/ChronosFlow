package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.LogActualTimeUseCase
import com.chronosflow.feature.daydial.testManualMissedBlockRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayDialReviewDelegateMissedTest {
    @Test
    fun `markBlockComplete clears manual missed for block date`() = runTest {
        val today = LocalDate.parse("2026-05-25")
        val registry = testManualMissedBlockRegistry()
        registry.markMissed("block-1", today)

        val repository = mockk<TimeBlockRepository>()
        val block = sampleBlock("block-1", today)
        coEvery { repository.getTimeBlockById("block-1") } returns block
        coEvery { repository.saveTimeBlock(any()) } returns Unit

        val logActualTimeUseCase = mockk<LogActualTimeUseCase>(relaxed = true)
        coEvery { logActualTimeUseCase(any()) } returns Unit

        val delegate = DayDialReviewDelegate(
            repository = repository,
            reviewRepository = mockk(relaxed = true),
            moodEnergyRepository = mockk(relaxed = true),
            habitRepository = mockk(relaxed = true),
            medicationRepository = mockk(relaxed = true),
            taskRepository = mockk(relaxed = true),
            taskScheduleRepository = mockk(relaxed = true),
            completeDailyReviewUseCase = mockk(relaxed = true),
            completeTaskOccurrenceUseCase = mockk(relaxed = true),
            logActualTimeUseCase = logActualTimeUseCase,
            syncRecurringTaskAlarmsUseCase = mockk(relaxed = true),
            alarmScheduler = mockk(relaxed = true),
            alarmRequestRepository = mockk(relaxed = true),
            energyCorrelationEngine = mockk(relaxed = true),
            insightsRecommendationsPlanner = mockk(relaxed = true),
            deepWorkAssistPlanner = mockk(relaxed = true),
            genAiAssistCoordinator = mockk(relaxed = true),
            manualMissedBlockRegistry = registry
        )

        delegate.markBlockComplete(this, "block-1")
        advanceUntilIdle()
        assertFalse(registry.missedIdsForDate(today).contains("block-1"))
    }

    private fun sampleBlock(id: String, date: LocalDate): TimeBlock {
        val now = Instant.now()
        return TimeBlock(
            id = id,
            date = date,
            title = "Focus",
            category = "WORK",
            startMinuteOfDay = 600,
            durationMinutes = 25,
            timezone = ZoneId.systemDefault().id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "TEST",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            taskOccurrenceDate = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
