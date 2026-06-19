package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.HabitAnalytics
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

internal object UseCaseTestFixtures {
    val date: LocalDate = LocalDate.of(2026, 5, 27)
    val now: Instant = Instant.parse("2026-05-27T14:30:00Z")

    fun task(
        id: String = "task-1",
        title: String = "Write proposal",
        isCompleted: Boolean = false
    ): Task = Task(
        id = id,
        title = title,
        description = null,
        isCompleted = isCompleted,
        priority = 1,
        dueDate = null,
        createdAt = now,
        updatedAt = now
    )

    fun habit(
        id: String,
        title: String,
        isActive: Boolean = true,
        streakCount: Int = 0,
        analytics: HabitAnalytics = HabitAnalytics(),
        lastCompletedDate: LocalDate? = date
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = 7 * 60,
        windowEndMinute = 8 * 60,
        difficulty = 2,
        isBundled = false,
        streakCount = streakCount,
        lastCompletedDate = lastCompletedDate,
        isActive = isActive,
        analytics = analytics
    )

    fun timeBlock(
        id: String = "block-1",
        startMinute: Int = 9 * 60,
        durationMinutes: Int = 60
    ): TimeBlock = TimeBlock(
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

    fun actualTimeSegment(): ActualTimeSegment = ActualTimeSegment(
        id = "actual-1",
        blockId = "block-1",
        date = date,
        startInstant = now,
        endInstant = now.plusSeconds(30 * 60),
        source = ActualTimeSource.MANUAL_ENTRY,
        confidence = 1f
    )

    fun dailyReviewSummary(): DailyReviewSummary = DailyReviewSummary(
        date = date,
        plannedMinutes = 240,
        actualMinutes = 210,
        missedMinutes = 30,
        driftMinutes = 15,
        completedBlockCount = 3,
        missedBlockCount = 1,
        insights = listOf(
            ReviewInsight(
                id = "insight-1",
                type = ReviewInsightType.DRIFT,
                title = "Late start",
                detail = "Deep work started later than planned."
            )
        )
    )

    fun medicationAlarmRequest(
        medicationPlanId: String? = "medication-1"
    ): AlarmRequest = AlarmRequest(
        id = "alarm-1",
        type = AlarmRequestType.MEDICATION,
        scheduledFor = now.plusSeconds(60 * 60),
        title = "Medication",
        message = "Take medication",
        medicationPlanId = medicationPlanId,
        blockId = null,
        reliability = AlarmReliability.EXACT,
        deliveryState = AlarmDeliveryState.PENDING,
        createdAt = now,
        updatedAt = now
    )

    fun medicationPlan(
        id: String = "medication-1",
        name: String = "Vitamin D",
        missedCount: Int = 0,
        isActive: Boolean = true
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 27, 8, 0),
        endAt = null,
        reminderMinuteOfDay = 8 * 60,
        takeWithFood = false,
        missedCount = missedCount,
        refillNeededAfterDoses = null,
        isActive = isActive
    )
}
