package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource

data class PlanAssistExplanation(
    val text: String,
    val source: AssistGenAiSource
)
