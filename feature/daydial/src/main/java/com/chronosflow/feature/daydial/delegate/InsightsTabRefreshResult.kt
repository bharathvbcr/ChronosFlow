package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.ai.AssistDigest
import com.chronosflow.core.ai.InsightRecommendation
import com.chronosflow.core.ai.genai.GenAiAssistUiSnapshot
import com.chronosflow.core.domain.model.ReviewInsight

data class InsightsTabRefreshResult(
    val reviewInsights: List<ReviewInsight>,
    val recommendations: List<InsightRecommendation>,
    val assistSnapshot: GenAiAssistUiSnapshot?,
    val digest: AssistDigest? = null
)
