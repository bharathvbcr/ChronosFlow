package com.ChronosFlow.VBCR.core.ai

import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.SearchSpec
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.FocusSession
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import javax.inject.Inject
import javax.inject.Singleton

enum class SemanticDocumentType {
    REVIEW,
    TIME_BLOCK,
    TASK,
    MEDICATION,
    HABIT,
    FOCUS
}

data class SemanticSearchHit(
    val id: String,
    val type: SemanticDocumentType,
    val title: String,
    val snippet: String,
    val score: Float,
    val provenance: String
)

/**
 * Local-first semantic planning index.
 *
 * The in-memory path remains the always-available fallback. AppSearch documents,
 * schema, and query specs are exposed so Android 17 semantic/embedding-backed
 * stores can index the same redacted corpus without introducing cloud dependency.
 */
@Singleton
class SemanticPlanningIndex @Inject constructor() {
    private val documents = mutableListOf<IndexedDocument>()

    fun rebuild(
        reviews: List<DailyReviewSummary>,
        blocks: List<TimeBlock>,
        tasks: List<Task>,
        habits: List<Habit>,
        medications: List<MedicationPlan>,
        focusSessions: List<FocusSession>,
        redactMedicationNames: Boolean = true
    ) {
        documents.clear()
        reviews.forEach { review ->
            documents += IndexedDocument(
                id = "review-${review.date}",
                type = SemanticDocumentType.REVIEW,
                text = "${review.date} planned ${review.plannedMinutes} actual ${review.actualMinutes} drift ${review.driftMinutes}"
            )
        }
        blocks.forEach { block ->
            documents += IndexedDocument(
                id = "block-${block.id}",
                type = SemanticDocumentType.TIME_BLOCK,
                text = "${block.title} ${block.category} ${block.date} ${block.startMinuteOfDay}"
            )
        }
        tasks.forEach { task ->
            documents += IndexedDocument(
                id = "task-${task.id}",
                type = SemanticDocumentType.TASK,
                text = buildString {
                    append(task.title)
                    task.description?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    append(" priority ").append(task.priority)
                    task.targetDate?.let { append(" target ").append(it) }
                    task.preferredDurationMinutes?.let { append(" duration ").append(it) }
                    task.preferredStartMinuteOfDay?.let { append(" around ").append(it) }
                    if (task.isCompleted) append(" completed") else append(" open")
                }
            )
        }
        habits.forEach { habit ->
            documents += IndexedDocument(
                id = "habit-${habit.id}",
                type = SemanticDocumentType.HABIT,
                text = "${habit.title} streak ${habit.streakCount} window ${habit.windowStartMinute}"
            )
        }
        medications.forEach { plan ->
            val label = if (redactMedicationNames) "medication plan" else plan.name
            documents += IndexedDocument(
                id = "med-${plan.id}",
                type = SemanticDocumentType.MEDICATION,
                text = "$label reminder ${plan.reminderMinuteOfDay} missed ${plan.missedCount}"
            )
        }
        focusSessions.forEach { session ->
            documents += IndexedDocument(
                id = "focus-${session.id}",
                type = SemanticDocumentType.FOCUS,
                text = "focus completed=${session.isCompleted} block ${session.blockId ?: "none"} interruptions ${session.interruptions}"
            )
        }
    }

    fun query(
        query: String,
        limit: Int = 8,
        semanticAvailable: Boolean = false
    ): List<SemanticSearchHit> {
        if (query.isBlank()) return emptyList()
        val tokens = query.lowercase().split(TOKEN_SPLIT).filter { it.length > 1 }
        if (tokens.isEmpty()) return emptyList()
        return documents
            .map { doc ->
                val score = if (semanticAvailable) {
                    lexicalScore(doc.normalizedText, tokens) * 1.1f
                } else {
                    lexicalScore(doc.normalizedText, tokens)
                }
                doc to score
            }
            .filter { (_, score) -> score > 0f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (doc, score) ->
                SemanticSearchHit(
                    id = doc.id,
                    type = doc.type,
                    title = titleFor(doc),
                    snippet = doc.text.take(120),
                    score = score,
                    provenance = doc.type.name.lowercase()
                )
            }
    }

    fun appSearchSchema(): AppSearchSchema {
        return AppSearchSchema.Builder(APPSEARCH_SCHEMA_TYPE)
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(APPSEARCH_FIELD_TYPE)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REQUIRED)
                    .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build()
            )
            .addProperty(
                AppSearchSchema.StringPropertyConfig.Builder(APPSEARCH_FIELD_TEXT)
                    .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REQUIRED)
                    .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                    .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                    .build()
            )
            .build()
    }

    fun appSearchDocuments(): List<GenericDocument> {
        return documents.map { doc ->
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                APPSEARCH_NAMESPACE,
                doc.id,
                APPSEARCH_SCHEMA_TYPE
            )
                .setPropertyString(APPSEARCH_FIELD_TYPE, doc.type.name)
                .setPropertyString(APPSEARCH_FIELD_TEXT, doc.text)
                .build()
        }
    }

    fun appSearchQuerySpec(limit: Int = 8, semanticAvailable: Boolean = false): SearchSpec {
        val rankingStrategy = if (semanticAvailable) {
            SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE
        } else {
            SearchSpec.RANKING_STRATEGY_NONE
        }
        return SearchSpec.Builder()
            .setResultCountPerPage(limit.coerceAtLeast(1))
            .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
            .setRankingStrategy(rankingStrategy)
            .build()
    }

    private fun titleFor(doc: IndexedDocument): String = when (doc.type) {
        SemanticDocumentType.REVIEW -> "Daily review"
        SemanticDocumentType.TIME_BLOCK -> "Time block"
        SemanticDocumentType.TASK -> "Task"
        SemanticDocumentType.MEDICATION -> "Medication"
        SemanticDocumentType.HABIT -> "Habit"
        SemanticDocumentType.FOCUS -> "Focus session"
    }

    private fun lexicalScore(normalizedText: String, tokens: List<String>): Float {
        if (tokens.isEmpty()) return 0f
        val hits = tokens.count { normalizedText.contains(it) }
        return hits.toFloat() / tokens.size
    }

    private data class IndexedDocument(
        val id: String,
        val type: SemanticDocumentType,
        val text: String
    ) {
        val normalizedText: String = text.lowercase()
    }

    companion object {
        private val TOKEN_SPLIT = Regex("\\s+")

        const val APPSEARCH_NAMESPACE = "chronosflow_planning"
        const val APPSEARCH_SCHEMA_TYPE = "ChronosPlanningDocument"
        const val APPSEARCH_FIELD_TYPE = "type"
        const val APPSEARCH_FIELD_TEXT = "text"
    }
}
