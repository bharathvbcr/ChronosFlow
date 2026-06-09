package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate

internal object PlannerTestFixtures {
    val date: LocalDate = LocalDate.of(2025, 1, 1)
    val now: Instant = Instant.parse("2025-01-01T12:00:00Z")

    fun timeBlock(
        id: String = "block-1",
        startMinute: Int = 9 * 60,
        durationMinutes: Int = 60,
        date: LocalDate = PlannerTestFixtures.date,
        isLocked: Boolean = false,
        isProtected: Boolean = false
    ): TimeBlock = TimeBlock(
        id = id,
        date = date,
        title = "Test Block",
        category = "WORK",
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
        isLocked = isLocked,
        isProtected = isProtected,
        recurrenceRuleId = null,
        actualStartMinuteOfDay = null,
        actualEndMinuteOfDay = null,
        createdAt = now,
        updatedAt = now
    )

    fun dailyReviewSummary(): com.chronosflow.core.domain.model.DailyReviewSummary =
        com.chronosflow.core.domain.model.DailyReviewSummary(
            date = date,
            plannedMinutes = 0,
            actualMinutes = 0,
            missedMinutes = 0,
            driftMinutes = 0,
            completedBlockCount = 0,
            missedBlockCount = 0,
            insights = emptyList()
        )
}
