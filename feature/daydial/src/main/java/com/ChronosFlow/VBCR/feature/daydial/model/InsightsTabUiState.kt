package com.ChronosFlow.VBCR.feature.daydial.model

import com.ChronosFlow.VBCR.core.ai.AssistDigest
import com.ChronosFlow.VBCR.core.ai.InsightRecommendation
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight

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
