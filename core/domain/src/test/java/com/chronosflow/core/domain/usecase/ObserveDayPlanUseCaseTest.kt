package com.chronosflow.core.domain.usecase

import app.cash.turbine.test
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.DayPlanStatus
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.ConflictDetectionEngine
import com.chronosflow.core.domain.planner.DayPlanAssembler
import com.chronosflow.core.domain.repository.ReviewRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ObserveDayPlanUseCaseTest {
    @Test
    fun `emits assembled day plan from time blocks and review repository`() = runTest {
        val date = LocalDate.of(2026, 5, 8)
        val block = timeBlock("block-1", date, 9 * 60, 60)
        val timeBlockRepository = mockk<TimeBlockRepository>()
        val reviewRepository = mockk<ReviewRepository>()
        every { timeBlockRepository.getTimeBlocksByDate(date) } returns flowOf(listOf(block))
        every { reviewRepository.observeDailyReview(date) } returns flowOf(null)
        val useCase = ObserveDayPlanUseCase(
            timeBlockRepository = timeBlockRepository,
            reviewRepository = reviewRepository,
            dayPlanAssembler = DayPlanAssembler(ConflictDetectionEngine())
        )

        useCase(date, ZoneId.of("America/Chicago")).test {
            val plan = awaitItem()
            assertEquals(date, plan.date)
            assertEquals(DayPlanStatus.PLANNED, plan.status)
            assertEquals(listOf(block), plan.blocks)
            assertEquals(emptyList<Any>(), plan.conflicts)
            awaitComplete()
        }
    }

    private fun timeBlock(
        id: String,
        date: LocalDate,
        startMinute: Int,
        durationMinutes: Int
    ): TimeBlock {
        val now = Instant.parse("2026-05-08T12:00:00Z")
        return TimeBlock(
            id = id,
            date = date,
            title = "Deep work",
            category = "FOCUS",
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            timezone = "UTC",
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
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = now,
            updatedAt = now
        )
    }
}
