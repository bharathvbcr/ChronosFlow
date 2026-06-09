package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.ProposedSuggestionBlock
import com.chronosflow.core.ai.StructuredDayPlanSuggestion
import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object DayPlanResponseParser {
    fun parse(
        raw: String,
        timezone: String,
        fallbackExplanation: String
    ): StructuredDayPlanSuggestion? {
        val jsonText = extractJsonObject(raw) ?: return null
        return runCatching {
            val root = JSONObject(jsonText)
            val blocksArray = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = buildList {
                for (index in 0 until blocksArray.length()) {
                    val item = blocksArray.optJSONObject(index) ?: continue
                    add(item.toSuggestion(timezone))
                }
            }
            if (blocks.isEmpty()) return@runCatching null
            StructuredDayPlanSuggestion(
                proposedBlocks = blocks,
                reason = root.optString("reason").ifBlank { "AI-generated day plan" },
                conflictsResolved = root.optJSONArray("conflictsResolved").toStringList(),
                requireConfirmation = true,
                explanation = root.optString("explanation").ifBlank { fallbackExplanation }
            )
        }.getOrNull()
    }

    private fun JSONObject.toSuggestion(timezone: String): ProposedSuggestionBlock {
        val flexibility = runCatching {
            BlockFlexibility.valueOf(optString("flexibility", "MOVABLE"))
        }.getOrDefault(BlockFlexibility.MOVABLE)
        return ProposedSuggestionBlock(
            id = UUID.randomUUID().toString(),
            title = optString("title", "Suggested block"),
            category = optString("category", "WORK"),
            startMinuteOfDay = optInt("startMinuteOfDay", 9 * 60).coerceIn(0, 1439),
            durationMinutes = optInt("durationMinutes", 45).coerceIn(5, 240),
            provenance = BlockProvenance.AI_SUGGESTED,
            flexibility = flexibility,
            isLocked = flexibility == BlockFlexibility.FIXED,
            isProtected = optBoolean("isProtected", false),
            timezone = timezone
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return null
    }
}
