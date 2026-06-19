package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.model.isFocusSuggestionCandidate
import javax.inject.Inject

enum class FocusAssistSource {
    GEMINI_NANO,
    CLOUD_GEMINI,
    LOCAL
}

data class FocusNextBlockSuggestion(
    val id: String,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val reason: String,
    val source: FocusAssistSource
)

class FocusNextBlockPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestNextBlock(
        blocks: List<TimeBlock>,
        currentMinute: Int,
        moodScore: Int? = null,
        energyScore: Int? = null
    ): FocusNextBlockSuggestion? {
        val candidates = blocks.filter { it.isFocusSuggestionCandidate() }
        val baseline = findNextFocusBlock(candidates, currentMinute) ?: return null
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(candidates, currentMinute, moodScore, energyScore)
        )
        val source = generation.source.toFocusAssistSource()
        generation.text?.let { raw ->
            parseAiPick(raw, candidates, baseline, source)?.let { return it }
        }
        return baseline.copy(
            reason = localReason(baseline, currentMinute),
            source = FocusAssistSource.LOCAL
        )
    }

    private fun buildPrompt(
        blocks: List<TimeBlock>,
        currentMinute: Int,
        moodScore: Int?,
        energyScore: Int?
    ): String = buildString {
        appendLine("Pick the best next focus block from today's ChronosFlow schedule.")
        appendLine("Return one line as blockId|reason.")
        appendLine("Prefer protected deep-work blocks when energy is adequate.")
        appendLine("Current minute of day: $currentMinute")
        moodScore?.let { appendLine("Mood score (1-5): $it") }
        energyScore?.let { appendLine("Energy score (1-5): $it") }
        appendLine("Candidates:")
        blocks.sortedBy { it.startMinuteOfDay }.forEach { block ->
            appendLine(
                "${block.id}|${block.title}|${block.category}|${block.startMinuteOfDay}|${block.durationMinutes}"
            )
        }
    }

    private fun parseAiPick(
        text: String,
        blocks: List<TimeBlock>,
        baseline: FocusNextBlockSuggestion,
        source: FocusAssistSource
    ): FocusNextBlockSuggestion? {
        val line = text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .firstOrNull { it.isNotBlank() && it.contains('|') }
            ?: return null
        val parts = line.split("|", limit = 2).map { it.trim() }
        val blockId = parts.getOrNull(0).orEmpty()
        val reason = parts.getOrNull(1).orEmpty().ifBlank { defaultReasonFor(source) }
        val block = blocks.firstOrNull { it.id == blockId } ?: return null
        return FocusNextBlockSuggestion(
            id = block.id,
            title = block.title,
            category = block.category,
            startMinuteOfDay = block.startMinuteOfDay,
            durationMinutes = block.durationMinutes,
            reason = reason,
            source = source
        )
    }

    private fun localReason(suggestion: FocusNextBlockSuggestion, currentMinute: Int): String {
        return if (suggestion.startMinuteOfDay > currentMinute) {
            "Next scheduled block after ${formatMinute(currentMinute)}."
        } else {
            "Earliest remaining block on today's plan."
        }
    }

    private fun defaultReasonFor(source: FocusAssistSource): String = when (source) {
        FocusAssistSource.GEMINI_NANO -> "Suggested with Gemini Nano on-device."
        FocusAssistSource.CLOUD_GEMINI -> "Suggested with cloud Gemini."
        FocusAssistSource.LOCAL -> "Suggested locally."
    }

    private fun formatMinute(minute: Int): String {
        val normalized = ((minute % 1440) + 1440) % 1440
        val hour = normalized / 60
        val min = normalized % 60
        return "%02d:%02d".format(hour, min)
    }
}

fun findNextFocusBlock(
    blocks: List<TimeBlock>,
    currentMinute: Int
): FocusNextBlockSuggestion? {
    val sorted = blocks
        .filter { it.isFocusSuggestionCandidate() }
        .sortedBy { it.startMinuteOfDay }
    val upcoming = sorted.filter { it.startMinuteOfDay > currentMinute }.minByOrNull { it.startMinuteOfDay }
        ?: sorted.minByOrNull { it.startMinuteOfDay }
    return upcoming?.let {
        FocusNextBlockSuggestion(
            id = it.id,
            title = it.title,
            category = it.category,
            startMinuteOfDay = it.startMinuteOfDay,
            durationMinutes = it.durationMinutes,
            reason = "",
            source = FocusAssistSource.LOCAL
        )
    }
}

private fun AssistGenAiSource.toFocusAssistSource(): FocusAssistSource = when (this) {
    AssistGenAiSource.GEMINI_NANO -> FocusAssistSource.GEMINI_NANO
    AssistGenAiSource.CLOUD_GEMINI -> FocusAssistSource.CLOUD_GEMINI
    AssistGenAiSource.LOCAL -> FocusAssistSource.LOCAL
}
