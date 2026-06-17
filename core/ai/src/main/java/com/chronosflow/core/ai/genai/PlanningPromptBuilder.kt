package com.chronosflow.core.ai.genai

import com.chronosflow.core.domain.model.DailyReviewSummary
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TimeBlock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PlanningPromptBuilder {
    fun dayPlanPrompt(
        userPreferences: String,
        date: LocalDate,
        timezone: String,
        review: DailyReviewSummary?,
        existingBlocks: List<TimeBlock>,
        pendingTasks: List<Task> = emptyList(),
        dueHabitTitles: List<String> = emptyList()
    ): String {
        val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        val blockSummary = existingBlocks.take(12).joinToString("\n") { block ->
            "- ${block.title} (${block.category}, ${block.provenance.name}) ${block.startMinuteOfDay}-${block.startMinuteOfDay + block.durationMinutes}m"
        }.ifBlank { "- none" }
        val reviewSummary = review?.let {
            "planned=${it.plannedMinutes} actual=${it.actualMinutes} missed=${it.missedMinutes} drift=${it.driftMinutes}"
        } ?: "none"
        val taskSummary = pendingTaskSummary(pendingTasks)
        val habitSummary = dueHabitTitles.take(6).joinToString("\n") { "- $it" }.ifBlank { "- none" }

        return """
            You are ChronosFlow, a privacy-aware day planner. Produce a JSON object only (no markdown fences).
            Schema:
            {
              "blocks": [
                {
                  "title": "string",
                  "category": "PLANNING|WORK|STUDY|RECOVERY|ADMIN|WORKOUT|REVIEW|FOCUS",
                  "startMinuteOfDay": 0-1439,
                  "durationMinutes": 5-240,
                  "flexibility": "FIXED|MOVABLE|RESIZABLE|OPTIONAL",
                  "isProtected": true|false
                }
              ],
              "reason": "string",
              "conflictsResolved": ["string"],
              "explanation": "string"
            }
            Constraints:
            - Use timezone $timezone for $dateLabel.
            - Respect locked or protected commitments in the existing plan.
            - Keep 3 to 6 blocks, non-overlapping, between 6:00 and 22:00 unless user asks otherwise.
            - Prefer recovery when review shows missed work or drift.
            - Schedule the user's pending tasks and due habits into free time before inventing generic blocks.
            User preferences: ${userPreferences.ifBlank { "balanced day" }}
            Review summary: $reviewSummary
            Existing blocks:
            $blockSummary
            Pending tasks (highest priority first):
            $taskSummary
            Habits due today:
            $habitSummary
        """.trimIndent()
    }

    internal fun pendingTaskSummary(pendingTasks: List<Task>): String =
        pendingTasks
            .filterNot(Task::isCompleted)
            .sortedByDescending(Task::priority)
            .take(10)
            .joinToString("\n") { task ->
                buildString {
                    append("- ${task.title} (priority=${task.priority}")
                    task.preferredDurationMinutes?.let { append(", prefers ${it}m") }
                    task.dueDate?.let { append(", due") }
                    append(")")
                }
            }
            .ifBlank { "- none" }

    /**
     * Corrective re-ask used when a day-plan response failed to parse as JSON. Echoes the original
     * instructions plus the malformed reply and demands a bare JSON object, which is usually enough
     * to coax a schema-valid retry out of a small on-device model before falling back to heuristics.
     */
    fun jsonRepairPrompt(originalPrompt: String, malformedResponse: String): String = """
        $originalPrompt

        Your previous response could not be parsed as JSON:
        ${malformedResponse.take(600)}
        Return only the JSON object described above. No prose, no markdown fences, no trailing commas.
    """.trimIndent()

    fun repairPrompt(currentPlan: String, conflictDescription: String): String = """
        You are ChronosFlow plan repair. Return plain text with numbered steps (max 6) to resolve the conflict.
        Keep advice on-device actionable. Do not mention cloud services.
        Conflict: ${conflictDescription.ifBlank { "unspecified conflict" }}
        Current plan excerpt: ${currentPlan.take(500)}
    """.trimIndent()
}
