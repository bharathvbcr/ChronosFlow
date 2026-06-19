package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import javax.inject.Inject

class MoodEnergyCheckInAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestAfterCheckIn(
        moodScore: Int,
        stressScore: Int,
        energyScore: Int,
        focusScore: Int,
        linkedBlockTitle: String? = null
    ): AssistNarrative {
        val baseline = localCoaching(
            moodScore = moodScore,
            stressScore = stressScore,
            energyScore = energyScore,
            focusScore = focusScore,
            linkedBlockTitle = linkedBlockTitle
        )
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(
                moodScore = moodScore,
                stressScore = stressScore,
                energyScore = energyScore,
                focusScore = focusScore,
                linkedBlockTitle = linkedBlockTitle,
                baselineHeadline = baseline.headline,
                baselineNextStep = baseline.nextStep
            )
        )
        return generation.text?.let { raw ->
            parseAssistNarrative(
                text = raw,
                source = generation.source,
                fallbackHeadline = baseline.headline,
                fallbackNextStep = baseline.nextStep
            )
        } ?: baseline
    }

    private fun buildPrompt(
        moodScore: Int,
        stressScore: Int,
        energyScore: Int,
        focusScore: Int,
        linkedBlockTitle: String?,
        baselineHeadline: String,
        baselineNextStep: String
    ): String = buildString {
        appendLine("Write a short mood and energy coaching tip for ChronosFlow after a check-in.")
        appendLine("Return one line as headline|nextStep.")
        appendLine("Keep each part under 120 characters. No medical advice or diagnosis.")
        appendLine("Mood: $moodScore/5")
        appendLine("Stress: $stressScore/5")
        appendLine("Energy: $energyScore/5")
        appendLine("Focus readiness: $focusScore/5")
        appendLine("Linked block: ${linkedBlockTitle ?: "none"}")
        appendLine("Baseline headline: $baselineHeadline")
        appendLine("Baseline next step: $baselineNextStep")
    }

    private fun localCoaching(
        moodScore: Int,
        stressScore: Int,
        energyScore: Int,
        focusScore: Int,
        linkedBlockTitle: String?
    ): AssistNarrative {
        val headline = when {
            stressScore >= 4 && energyScore <= 2 ->
                "Stress is high and energy is low — protect recovery before pushing harder."
            moodScore <= 2 && focusScore <= 2 ->
                "Mood and focus are both low — shrink the next commitment."
            energyScore >= 4 && focusScore >= 4 ->
                "Energy and focus look strong — use the next block for protected work."
            linkedBlockTitle != null ->
                "Check-in saved for $linkedBlockTitle — adjust the session if scores shifted."
            else -> "Check-in saved — match the next block to how you feel right now."
        }
        val nextStep = when {
            stressScore >= 4 -> "Take a 5-minute reset, then pick one small win for the next block."
            energyScore <= 2 -> "Shorten the next block or swap to a lighter category."
            focusScore >= 4 && energyScore >= 3 ->
                "Start focus on the highest-priority block while attention is available."
            else -> "Glance at today's plan and pick the block that fits this energy level."
        }
        return AssistNarrative(headline, nextStep, com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource.LOCAL)
    }
}
