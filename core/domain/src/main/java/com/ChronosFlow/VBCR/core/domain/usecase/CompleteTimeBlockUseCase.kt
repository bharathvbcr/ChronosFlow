package com.ChronosFlow.VBCR.core.domain.usecase

import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSource
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.repository.ReviewRepository
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Idempotently records a planned [TimeBlock] as completed by filling its full planned window as
 * actual time, so the daily review counts it done. Shared by the in-app "mark complete" action
 * (DayDialReviewDelegate) and the agent-facing completeTimeBlock AppFunction so both stay identical
 * and can't drift.
 */
class CompleteTimeBlockUseCase @Inject constructor(
    private val plannerService: PlannerService,
    private val reviewRepository: ReviewRepository
) {
    /**
     * @return true if actual time was logged; false if [block] already had actual time. The
     * already-logged case is a deliberate no-op so callers never append a duplicate segment that
     * would inflate the review (segments are keyed by random id and are not deduped on insert).
     */
    suspend operator fun invoke(block: TimeBlock): Boolean {
        if (block.actualStartMinuteOfDay != null) return false
        val startMinute = block.startMinuteOfDay
        val endMinute = (block.startMinuteOfDay + block.durationMinutes).coerceIn(0, 1440)
        plannerService.logActualWindow(block.id, startMinute, endMinute)
        val dayStart = block.date.atStartOfDay(ZoneId.systemDefault())
        reviewRepository.saveActualTimeSegment(
            ActualTimeSegment(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                date = block.date,
                startInstant = dayStart.plusMinutes(startMinute.toLong()).toInstant(),
                endInstant = dayStart.plusMinutes(endMinute.toLong()).toInstant(),
                source = ActualTimeSource.FOCUS_SESSION,
                confidence = 1f
            )
        )
        return true
    }
}
