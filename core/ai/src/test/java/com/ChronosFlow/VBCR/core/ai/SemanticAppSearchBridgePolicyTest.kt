package com.ChronosFlow.VBCR.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticAppSearchBridgePolicyTest {
    @Test
    fun `stale ids are exactly the indexed ids missing from the fresh corpus`() {
        val stale = computeStaleDocumentIds(
            indexedIds = setOf("task-1", "task-2", "block-9", "habit-gone"),
            freshIds = setOf("task-1", "block-9")
        )
        // Deterministic ordering keeps removal batches reproducible.
        assertEquals(listOf("habit-gone", "task-2"), stale)
    }

    @Test
    fun `empty index yields no removals`() {
        assertTrue(computeStaleDocumentIds(emptySet(), setOf("task-1")).isEmpty())
    }

    @Test
    fun `fully replaced corpus removes everything old`() {
        val stale = computeStaleDocumentIds(
            indexedIds = setOf("a", "b", "c"),
            freshIds = emptySet()
        )
        assertEquals(listOf("a", "b", "c"), stale)
    }

    @Test
    fun `match-all query covers every semantic document type`() {
        val query = SemanticAppSearchBridge.planningTypeMatchAllQuery()
        SemanticDocumentType.entries.forEach { type ->
            assertTrue(
                "query must match type ${type.name}: $query",
                query.split(" OR ").contains(type.name)
            )
        }
    }

    @Test
    fun `index scan bounds are sane`() {
        assertTrue(SemanticAppSearchBridge.MAX_INDEX_SCAN_DOCS > 0)
        assertTrue(SemanticAppSearchBridge.INDEX_SCAN_PAGE_SIZE in 1..SemanticAppSearchBridge.MAX_INDEX_SCAN_DOCS)
    }
}
