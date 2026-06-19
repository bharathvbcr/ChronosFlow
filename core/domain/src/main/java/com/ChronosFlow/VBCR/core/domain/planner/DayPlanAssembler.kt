package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.DayPlan
import com.ChronosFlow.VBCR.core.domain.model.DayPlanStatus
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class DayPlanAssembler @Inject constructor(
    private val conflictDetectionEngine: ConflictDetectionEngine
) {
    fun assemble(
        date: LocalDate,
        timezone: ZoneId,
        blocks: List<TimeBlock>,
        review: DailyReviewSummary? = null
    ): DayPlan {
        val dayBlocks = blocks.filter { it.date == date }.sortedBy { it.startMinuteOfDay }
        val status = when {
            review != null -> DayPlanStatus.COMPLETED
            dayBlocks.isEmpty() -> DayPlanStatus.DRAFT
            else -> DayPlanStatus.PLANNED
        }
        return DayPlan(
            date = date,
            timezone = timezone,
            status = status,
            blocks = dayBlocks,
            conflicts = conflictDetectionEngine.detect(dayBlocks),
            review = review
        )
    }
}
