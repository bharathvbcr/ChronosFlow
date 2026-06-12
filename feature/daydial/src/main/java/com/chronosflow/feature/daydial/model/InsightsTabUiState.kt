package com.chronosflow.feature.daydial.model

import com.chronosflow.core.ai.InsightRecommendation
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.feature.daydial.delegate.CompanionTrendSections

data class InsightsTabUiState(
    val reviewInsights: List<ReviewInsight> = emptyList(),
    val recommendations: List<InsightRecommendation> = emptyList(),
    val assistSnapshot: GenAiAssistUiSnapshot? = null,
    val isRefreshing: Boolean = false,
    val trendRangeDays: Int = 14,
    val trends: CompanionTrendSections = CompanionTrendSections()
)
