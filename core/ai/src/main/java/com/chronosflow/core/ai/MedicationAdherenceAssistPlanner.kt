package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.toRoutineAssistSource
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationPlan
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class MedicationAdherenceAssistResult(
    val medicationPlanId: String,
    val suggestedReminderMinute: Int,
    val reason: String,
    val source: RoutineAssistSource
)

/**
 * Passive adherence helper for medication plans, mirroring the habit-repair
 * pattern: flags doses that slipped today plus reminders that are routinely
 * acknowledged late, and suggests a better reminder time. Suggestions are
 * limited to reminder timing — never dosing or other medical advice.
 */
class MedicationAdherenceAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestAdjustments(
        plans: List<MedicationPlan>,
        today: LocalDate,
        currentMinute: Int
    ): List<MedicationAdherenceAssistResult> {
        val flagged = plans.needingAdherenceHelp(today, currentMinute)
        if (flagged.isEmpty()) return emptyList()
        val baseline = flagged.map { it.toLocalAdherenceResult(today, currentMinute) }
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(flagged, today, currentMinute)
        )
        val source = generation.source.toRoutineAssistSource()
        val parsed = generation.text?.let { parseAdjustments(it, flagged, source) }.orEmpty()
        if (parsed.isNotEmpty()) {
            return parsed.take(3)
        }
        return baseline.take(3)
    }

    private fun buildPrompt(
        plans: List<MedicationPlan>,
        today: LocalDate,
        currentMinute: Int
    ): String = buildString {
        appendLine("Suggest reminder-time adjustments for ChronosFlow medication plans.")
        appendLine("Return one line per plan as planId|reminderMinute|reason.")
        appendLine("Only adjust reminder timing. Never give dosing or medical advice.")
        appendLine("Keep reminderMinute within the same day (0-1439).")
        appendLine("Current minute: $currentMinute")
        plans.forEach { plan ->
            val takenMinutes = plan.takenMinutesOfDay(today)
            appendLine(
                "${plan.id}|${plan.name}|reminder=${plan.reminderMinuteOfDay}|" +
                    "ackToday=${plan.acknowledgedToday(today)}|recentTakenMinutes=${takenMinutes.joinToString(",")}"
            )
        }
    }

    private fun parseAdjustments(
        text: String,
        plans: List<MedicationPlan>,
        source: RoutineAssistSource
    ): List<MedicationAdherenceAssistResult> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 2 }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                val planId = parts.getOrNull(0).orEmpty()
                val plan = plans.firstOrNull { it.id == planId } ?: return@mapNotNull null
                val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 1439)
                    ?: return@mapNotNull null
                val reason = parts.drop(2).joinToString("|").ifBlank { defaultAdherenceReason(source) }
                MedicationAdherenceAssistResult(
                    medicationPlanId = plan.id,
                    suggestedReminderMinute = minute,
                    reason = reason,
                    source = source
                )
            }
            .distinctBy { it.medicationPlanId }
            .toList()
    }

    private fun defaultAdherenceReason(source: RoutineAssistSource): String = when (source) {
        RoutineAssistSource.GEMINI_NANO -> "Reminder adjustment suggested with Gemini Nano."
        RoutineAssistSource.CLOUD_GEMINI -> "Reminder adjustment suggested with cloud Gemini."
        RoutineAssistSource.LOCAL -> "Reminder adjustment suggested locally."
    }
}

private const val MISSED_GRACE_MINUTES = 30
private const val LATE_TAKE_THRESHOLD_MINUTES = 45
private const val LATE_TAKE_MIN_OCCURRENCES = 3

fun List<MedicationPlan>.needingAdherenceHelp(
    today: LocalDate,
    currentMinute: Int
): List<MedicationPlan> {
    return filter { plan ->
        plan.isActive &&
            (plan.missedToday(today, currentMinute) || plan.lateTakePattern(today))
    }.sortedBy { it.reminderMinuteOfDay }
}

fun MedicationPlan.missedToday(today: LocalDate, currentMinute: Int): Boolean =
    !acknowledgedToday(today) && currentMinute > reminderMinuteOfDay + MISSED_GRACE_MINUTES

fun MedicationPlan.acknowledgedToday(today: LocalDate): Boolean =
    recentDoseEvents.any { event ->
        event.eventDate == today &&
            (event.type == MedicationDoseEventType.TAKEN || event.type == MedicationDoseEventType.SKIPPED)
    }

/** True when recent doses are routinely logged well after the scheduled reminder. */
fun MedicationPlan.lateTakePattern(today: LocalDate): Boolean =
    lateTakenMinutes(today).size >= LATE_TAKE_MIN_OCCURRENCES

private fun MedicationPlan.lateTakenMinutes(today: LocalDate): List<Int> =
    recentDoseEvents
        .filter { it.type == MedicationDoseEventType.TAKEN && it.eventDate != today }
        .mapNotNull { event ->
            val scheduled = event.scheduledMinuteOfDay ?: reminderMinuteOfDay
            val recorded = event.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()
                .let { it.hour * 60 + it.minute }
            recorded.takeIf { it - scheduled >= LATE_TAKE_THRESHOLD_MINUTES }
        }

private fun MedicationPlan.takenMinutesOfDay(today: LocalDate): List<Int> =
    recentDoseEvents
        .filter { it.type == MedicationDoseEventType.TAKEN && it.eventDate != today }
        .map { event ->
            event.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()
                .let { it.hour * 60 + it.minute }
        }

private fun MedicationPlan.toLocalAdherenceResult(
    today: LocalDate,
    currentMinute: Int
): MedicationAdherenceAssistResult {
    val lateMinutes = lateTakenMinutes(today)
    return if (missedToday(today, currentMinute)) {
        MedicationAdherenceAssistResult(
            medicationPlanId = id,
            suggestedReminderMinute = (currentMinute + 15).coerceAtMost(23 * 60),
            reason = "Reminder at ${formatMinute(reminderMinuteOfDay)} passed without a logged dose",
            source = RoutineAssistSource.LOCAL
        )
    } else {
        val typicalMinute = lateMinutes.sorted()[lateMinutes.size / 2]
        MedicationAdherenceAssistResult(
            medicationPlanId = id,
            suggestedReminderMinute = typicalMinute.coerceIn(0, 1439),
            reason = "Usually taken around ${formatMinute(typicalMinute)} — consider moving the reminder",
            source = RoutineAssistSource.LOCAL
        )
    }
}

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % (24 * 60)) + (24 * 60)) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}
