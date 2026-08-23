package com.ChronosFlow.VBCR.core.ai.genai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemanticResponseCacheTest {

    private fun cache(
        maxEntries: Int = 32,
        ttlMs: Long = 10_000L,
        threshold: Float = SemanticResponseCache.DEFAULT_SIMILARITY_THRESHOLD,
        margin: Float = SemanticResponseCache.DEFAULT_SIMILARITY_MARGIN,
        minTokens: Int = SemanticResponseCache.DEFAULT_MIN_TOKENS_FOR_SEMANTIC
    ) = SemanticResponseCache(maxEntries, ttlMs, threshold, margin, minTokens)

    @Test
    fun `exact key replays stored value`() {
        val cache = cache()
        cache.put("k1", "BALANCED", "summarize my open tasks for the week", "SUMMARY", nowMs = 0)
        assertEquals("SUMMARY", cache.getExact("k1", nowMs = 100))
        assertEquals("SUMMARY", cache.lookup("k1", "BALANCED", "summarize my open tasks for the week", nowMs = 100))
    }

    @Test
    fun `reordered near-duplicate hits semantically under a different exact key`() {
        val cache = cache()
        cache.put("k1", "BALANCED", "summarize my open tasks for the week", "SUMMARY", nowMs = 0)
        // Same tokens, reordered, distinct exact key → exact miss, semantic hit.
        val hit = cache.lookup("k2", "BALANCED", "for the week summarize my open tasks", nowMs = 50)
        assertEquals("SUMMARY", hit)
    }

    @Test
    fun `changed date in an otherwise near-identical prompt misses instead of replaying stale data`() {
        val cache = cache()
        cache.put(
            "k1", "BALANCED",
            "plan august 22 with timezone america chicago and these tasks write report 45 minutes review budget 30 minutes",
            "TODAY_PLAN",
            nowMs = 0
        )
        // Same shape and wording except the date moved one day — the old plan must NOT replay
        // even though cosine similarity stays far above the threshold.
        val hit = cache.lookup(
            "k2", "BALANCED",
            "plan august 23 with timezone america chicago and these tasks write report 45 minutes review budget 30 minutes",
            nowMs = 50
        )
        assertNull(hit)
    }

    @Test
    fun `an added task changes the prompt shape enough to miss the cache`() {
        val cache = cache()
        val base =
            "plan august 22 with tasks write report 45 minutes review budget 30 minutes draft memo 20 minutes " +
                "prepare slides 60 minutes call client 15 minutes file expenses 25 minutes"
        cache.put("k1", "BALANCED", base, "OLD_PLAN", nowMs = 0)
        // One extra task line (~10% more tokens) — must miss so fresh inference sees the new task.
        val hit = cache.lookup(
            "k2", "BALANCED",
            base + " book dentist appointment 15 minutes",
            nowMs = 50
        )
        assertNull(hit)
    }

    @Test
    fun `cosmetic edits that keep dates and shape still hit the cache`() {
        val cache = cache()
        val base =
            "plan august 22 with tasks write report 45 minutes review budget 30 minutes draft memo 20 minutes " +
                "prepare slides 60 minutes"
        cache.put("k1", "BALANCED", base, "PLAN", nowMs = 0)
        val edited = base.replace("write report 45 minutes", "45 minutes write report")
        assertEquals("PLAN", cache.lookup("k2", "BALANCED", edited, nowMs = 50))
    }

    @Test
    fun `different namespace never shares an answer`() {
        val cache = cache()
        cache.put("k1", "BALANCED", "summarize my open tasks for the week", "SUMMARY", nowMs = 0)
        val hit = cache.lookup("k2", "CREATIVE", "summarize my open tasks for the week", nowMs = 50)
        assertNull(hit)
    }

    @Test
    fun `dissimilar prompt misses on threshold`() {
        val cache = cache()
        cache.put("k1", "BALANCED", "summarize my open tasks for the week", "SUMMARY", nowMs = 0)
        val hit = cache.lookup("k2", "BALANCED", "proofread this paragraph about hiking trips", nowMs = 50)
        assertNull(hit)
    }

    @Test
    fun `ambiguous match between two close neighbours is rejected by the margin gate`() {
        val cache = cache()
        cache.put("k1", "BALANCED", "review my morning workout routine plan", "MORNING", nowMs = 0)
        cache.put("k2", "BALANCED", "review my evening workout routine plan", "EVENING", nowMs = 0)
        // Equally close to both stored prompts (differs only by morning/evening) → cannot disambiguate.
        val hit = cache.lookup("k3", "BALANCED", "review my workout routine plan", nowMs = 50)
        assertNull(hit)
    }

    @Test
    fun `expired entry is not returned`() {
        val cache = cache(ttlMs = 1_000L)
        cache.put("k1", "BALANCED", "summarize my open tasks for the week", "SUMMARY", nowMs = 0)
        assertNull(cache.getExact("k1", nowMs = 2_000))
        assertNull(cache.lookup("k2", "BALANCED", "for the week summarize my open tasks", nowMs = 2_000))
    }

    @Test
    fun `short prompt falls back to exact-only matching`() {
        val cache = cache(minTokens = 3)
        cache.put("k1", "BALANCED", "plan the whole busy afternoon", "PLAN", nowMs = 0)
        // Query has fewer than minTokens meaningful tokens → no semantic scan, and exact key differs.
        assertNull(cache.lookup("k2", "BALANCED", "ok no", nowMs = 50))
    }

    @Test
    fun `honours max entries with lru eviction`() {
        val cache = cache(maxEntries = 2)
        cache.put("k1", "BALANCED", "first distinct prompt alpha", "A", nowMs = 0)
        cache.put("k2", "BALANCED", "second distinct prompt bravo", "B", nowMs = 0)
        cache.put("k3", "BALANCED", "third distinct prompt charlie", "C", nowMs = 0)
        assertNull(cache.getExact("k1", nowMs = 10)) // evicted as least-recently-used
        assertEquals("B", cache.getExact("k2", nowMs = 10))
        assertEquals("C", cache.getExact("k3", nowMs = 10))
    }
}
