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
        // A review only marks THIS day completed — a summary built for a different date must not
        // flip this plan's status, so mismatched reviews are ignored here.
        val reviewForDate = review?.takeIf { it.date == date }
        val status = when {
            reviewForDate != null -> DayPlanStatus.COMPLETED
            dayBlocks.isEmpty() -> DayPlanStatus.DRAFT
            else -> DayPlanStatus.PLANNED
        }
        return DayPlan(
            date = date,
            timezone = timezone,
            status = status,
            blocks = dayBlocks,
            conflicts = conflictDetectionEngine.detect(dayBlocks),
            review = reviewForDate
        )
    }
}
