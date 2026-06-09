package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.AssistGenAiSource
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.domain.model.ReviewInsight
import com.chronosflow.core.domain.model.ReviewInsightSeverity
import com.chronosflow.core.domain.model.ReviewInsightType
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

class DeepWorkAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val energyCorrelationEngine: EnergyCorrelationEngine
) {
    suspend fun suggestInsight(
        blocks: List<com.chronosflow.core.domain.model.TimeBlock>,
        checkIns: List<com.chronosflow.core.domain.model.MoodEnergyCheckIn>,
        date: LocalDate
    ): ReviewInsight? {
        val candidate = energyCorrelationEngine
            .deepWorkCandidates(blocks = blocks, checkIns = checkIns)
            .firstOrNull()
            ?: return null
        val baseline = localInsight(candidate, date)
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(candidate, checkIns.size, baseline)
        )
        val parsed = generation.text?.let { parseInsightLine(it, generation.source, date) }
        return parsed ?: baseline.copy(assistSource = generation.source.name)
    }

    private fun buildPrompt(
        candidate: DeepWorkCandidate,
        checkInCount: Int,
        baseline: ReviewInsight
    ): String = buildString {
        appendLine("Write one deep-work window insight for ChronosFlow.")
        appendLine("Return one line as title|detail.")
        appendLine("No medical advice. Use only supplied timing.")
        appendLine("Window: ${formatMinute(candidate.startMinuteOfDay)} to ${formatMinute(candidate.endMinuteOfDay)}")
        appendLine("Score: ${candidate.score}")
        appendLine("Reason: ${candidate.reason}")
        appendLine("Check-ins: $checkInCount")
        appendLine("Baseline title: ${baseline.title}")
        appendLine("Baseline detail: ${baseline.detail}")
    }

    private fun parseInsightLine(
        text: String,
        source: AssistGenAiSource,
        date: LocalDate
    ): ReviewInsight? {
        val line = text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .firstOrNull { it.isNotBlank() && it.contains('|') }
            ?: return null
        val parts = line.split("|", limit = 2).map { it.trim() }
        val title = parts.getOrNull(0).orEmpty()
        val detail = parts.getOrNull(1).orEmpty()
        if (title.isBlank() || detail.isBlank()) return null
        return ReviewInsight(
            id = "deep-work-$date",
            type = ReviewInsightType.DEEP_WORK_WINDOW,
            title = title,
            detail = detail,
            severity = ReviewInsightSeverity.INFO,
            assistSource = source.name
        )
    }

    private fun localInsight(candidate: DeepWorkCandidate, date: LocalDate): ReviewInsight {
        val start = formatMinute(candidate.startMinuteOfDay)
        val end = formatMinute(candidate.endMinuteOfDay)
        return ReviewInsight(
            id = "deep-work-$date",
            type = ReviewInsightType.DEEP_WORK_WINDOW,
            title = "Protect a deep-work window around $start",
            detail = "$start–$end is open for ${(candidate.endMinuteOfDay - candidate.startMinuteOfDay).coerceAtLeast(1)} minutes. ${candidate.reason}",
            severity = ReviewInsightSeverity.INFO,
            assistSource = AssistGenAiSource.LOCAL.name
        )
    }

    private fun formatMinute(minuteOfDay: Int): String {
        val normalized = ((minuteOfDay % 1440) + 1440) % 1440
        val hour = normalized / 60
        val minute = normalized % 60
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, suffix)
    }
}
