package com.chronosflow.core.ai

import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.data.privacy.AssistantPreferences
import javax.inject.Inject

data class CommandAssistCandidate(
    val id: String,
    val title: String,
    val keywords: Set<String>
)

class CommandAssistPlanner @Inject constructor(
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val assistantPreferences: AssistantPreferences
) {
    fun localRankCommandIds(
        query: String,
        candidates: List<CommandAssistCandidate>,
        limit: Int = 3
    ): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.length < 3) return emptyList()
        val queryTokens = normalized.split(" ").filter { it.length > 2 }
        return candidates
            .map { candidate ->
                val haystack = (candidate.title + " " + candidate.keywords.joinToString(" ")).lowercase()
                val phraseScore = when {
                    candidate.title.lowercase() == normalized -> 100
                    candidate.title.lowercase().contains(normalized) -> 80
                    candidate.id.lowercase().contains(normalized) -> 70
                    haystack.contains(normalized) -> 60
                    else -> 0
                }
                // Graded token overlap: every matching query token counts, so a single strong keyword
                // match still ranks (the old code needed >=2 tokens and gave a flat 40) and more
                // overlap ranks higher. Base score when there's no phrase hit; a tiebreaker otherwise.
                val tokenHits = queryTokens.count { token -> haystack.contains(token) }
                val score = when {
                    phraseScore > 0 -> phraseScore + tokenHits
                    tokenHits > 0 -> 20 + tokenHits * 15
                    else -> 0
                }
                candidate.id to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    suspend fun rankCommandIdsWithAssist(
        query: String,
        candidates: List<CommandAssistCandidate>,
        limit: Int = 3
    ): List<String> {
        val privacy = runCatching {
            PrivacyMode.valueOf(assistantPreferences.assistantPrivacyModeValue())
        }.getOrDefault(PrivacyMode.ON_DEVICE_ONLY)
        if (privacy == PrivacyMode.DISABLED) {
            return localRankCommandIds(query, candidates, limit)
        }
        val baseline = localRankCommandIds(query, candidates, limit)
        val generation = genAiAssistCoordinator.generateAssistText(
            buildPrompt(query, candidates, baseline)
        )
        val parsed = generation.text?.let { parseCommandIds(it, candidates) }.orEmpty()
        return (parsed + baseline).distinct().take(limit)
    }

    private fun buildPrompt(
        query: String,
        candidates: List<CommandAssistCandidate>,
        baseline: List<String>
    ): String = buildString {
        appendLine("Pick up to three ChronosFlow command ids for the user query.")
        appendLine("Return one command id per line. Use only ids from the list.")
        appendLine("Query: $query")
        candidates.take(40).forEach { candidate ->
            appendLine("Command: ${candidate.id} | ${candidate.title} | ${candidate.keywords.joinToString(", ")}")
        }
        baseline.forEach { appendLine("Baseline match: $it") }
    }

    private fun parseCommandIds(
        text: String,
        candidates: List<CommandAssistCandidate>
    ): List<String> {
        val allowed = candidates.map { it.id }.toSet()
        return text.lineSequence()
            .map { it.trim().trim('-', '*') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val id = line.substringBefore("|").trim().ifBlank { line.trim() }
                id.takeIf { it in allowed }
            }
            .distinct()
            .toList()
    }
}
