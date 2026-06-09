package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource

data class PlanAssistExplanation(
    val text: String,
    val source: AssistGenAiSource
)
