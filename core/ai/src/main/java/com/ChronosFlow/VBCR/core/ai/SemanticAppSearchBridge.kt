package com.ChronosFlow.VBCR.core.ai

import android.content.Context
import android.os.Build
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchResults
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Syncs the in-memory semantic planning corpus into on-device AppSearch.
 *
 * [syncDocuments] is a full set-replacement: documents put this round that are absent from the
 * fresh corpus are REMOVED. Without the removal pass, deleted tasks/blocks/habits would stay
 * searchable forever and keep outranking the fresh lexical results in [query].
 */
@Singleton
class SemanticAppSearchBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val semanticPlanningIndex: SemanticPlanningIndex
) {
    private var session: AppSearchSession? = null

    /** Serialises session creation so concurrent callers cannot leak a second session. */
    private val sessionMutex = Mutex()

    suspend fun ensureSession(): Boolean {
        session?.let { return true }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return sessionMutex.withLock {
            session?.let { return true }
            runCatching {
                val searchContext = LocalStorage.SearchContext.Builder(context, APPSEARCH_DATABASE_NAME).build()
                val created = LocalStorage.createSearchSessionAsync(searchContext).await()
                created.setSchemaAsync(
                    SetSchemaRequest.Builder()
                        .addSchemas(semanticPlanningIndex.appSearchSchema())
                        .build()
                ).await()
                session = created
                true
            }.getOrDefault(false)
        }
    }

    suspend fun syncDocuments(): Boolean {
        if (!ensureSession()) return false
        val documents = semanticPlanningIndex.appSearchDocuments()
        val activeSession = session ?: return false
        return runCatching {
            if (documents.isNotEmpty()) {
                activeSession.putAsync(
                    PutDocumentsRequest.Builder()
                        .addGenericDocuments(documents)
                        .build()
                )?.await()
            }
            // Deletion pass: everything indexed under our namespace that the fresh corpus no
            // longer contains is stale and must not stay searchable.
            val freshIds = documents.mapTo(HashSet()) { it.id }
            val indexedIds = collectIndexedIds(activeSession)
            val stale = computeStaleDocumentIds(indexedIds, freshIds)
            if (stale.isNotEmpty()) {
                activeSession.removeAsync(
                    RemoveByDocumentIdRequest.Builder(SemanticPlanningIndex.APPSEARCH_NAMESPACE)
                        .addIds(stale)
                        .build()
                ).await()
            }
            true
        }.getOrDefault(false)
    }

    suspend fun query(
        query: String,
        limit: Int = 8,
        semanticAvailable: Boolean = false
    ): List<SemanticSearchHit> {
        val lexical = semanticPlanningIndex.query(query, limit, semanticAvailable)
        val activeSession = session ?: return lexical
        if (query.isBlank()) return lexical

        return runCatching {
            val results: SearchResults = activeSession.search(
                query,
                semanticPlanningIndex.appSearchQuerySpec(limit, semanticAvailable)
            )
            val mapped = mutableListOf<SemanticSearchHit>()
            // Stop as soon as we have enough hits — never drain the whole index for a capped query.
            while (mapped.size < limit) {
                val page = results.nextPageAsync.await()
                if (page.isEmpty()) break
                page.forEach { match ->
                    mapped += match.genericDocument.toSemanticHit(match.rankingSignal)
                }
            }
            if (mapped.isEmpty()) lexical else mapped.take(limit)
        }.getOrDefault(lexical)
    }

    /**
     * Enumerates every document id currently indexed in our namespace. Every document carries one
     * of the known type terms on an EXACT_TERMS-indexed field, so an OR over those terms is a
     * match-all scan bounded by [MAX_INDEX_SCAN_DOCS].
     */
    private suspend fun collectIndexedIds(activeSession: AppSearchSession): Set<String> {
        val spec = SearchSpec.Builder()
            .addFilterSchemas(listOf(SemanticPlanningIndex.APPSEARCH_SCHEMA_TYPE))
            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
            .setResultCountPerPage(INDEX_SCAN_PAGE_SIZE)
            .setRankingStrategy(SearchSpec.RANKING_STRATEGY_NONE)
            .build()
        val results: SearchResults = activeSession.search(planningTypeMatchAllQuery(), spec)
        val ids = HashSet<String>()
        while (ids.size < MAX_INDEX_SCAN_DOCS) {
            val page = results.nextPageAsync.await()
            if (page.isEmpty()) break
            page.forEach { match -> ids += match.genericDocument.id }
        }
        return ids
    }

    private fun GenericDocument.toSemanticHit(score: Double): SemanticSearchHit {
        val typeRaw = getPropertyString(SemanticPlanningIndex.APPSEARCH_FIELD_TYPE).orEmpty()
        val type = runCatching { SemanticDocumentType.valueOf(typeRaw) }
            .getOrElse { SemanticDocumentType.TIME_BLOCK }
        val text = getPropertyString(SemanticPlanningIndex.APPSEARCH_FIELD_TEXT).orEmpty()
        return SemanticSearchHit(
            id = id,
            type = type,
            title = when (type) {
                SemanticDocumentType.REVIEW -> "Daily review"
                SemanticDocumentType.TIME_BLOCK -> "Time block"
                SemanticDocumentType.TASK -> "Task"
                SemanticDocumentType.MEDICATION -> "Medication"
                SemanticDocumentType.HABIT -> "Habit"
                SemanticDocumentType.FOCUS -> "Focus session"
            },
            snippet = text.take(120),
            score = score.toFloat().coerceAtLeast(0.01f),
            provenance = "appsearch"
        )
    }

    companion object {
        const val APPSEARCH_DATABASE_NAME = "chronosflow_planning"

        /** Safety bound so a corrupt index can never turn a refresh into an endless scan. */
        internal const val MAX_INDEX_SCAN_DOCS = 2_000
        internal const val INDEX_SCAN_PAGE_SIZE = 100

        /**
         * Query matching every document we index: each carries exactly one known value on the
         * EXACT_TERMS-indexed `type` field, so OR-ing the values covers the corpus.
         */
        internal fun planningTypeMatchAllQuery(): String =
            SemanticDocumentType.entries.joinToString(" OR ") { it.name }
    }
}

/**
 * Pure set-difference policy for the sync deletion pass: ids present in the index but absent from
 * the fresh corpus must be removed. Extracted for direct unit testing.
 */
internal fun computeStaleDocumentIds(indexedIds: Set<String>, freshIds: Set<String>): List<String> =
    indexedIds.filterNot { it in freshIds }.sorted()
