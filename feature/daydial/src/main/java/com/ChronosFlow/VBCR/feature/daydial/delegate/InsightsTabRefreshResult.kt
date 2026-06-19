package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.ai.AssistDigest
import com.ChronosFlow.VBCR.core.ai.InsightRecommendation
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistUiSnapshot
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight

data class InsightsTabRefreshResult(
    val reviewInsights: List<ReviewInsight>,
    val recommendations: List<InsightRecommendation>,
    val assistSnapshot: GenAiAssistUiSnapshot?,
    val digest: AssistDigest? = null
)
