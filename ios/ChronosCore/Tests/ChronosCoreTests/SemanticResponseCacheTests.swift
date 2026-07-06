import XCTest
@testable import ChronosCore

final class SemanticResponseCacheTests: XCTestCase {

    private func makeCache(
        capacity: Int = 32,
        ttlMs: Int64 = 10_000,
        threshold: Double = 0.90,
        margin: Double = 0.05,
        minTokens: Int = 3
    ) -> SemanticResponseCache {
        SemanticResponseCache(
            capacity: capacity,
            ttlMs: ttlMs,
            similarityThreshold: threshold,
            similarityMargin: margin,
            minTokensForSemantic: minTokens
        )
    }

    func testExactKeyReplaysStoredValue() {
        let cache = makeCache()
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", value: "SUMMARY", nowMs: 0)
        XCTAssertEqual(cache.getExact("k1", nowMs: 100), "SUMMARY")
        XCTAssertEqual(cache.lookup(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", nowMs: 100), "SUMMARY")
    }

    func testReorderedNearDuplicateHitsSemantically() {
        let cache = makeCache()
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", value: "SUMMARY", nowMs: 0)
        // Same tokens, reordered, distinct exact key → exact miss, semantic hit.
        let hit = cache.lookup(exactKey: "k2", namespace: "BALANCED", semanticText: "for the week summarize my open tasks", nowMs: 50)
        XCTAssertEqual(hit, "SUMMARY")
    }

    func testDifferentNamespaceNeverShares() {
        let cache = makeCache()
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", value: "SUMMARY", nowMs: 0)
        XCTAssertNil(cache.lookup(exactKey: "k2", namespace: "CREATIVE", semanticText: "summarize my open tasks for the week", nowMs: 50))
    }

    func testDissimilarPromptMissesOnThreshold() {
        let cache = makeCache()
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", value: "SUMMARY", nowMs: 0)
        XCTAssertNil(cache.lookup(exactKey: "k2", namespace: "BALANCED", semanticText: "proofread this paragraph about hiking trips", nowMs: 50))
    }

    func testAmbiguousMatchRejectedByMargin() {
        let cache = makeCache()
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "review my morning workout routine plan", value: "MORNING", nowMs: 0)
        cache.put(exactKey: "k2", namespace: "BALANCED", semanticText: "review my evening workout routine plan", value: "EVENING", nowMs: 0)
        // Equally close to both stored prompts (differs only by morning/evening) → cannot disambiguate.
        XCTAssertNil(cache.lookup(exactKey: "k3", namespace: "BALANCED", semanticText: "review my workout routine plan", nowMs: 50))
    }

    func testExpiredEntryNotReturned() {
        let cache = makeCache(ttlMs: 1_000)
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "summarize my open tasks for the week", value: "SUMMARY", nowMs: 0)
        XCTAssertNil(cache.getExact("k1", nowMs: 2_000))
        XCTAssertNil(cache.lookup(exactKey: "k2", namespace: "BALANCED", semanticText: "for the week summarize my open tasks", nowMs: 2_000))
    }

    func testShortPromptFallsBackToExactOnly() {
        let cache = makeCache(minTokens: 3)
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "plan the whole busy afternoon", value: "PLAN", nowMs: 0)
        // Fewer than minTokens meaningful tokens → no semantic scan, and exact key differs.
        XCTAssertNil(cache.lookup(exactKey: "k2", namespace: "BALANCED", semanticText: "ok no", nowMs: 50))
    }

    func testHonoursCapacityWithLruEviction() {
        let cache = makeCache(capacity: 2)
        cache.put(exactKey: "k1", namespace: "BALANCED", semanticText: "first distinct prompt alpha", value: "A", nowMs: 0)
        cache.put(exactKey: "k2", namespace: "BALANCED", semanticText: "second distinct prompt bravo", value: "B", nowMs: 0)
        cache.put(exactKey: "k3", namespace: "BALANCED", semanticText: "third distinct prompt charlie", value: "C", nowMs: 0)
        XCTAssertNil(cache.getExact("k1", nowMs: 10)) // evicted as least-recently-used
        XCTAssertEqual(cache.getExact("k2", nowMs: 10), "B")
        XCTAssertEqual(cache.getExact("k3", nowMs: 10), "C")
    }
}
