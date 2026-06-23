package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.domain.model.ActualTimeSegment
import com.ChronosFlow.VBCR.core.domain.model.FocusSession
import com.ChronosFlow.VBCR.core.domain.model.MoodEnergyCheckIn
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class EnergyInsight(
    val type: ReviewInsightType,
    val title: String,
    val detail: String,
    val confidence: Float,
    val dataPoints: Int,
    val source: AssistGenAiSource = AssistGenAiSource.LOCAL
)

data class DeepWorkCandidate(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val score: Int,
    val reason: String,
    val confidence: Float
)

@Singleton
class EnergyCorrelationEngine @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    fun analyze(
        blocks: List<TimeBlock>,
        actualSegments: List<ActualTimeSegment>,
        focusSessions: List<FocusSession>,
        checkIns: List<MoodEnergyCheckIn>,
        habitCompletionRate: Float = 0f,
        medicationAdherenceRate: Float = 1f,
        date: LocalDate = LocalDate.now()
    ): List<EnergyInsight> {
        if (checkIns.size < 2 && focusSessions.isEmpty()) {
            return listOf(
                EnergyInsight(
                    type = ReviewInsightType.ENERGY_PEAK,
                    title = "Not enough data yet",
                    detail = "Log mood/energy check-ins and complete focus sessions to unlock insights.",
                    confidence = 0.2f,
                    dataPoints = checkIns.size + focusSessions.size
                )
            )
        }

        val insights = mutableListOf<EnergyInsight>()
        val peakHour = peakEnergyHour(checkIns)
        if (peakHour != null) {
            insights += EnergyInsight(
                type = ReviewInsightType.ENERGY_PEAK,
                title = "Energy tends to peak around ${formatHour(peakHour)}",
                detail = "Based on ${checkIns.size} check-ins in the last period.",
                confidence = (checkIns.size / 7f).coerceIn(0.35f, 0.9f),
                dataPoints = checkIns.size
            )
        }

        val planned = blocks.sumOf { it.durationMinutes }
        val actual = actualSegments.sumOf { segment ->
            val start = segment.startInstant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            val end = segment.endInstant?.atZone(java.time.ZoneId.systemDefault())?.toLocalTime()
            if (end != null) {
                ((end.toSecondOfDay() - start.toSecondOfDay()) / 60).coerceAtLeast(0)
            } else 0
        }
        if (planned > 0 && actual < planned * 0.75) {
            insights += EnergyInsight(
                type = ReviewInsightType.PLANNING_OPTIMISM,
                title = "Plans often run longer than actual focus",
                detail = "Actual tracked time is about ${(actual * 100L / planned)}% of planned blocks.",
                confidence = 0.55f,
                dataPoints = blocks.size
            )
        }

        if (medicationAdherenceRate < 0.8f) {
            insights += EnergyInsight(
                type = ReviewInsightType.MEDICATION_PATTERN,
                title = "Medication adherence needs attention",
                detail = "Recent adherence is ${(medicationAdherenceRate * 100).toInt()}%.",
                confidence = 0.7f,
                dataPoints = 1
            )
        }

        if (habitCompletionRate in 0.01f..0.99f) {
            insights += EnergyInsight(
                type = ReviewInsightType.HABIT_CORRELATION,
                title = "Habit completion correlates with afternoon energy",
                detail = "Days with morning habits completed show higher afternoon energy scores.",
                confidence = (habitCompletionRate * 0.6f + 0.2f).coerceAtMost(0.85f),
                dataPoints = checkIns.size
            )
        }

        return insights
    }

    suspend fun analyzeWithAssist(
        blocks: List<TimeBlock>,
        actualSegments: List<ActualTimeSegment>,
        focusSessions: List<FocusSession>,
        checkIns: List<MoodEnergyCheckIn>,
        habitCompletionRate: Float = 0f,
        medicationAdherenceRate: Float = 1f,
        date: LocalDate = LocalDate.now()
    ): List<EnergyInsight> {
        val baseline = analyze(
            blocks = blocks,
            actualSegments = actualSegments,
            focusSessions = focusSessions,
            checkIns = checkIns,
            habitCompletionRate = habitCompletionRate,
            medicationAdherenceRate = medicationAdherenceRate,
            date = date
        )
        val generation = genAiAssistCoordinator.generateAssistText(
            buildAssistPrompt(
                baseline = baseline,
                blocks = blocks,
                actualSegments = actualSegments,
                focusSessions = focusSessions,
                checkIns = checkIns,
                habitCompletionRate = habitCompletionRate,
                medicationAdherenceRate = medicationAdherenceRate
            )
        )
        val parsed = generation.text?.let { parseAssistInsights(it, generation.source) }.orEmpty()
        if (parsed.isEmpty()) return baseline
        val merged = (baseline + parsed).distinctBy { it.type.name + it.title }.take(5)
        return merged
    }

    fun deepWorkCandidates(
        blocks: List<TimeBlock>,
        checkIns: List<MoodEnergyCheckIn>,
        detector: DeepWorkWindowDetector = DeepWorkWindowDetector()
    ): List<DeepWorkCandidate> {
        val windows = detector.detect(blocks)
        val peakHour = peakEnergyHour(checkIns)
        return windows.map { window ->
            val energyBoost = if (peakHour != null && window.startMinuteOfDay / 60 == peakHour) 15 else 0
            val confidence = ((checkIns.size + 2) / 10f).coerceIn(0.3f, 0.95f)
            DeepWorkCandidate(
                startMinuteOfDay = window.startMinuteOfDay,
                endMinuteOfDay = window.endMinuteOfDay,
                score = window.score + energyBoost,
                reason = window.reason,
                confidence = confidence
            )
        }.sortedByDescending { it.score }
    }

    fun toReviewInsights(energyInsights: List<EnergyInsight>, date: LocalDate): List<ReviewInsight> =
        energyInsights.map { insight ->
            ReviewInsight(
                id = "${insight.type.name}-$date",
                type = insight.type,
                title = insight.title,
                detail = insight.detail,
                relatedBlockId = null,
                severity = ReviewInsightSeverity.INFO,
                assistSource = insight.source.name
            )
        }

    private fun peakEnergyHour(checkIns: List<MoodEnergyCheckIn>): Int? {
        if (checkIns.isEmpty()) return null
        return checkIns
            .groupBy { it.recordedAt.hour }
            .maxByOrNull { (_, entries) -> entries.map { it.energyScore }.average() }
            ?.key
    }

    private fun buildAssistPrompt(
        baseline: List<EnergyInsight>,
        blocks: List<TimeBlock>,
        actualSegments: List<ActualTimeSegment>,
        focusSessions: List<FocusSession>,
        checkIns: List<MoodEnergyCheckIn>,
        habitCompletionRate: Float,
        medicationAdherenceRate: Float
    ): String = buildString {
        appendLine("Add up to two energy review insights for ChronosFlow.")
        appendLine("Return one insight per line as type|title|detail.")
        appendLine("Allowed types: ENERGY_PEAK, PLANNING_OPTIMISM, MEDICATION_PATTERN, HABIT_CORRELATION.")
        appendLine("Use only supplied metrics. No medical advice.")
        appendLine("Planned minutes: ${blocks.sumOf { it.durationMinutes }}")
        appendLine("Actual segments: ${actualSegments.size}")
        appendLine("Check-ins: ${checkIns.size}")
        appendLine("Focus sessions: ${focusSessions.size}")
        appendLine("Habit completion rate: $habitCompletionRate")
        appendLine("Medication adherence rate: $medicationAdherenceRate")
        baseline.forEach { insight ->
            appendLine("Baseline: ${insight.type.name}|${insight.title}|${insight.detail}")
        }
    }

    private fun parseAssistInsights(text: String, source: AssistGenAiSource): List<EnergyInsight> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 2 }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                val typeRaw = parts.getOrNull(0).orEmpty().uppercase(Locale.getDefault())
                val title = parts.getOrNull(1).orEmpty()
                val detail = parts.drop(2).joinToString("|").ifBlank { return@mapNotNull null }
                val type = runCatching { ReviewInsightType.valueOf(typeRaw) }.getOrNull() ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                EnergyInsight(
                    type = type,
                    title = title,
                    detail = detail,
                    confidence = 0.65f,
                    dataPoints = 1,
                    source = source
                )
            }
            .take(2)
            .toList()
    }

    private fun formatHour(hour: Int): String {
        val normalized = hour % 24
        val suffix = if (normalized < 12) "AM" else "PM"
        val display = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        return "$display $suffix"
    }
}
