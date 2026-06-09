package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource

data class AssistNarrative(
    val headline: String,
    val nextStep: String,
    val source: AssistGenAiSource
)

internal fun parseAssistNarrative(
    text: String,
    source: AssistGenAiSource,
    fallbackHeadline: String,
    fallbackNextStep: String
): AssistNarrative {
    val line = text.lineSequence()
        .map { it.trim().trim('-', '*') }
        .firstOrNull { it.isNotBlank() && it.contains('|') }
        ?: return AssistNarrative(fallbackHeadline, fallbackNextStep, AssistGenAiSource.LOCAL)
    val parts = line.split("|", limit = 2).map { it.trim() }
    val headline = parts.getOrNull(0).orEmpty().ifBlank { fallbackHeadline }
    val nextStep = parts.getOrNull(1).orEmpty().ifBlank { fallbackNextStep }
    return AssistNarrative(headline, nextStep, source)
}
