package com.chronosflow.core.domain.planner

import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.ReviewInsightType
import com.chronosflow.core.domain.model.TimeBlock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class DailyReviewCalculator @Inject constructor() {
    fun calculate(
        date: LocalDate,
        plannedBlocks: List<TimeBlock>,
        actualSegments: List<ActualTimeSegment>,
        timezone: ZoneId = ZoneId.systemDefault()
    ): DailyReviewSummary {
        val blocksForDate = plannedBlocks.filter { it.date == date }
        val segmentsForDate = actualSegments.filter { it.date == date }
        val plannedMinutes = blocksForDate.sumOf { it.durationMinutes }
        val actualMinutes = segmentsForDate.sumOf { it.durationMinutes() }
        val completedBlockIds = matchedBlockIds(blocksForDate, segmentsForDate, timezone)
        val completedBlockCount = blocksForDate.count { it.id in completedBlockIds }
        val missedBlocks = blocksForDate.filter { it.id !in completedBlockIds }
        val missedMinutes = missedBlocks.sumOf { it.durationMinutes }
        val driftMinutes = actualMinutes - plannedMinutes
        val insights = buildList {
            missedBlocks.forEach { block ->
                add(
                    ReviewInsight(
                        id = UUID.randomUUID().toString(),
                        type = ReviewInsightType.MISSED_BLOCK,
                        title = "Missed ${block.title}",
                        detail = "${block.durationMinutes} planned minutes were not matched to actual time.",
                        relatedBlockId = block.id,
                        severity = ReviewInsightSeverity.WARNING
                    )
                )
            }
            if (kotlin.math.abs(driftMinutes) >= 30) {
                add(
                    ReviewInsight(
                        id = UUID.randomUUID().toString(),
                        type = ReviewInsightType.DRIFT,
                        title = "Schedule drift",
                        detail = "Actual time differed from the plan by $driftMinutes minutes.",
                        severity = ReviewInsightSeverity.INFO
                    )
                )
            }
        }
        return DailyReviewSummary(
            date = date,
            plannedMinutes = plannedMinutes,
            actualMinutes = actualMinutes,
            missedMinutes = missedMinutes,
            driftMinutes = driftMinutes,
            completedBlockCount = completedBlockCount,
            missedBlockCount = missedBlocks.size,
            insights = insights
        )
    }

    private fun ActualTimeSegment.durationMinutes(): Int {
        val end = endInstant ?: startInstant
        return Duration.between(startInstant, end).toMinutes().coerceAtLeast(0).toInt()
    }

    private fun matchedBlockIds(
        blocks: List<TimeBlock>,
        segments: List<ActualTimeSegment>,
        timezone: ZoneId
    ): Set<String> {
        val directMatches = segments.mapNotNull { it.blockId }.toMutableSet()
        val unmatchedSegments = segments.filter { it.blockId == null && it.endInstant != null }
        blocks.forEach { block ->
            if (block.id in directMatches) return@forEach
            if (unmatchedSegments.any { segment -> block.overlaps(segment, timezone) }) {
                directMatches += block.id
            }
        }
        return directMatches
    }

    private fun TimeBlock.overlaps(segment: ActualTimeSegment, timezone: ZoneId): Boolean {
        val blockStart = date.atStartOfDay(timezone)
            .plusMinutes(startMinuteOfDay.toLong())
            .toInstant()
        val blockEnd = blockStart.plus(durationMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES)
        val segmentEnd = segment.endInstant ?: return false
        return blockStart < segmentEnd && segment.startInstant < blockEnd
    }
}
