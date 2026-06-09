package com.chronosflow.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class ActualTimeSegment(
    val id: String,
    val blockId: String?,
    val date: LocalDate,
    val startInstant: Instant,
    val endInstant: Instant?,
    val source: ActualTimeSource,
    val confidence: Float
)

enum class ActualTimeSource {
    FOCUS_SESSION,
    MANUAL_ENTRY,
    SYSTEM_INFERENCE,
    IMPORTED_CALENDAR
}

data class DailyReviewSummary(
    val date: LocalDate,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val driftMinutes: Int,
    val completedBlockCount: Int,
    val missedBlockCount: Int,
    val insights: List<ReviewInsight>
)

data class ReviewInsight(
    val id: String,
    val type: ReviewInsightType,
    val title: String,
    val detail: String,
    val relatedBlockId: String? = null,
    val severity: ReviewInsightSeverity = ReviewInsightSeverity.INFO,
    /** AssistGenAiSource name when insight was model-assisted; null for heuristic-only rows. */
    val assistSource: String? = null
)

enum class ReviewInsightType {
    MISSED_BLOCK,
    DRIFT,
    FOCUS_UNDERRUN,
    MEDICATION_ADHERENCE,
    HABIT_WINDOW,
    SCHEDULE_BALANCE,
    ENERGY_PEAK,
    DEEP_WORK_WINDOW,
    PLANNING_OPTIMISM,
    MEDICATION_PATTERN,
    HABIT_CORRELATION
}

enum class ReviewInsightSeverity {
    INFO,
    WARNING,
    CRITICAL
}
