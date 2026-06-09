package com.chronosflow.core.ai

import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.ai.genai.toRoutineAssistSource
import com.chronosflow.core.domain.model.Habit
import java.time.LocalDate
import javax.inject.Inject

data class HabitRepairAssistResult(
    val habitId: String,
    val suggestedStartMinute: Int,
    val suggestedEndMinute: Int,
    val reason: String,
    val source: RoutineAssistSource
)

class HabitRepairAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator
) {
    suspend fun suggestRepairs(
        habits: List<Habit>,
        today: LocalDate,
        currentMinute: Int
    ): List<HabitRepairAssistResult> {
        val missed = habits.missedForRepair(today, currentMinute)
        if (missed.isEmpty()) return emptyList()
        val baseline = missed.map { it.toLocalRepairResult(currentMinute) }
        val generation = genAiAssistCoordinator.generateAssistText(buildPrompt(missed, currentMinute))
        val source = generation.source.toRoutineAssistSource()
        val parsed = generation.text?.let { parseRepairs(it, missed, source) }.orEmpty()
        if (parsed.isNotEmpty()) {
            return parsed.take(3)
        }
        return baseline.take(3)
    }

    private fun buildPrompt(habits: List<Habit>, currentMinute: Int): String = buildString {
        appendLine("Suggest short recovery windows for missed ChronosFlow habits today.")
        appendLine("Return one line per habit as habitId|startMinute,endMinute|reason.")
        appendLine("Keep windows 15-30 minutes and within the same day.")
        appendLine("Do not add new habits or change cadence.")
        appendLine("Current minute: $currentMinute")
        habits.forEach { habit ->
            appendLine(
                "${habit.id}|${habit.title}|window=${habit.windowStartMinute}-${habit.windowEndMinute}|cadence=${habit.cadence}"
            )
        }
    }

    private fun parseRepairs(
        text: String,
        habits: List<Habit>,
        source: RoutineAssistSource
    ): List<HabitRepairAssistResult> {
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() && it.count { char -> char == '|' } >= 2 }
            .mapNotNull { line ->
                val parts = line.split("|").map { it.trim() }
                val habitId = parts.getOrNull(0).orEmpty()
                val habit = habits.firstOrNull { it.id == habitId } ?: return@mapNotNull null
                val window = parts.getOrNull(1).orEmpty()
                val reason = parts.drop(2).joinToString("|").ifBlank { defaultRepairReason(source) }
                val (start, end) = parseMinutePair(window) ?: return@mapNotNull null
                HabitRepairAssistResult(
                    habitId = habit.id,
                    suggestedStartMinute = start,
                    suggestedEndMinute = end,
                    reason = reason,
                    source = source
                )
            }
            .distinctBy { it.habitId }
            .toList()
    }

    private fun defaultRepairReason(source: RoutineAssistSource): String = when (source) {
        RoutineAssistSource.GEMINI_NANO -> "Recovery window suggested with Gemini Nano."
        RoutineAssistSource.CLOUD_GEMINI -> "Recovery window suggested with cloud Gemini."
        RoutineAssistSource.LOCAL -> "Recovery window suggested locally."
    }
}

fun List<Habit>.missedForRepair(today: LocalDate, currentMinute: Int): List<Habit> {
    return filter { habit ->
        habit.isActive &&
            (habit.schedule?.pausedUntil == null || habit.schedule?.pausedUntil?.isBefore(today) == true) &&
            habit.schedule?.skipDate != today &&
            habit.windowEndMinute < currentMinute &&
            habit.lastCompletedDate != today
    }.sortedBy { it.windowEndMinute }
}

private fun Habit.toLocalRepairResult(currentMinute: Int): HabitRepairAssistResult {
    val suggestedStart = maxOf(currentMinute + 15, windowEndMinute + 15).coerceAtMost(22 * 60)
    return HabitRepairAssistResult(
        habitId = id,
        suggestedStartMinute = suggestedStart,
        suggestedEndMinute = (suggestedStart + 20).coerceAtMost(24 * 60),
        reason = "Window closed at ${formatMinute(windowEndMinute)} without completion",
        source = RoutineAssistSource.LOCAL
    )
}

private fun parseMinutePair(value: String): Pair<Int, Int>? {
    val parts = value.split(',', ';').map { it.trim() }
    val start = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 1425) ?: return null
    val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(start + 15, 1440) ?: return null
    return start to end
}

private fun formatMinute(minute: Int): String {
    val normalized = ((minute % (24 * 60)) + (24 * 60)) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}
