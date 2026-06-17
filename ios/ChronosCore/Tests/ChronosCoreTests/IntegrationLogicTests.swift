import XCTest
@testable import ChronosCore

final class CalendarImportTests: XCTestCase {
    func testClassification() {
        XCTAssertEqual(classify(ExternalEvent(externalID: "1", title: "Standup", startMinute: 540, durationMinutes: 30)), .fixedBlock)
        if case .skipped = classify(ExternalEvent(externalID: "2", title: "Holiday", startMinute: 0, durationMinutes: 1440, isAllDay: true)) {} else { XCTFail("all-day should skip") }
        if case .skipped = classify(ExternalEvent(externalID: "3", title: "Tentative", startMinute: 600, durationMinutes: 60, isBusy: false)) {} else { XCTFail("free should skip") }
    }

    func testIdempotentMerge() {
        let fetched = [
            ExternalEvent(externalID: "a", title: "Meeting", startMinute: 540, durationMinutes: 60),
            ExternalEvent(externalID: "b", title: "Lunch w/ client", startMinute: 720, durationMinutes: 60),
            ExternalEvent(externalID: "c", title: "All day", startMinute: 0, durationMinutes: 1440, isAllDay: true),
        ]
        // "a" already imported; "d" was imported before but no longer fetched (stale).
        let plan = planCalendarImport(fetched: fetched, existingExternalIDs: ["a", "d"])
        XCTAssertEqual(plan.toAdd.map(\.externalID), ["b"])      // c skipped (all-day), a already present
        XCTAssertEqual(plan.staleExternalIDs, ["d"])            // d vanished → remove
    }
}

final class SleepTrendsTests: XCTestCase {
    func testAveragesAndDebt() {
        let durations = [7*60, 6*60, 8*60]      // avg 7h
        let bedtimes = [23*60, 23*60, 23*60]    // perfectly consistent
        let s = sleepTrends(durations: durations, bedtimes: bedtimes, qualities: [nil, nil, nil], targetMinutes: 8*60)
        XCTAssertEqual(s.averageDurationMinutes, 7*60)
        XCTAssertEqual(s.consistencyMinutes, 0)
        XCTAssertEqual(s.debtMinutes, 8*60*3 - (7*60+6*60+8*60)) // 3h debt
        XCTAssertEqual(s.nights, 3)
    }

    func testQualityTrendImproving() {
        let s = sleepTrends(durations: [400,420,440,460], bedtimes: [1380,1380,1380,1380],
                            qualities: [2, 2, 4, 5])
        XCTAssertEqual(s.trend, .improving)
    }

    func testEmpty() {
        let s = sleepTrends(durations: [], bedtimes: [], qualities: [])
        XCTAssertEqual(s.trend, .unknown)
        XCTAssertEqual(s.nights, 0)
    }
}

final class DayBalanceTests: XCTestCase {
    func testBalancedDayScoresHigh() {
        let blocks = [
            BalanceBlock(category: "FOCUS", durationMinutes: 120, energyLevel: 4),
            BalanceBlock(category: "BREAK", durationMinutes: 30, energyLevel: 1),
            BalanceBlock(category: "MEAL", durationMinutes: 45, energyLevel: 1),
            BalanceBlock(category: "STUDY", durationMinutes: 90, energyLevel: 3),
        ]
        let b = evaluateDayBalance(blocks)
        XCTAssertFalse(b.isOverloaded)
        XCTAssertGreaterThanOrEqual(b.score, 80)
        XCTAssertEqual(b.categoryVariety, 4)
    }

    func testOverloadedDayFlaggedAndPenalized() {
        let blocks = [
            BalanceBlock(category: "WORK", durationMinutes: 240, energyLevel: 5),
            BalanceBlock(category: "FOCUS", durationMinutes: 240, energyLevel: 5), // 8h demanding total
        ]
        let b = evaluateDayBalance(blocks)
        XCTAssertTrue(b.isOverloaded)
        XCTAssertLessThan(b.score, 60)
        XCTAssertEqual(b.demandingMinutes, 480)
    }

    func testEmptyDay() {
        XCTAssertEqual(evaluateDayBalance([]).score, 0)
    }
}
