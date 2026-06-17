import XCTest
@testable import ChronosCore

/// Tests for the deterministic command-palette ranker (`rankCommands`) and the response LRU.
/// Both are pure logic, so they run on the Windows/Linux ChronosCore CI without the model.
final class CommandAssistTests: XCTestCase {

    private let commands: [CommandAssistCandidate] = [
        CommandAssistCandidate(id: "add.task", title: "Add task", keywords: ["todo", "create", "new"]),
        CommandAssistCandidate(id: "start.focus", title: "Start focus", keywords: ["pomodoro", "timer", "deep work"]),
        CommandAssistCandidate(id: "log.mood", title: "Log mood", keywords: ["check in", "energy", "feeling"]),
        CommandAssistCandidate(id: "open.insights", title: "Open insights", keywords: ["review", "trends", "stats"]),
        CommandAssistCandidate(id: "plan.day", title: "Plan my day", keywords: ["ai", "schedule", "blocks"]),
    ]

    private func rank(_ q: String, limit: Int = 3) -> [String] {
        rankCommands(query: q, candidates: commands, limit: limit)
    }

    func testShortQueryReturnsNothing() {
        XCTAssertEqual(rank(""), [])
        XCTAssertEqual(rank("a"), [])
        XCTAssertEqual(rank("ad"), [])
    }

    func testExactTitleRanksFirst() {
        // "add task" is an exact title match (100) and should top the list.
        XCTAssertEqual(rank("add task").first, "add.task")
    }

    func testTitleContainsBeatsKeywordOnly() {
        // "focus" is contained in the "Start focus" title (phrase 80) but only a keyword elsewhere.
        XCTAssertEqual(rank("focus").first, "start.focus")
    }

    func testKeywordMatchWhenNoPhraseHit() {
        // "pomodoro" appears only as a keyword of start.focus — token overlap should still match it.
        XCTAssertEqual(rank("pomodoro").first, "start.focus")
    }

    func testIdContainsMatches() {
        // "insights" is in the id open.insights and the title — title-contains wins, but it resolves.
        XCTAssertTrue(rank("insights").contains("open.insights"))
    }

    func testNoMatchReturnsEmpty() {
        XCTAssertEqual(rank("xylophone"), [])
    }

    func testLimitIsRespected() {
        // "ai schedule" / broad query — ensure we never exceed the limit.
        XCTAssertLessThanOrEqual(rank("plan", limit: 2).count, 2)
    }

    func testMoreTokenOverlapRanksHigher() {
        // A query hitting two keywords of one command should outrank a single-keyword hit elsewhere.
        let ranked = rank("deep work timer")
        XCTAssertEqual(ranked.first, "start.focus")
    }

    func testCaseInsensitive() {
        XCTAssertEqual(rank("ADD TASK").first, "add.task")
    }

    // MARK: - ResponseLRU

    func testLRUHitAndMiss() {
        let cache = ResponseLRU(capacity: 2)
        XCTAssertNil(cache.get("a"))
        cache.put("a", "1")
        XCTAssertEqual(cache.get("a"), "1")
    }

    func testLRUEvictsLeastRecentlyUsed() {
        let cache = ResponseLRU(capacity: 2)
        cache.put("a", "1")
        cache.put("b", "2")
        _ = cache.get("a")          // a is now most-recently-used
        cache.put("c", "3")         // over capacity → evict b (LRU)
        XCTAssertEqual(cache.get("a"), "1")
        XCTAssertNil(cache.get("b"))
        XCTAssertEqual(cache.get("c"), "3")
        XCTAssertEqual(cache.count, 2)
    }

    func testLRUReputRefreshesWithoutGrowth() {
        let cache = ResponseLRU(capacity: 2)
        cache.put("a", "1")
        cache.put("a", "2")
        XCTAssertEqual(cache.get("a"), "2")
        XCTAssertEqual(cache.count, 1)
    }
}
