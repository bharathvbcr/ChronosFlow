package com.chronosflow.core.ai

import android.content.Context
import android.os.Build
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchResults
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await

@Singleton
class SemanticAppSearchBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val semanticPlanningIndex: SemanticPlanningIndex
) {
    private var session: AppSearchSession? = null

    suspend fun ensureSession(): Boolean {
        if (session != null) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
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

    suspend fun syncDocuments(): Boolean {
        if (!ensureSession()) return false
        val documents = semanticPlanningIndex.appSearchDocuments()
        if (documents.isEmpty()) return true
        return runCatching {
            session?.putAsync(
                PutDocumentsRequest.Builder()
                    .addGenericDocuments(documents)
                    .build()
            )?.await()
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
            var page = results.nextPageAsync.await()
            while (page.isNotEmpty()) {
                page.forEach { match ->
                    mapped += match.genericDocument.toSemanticHit(match.rankingSignal)
                }
                page = results.nextPageAsync.await()
            }
            if (mapped.isEmpty()) lexical else mapped.take(limit)
        }.getOrDefault(lexical)
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

    private companion object {
        const val APPSEARCH_DATABASE_NAME = "chronosflow_planning"
    }
}
