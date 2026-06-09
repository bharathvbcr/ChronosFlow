package com.chronosflow.core.ai.genai

import com.chronosflow.core.domain.model.DailyReviewSummary
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
        existingBlocks: List<TimeBlock>
    ): String {
        val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        val blockSummary = existingBlocks.take(12).joinToString("\n") { block ->
            "- ${block.title} (${block.category}) ${block.startMinuteOfDay}-${block.startMinuteOfDay + block.durationMinutes}m"
        }.ifBlank { "- none" }
        val reviewSummary = review?.let {
            "planned=${it.plannedMinutes} actual=${it.actualMinutes} missed=${it.missedMinutes} drift=${it.driftMinutes}"
        } ?: "none"

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
            User preferences: ${userPreferences.ifBlank { "balanced day" }}
            Review summary: $reviewSummary
            Existing blocks:
            $blockSummary
        """.trimIndent()
    }

    fun repairPrompt(currentPlan: String, conflictDescription: String): String = """
        You are ChronosFlow plan repair. Return plain text with numbered steps (max 6) to resolve the conflict.
        Keep advice on-device actionable. Do not mention cloud services.
        Conflict: ${conflictDescription.ifBlank { "unspecified conflict" }}
        Current plan excerpt: ${currentPlan.take(500)}
    """.trimIndent()
}
