package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.runtime.Immutable
import com.ChronosFlow.VBCR.core.ai.AssistDigest
import com.ChronosFlow.VBCR.core.ai.InsightRecommendation
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight

// @Immutable (stronger than @Stable) because the ViewModel only ever replaces this object — it is
// never mutated in place. This allows the Compose compiler to skip recomposition when the reference
// is unchanged, even though List<T> fields are otherwise considered unstable.
@Immutable
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
