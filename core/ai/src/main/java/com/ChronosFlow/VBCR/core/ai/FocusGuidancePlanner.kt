package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import javax.inject.Inject

class FocusGuidancePlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestGuidance(
        focusTitle: String,
        linkedBlockId: String?,
        isRunning: Boolean,
        isPaused: Boolean,
        timeLeft: Int,
        totalSeconds: Int,
        nextBlockTitle: String? = null,
        moodScore: Int? = null,
        energyScore: Int? = null
    ): AssistNarrative {
        val baseline = localGuidance(
            focusTitle = focusTitle,
            linkedBlockId = linkedBlockId,
            isRunning = isRunning,
            isPaused = isPaused,
            timeLeft = timeLeft,
            totalSeconds = totalSeconds
        )
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(
                focusTitle = focusTitle,
                linkedBlockId = linkedBlockId,
                isRunning = isRunning,
                isPaused = isPaused,
                timeLeft = timeLeft,
                totalSeconds = totalSeconds,
                nextBlockTitle = nextBlockTitle,
                moodScore = moodScore,
                energyScore = energyScore,
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
        focusTitle: String,
        linkedBlockId: String?,
        isRunning: Boolean,
        isPaused: Boolean,
        timeLeft: Int,
        totalSeconds: Int,
        nextBlockTitle: String?,
        moodScore: Int?,
        energyScore: Int?,
        baselineHeadline: String,
        baselineNextStep: String
    ): String = buildString {
        appendLine("Write focus coaching for ChronosFlow.")
        appendLine("Return one line as headline|nextStep.")
        appendLine("Keep each part under 120 characters. No medical advice.")
        appendLine("Focus title: $focusTitle")
        appendLine("Linked block: ${linkedBlockId ?: "standalone"}")
        appendLine("Running: $isRunning")
        appendLine("Paused: $isPaused")
        appendLine("Time left seconds: $timeLeft")
        appendLine("Total seconds: $totalSeconds")
        appendLine("Suggested next block: ${nextBlockTitle ?: "none"}")
        moodScore?.let { appendLine("Mood score: $it") }
        energyScore?.let { appendLine("Energy score: $it") }
        appendLine("Baseline headline: $baselineHeadline")
        appendLine("Baseline next step: $baselineNextStep")
    }

    private fun localGuidance(
        focusTitle: String,
        linkedBlockId: String?,
        isRunning: Boolean,
        isPaused: Boolean,
        timeLeft: Int,
        totalSeconds: Int
    ): AssistNarrative {
        val headline = when {
            isPaused -> "$focusTitle is paused with ${timeLeft / 60} minutes remaining."
            isRunning -> "$focusTitle is active and ${(timeLeft / 60).coerceAtLeast(1)} minutes remain."
            linkedBlockId != null -> "This focus session is linked to a DayDial block."
            else -> "Focus mode is ready for the next protected work block."
        }
        val nextStep = when {
            isPaused -> "Resume before context fades so the current block still lands cleanly."
            isRunning && timeLeft <= (totalSeconds / 4).coerceAtLeast(60) ->
                "Finish the current pass, then use the last few minutes to capture follow-up tasks."
            linkedBlockId != null ->
                "Start this linked session to keep actual-time logging aligned with DayDial."
            else ->
                "Launch the timer when the next priority is clear and notifications are under control."
        }
        return AssistNarrative(
            headline = headline,
            nextStep = nextStep,
            source = com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource.LOCAL
        )
    }
}
