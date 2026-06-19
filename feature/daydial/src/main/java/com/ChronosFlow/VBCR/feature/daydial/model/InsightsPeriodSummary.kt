package com.ChronosFlow.VBCR.feature.daydial.model

import com.ChronosFlow.VBCR.feature.daydial.DailyReview

/**
 * Aggregated execution metrics for a multi-day [InsightsPeriod]. Null on the Insights
 * tab means the selected-day live data is used instead (the DAY period).
 */
data class InsightsPeriodSummary(
    val review: DailyReview,
    val missedCount: Int,
    val categoryRows: List<InsightCategoryBreakdownRow>,
    val isLoading: Boolean = false
) {
    companion object {
        val Loading = InsightsPeriodSummary(
            review = DailyReview(plannedMinutes = 0, actualMinutes = 0, missedMinutes = 0, completedBlocks = 0),
            missedCount = 0,
            categoryRows = emptyList(),
            isLoading = true
        )
    }
}
