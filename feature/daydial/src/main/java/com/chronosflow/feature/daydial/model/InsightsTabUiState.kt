package com.chronosflow.feature.daydial.model

import com.chronosflow.core.ai.AssistDigest
import com.chronosflow.core.ai.InsightRecommendation
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.domain.model.ReviewInsight

data class InsightsTabUiState(
    val reviewInsights: List<ReviewInsight> = emptyList(),
    val recommendations: List<InsightRecommendation> = emptyList(),
    val assistSnapshot: GenAiAssistUiSnapshot? = null,
    val digest: AssistDigest? = null,
    val isRefreshing: Boolean = false,
    val period: InsightsPeriod = InsightsPeriod.DAY,
    /** Aggregated metrics for [period]; null for DAY (the live selected-day data is used). */
    val periodSummary: InsightsPeriodSummary? = null
)
