import XCTest
@testable import ChronosCore

private var utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()
private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d))!
}

final class InsightsSummaryTests: XCTestCase {
    func testCategorySharesSumToOne() {
        let rows = categoryBreakdownRows([
            ("FOCUS", 120), ("BREAK", 30), ("FOCUS", 60), ("MEAL", 30)
        ])
        // FOCUS 180, BREAK 30, MEAL 30 -> total 240. Sorted: FOCUS, then BREAK<MEAL by name.
        XCTAssertEqual(rows.map(\.category), ["FOCUS", "BREAK", "MEAL"])
        XCTAssertEqual(rows[0].minutes, 180)
        XCTAssertEqual(rows.map(\.share).reduce(0, +), 1.0, accuracy: 0.0001)
        // progress is share-of-max: FOCUS is the max so 1.0; BREAK/MEAL = 30/180.
        XCTAssertEqual(rows[0].progress, 1.0, accuracy: 0.0001)
        XCTAssertEqual(rows[1].progress, 30.0 / 180.0, accuracy: 0.0001)
    }

    func testEmptyBreakdown() {
        XCTAssertTrue(categoryBreakdownRows([]).isEmpty)
        XCTAssertTrue(categoryBreakdownRows([("FOCUS", 0)]).isEmpty)
    }

    func testConcentrationLabels() {
        XCTAssertEqual(ConcentrationLabel(topShare: 0.8), .high)
        XCTAssertEqual(ConcentrationLabel(topShare: 0.6), .moderate)
        XCTAssertEqual(ConcentrationLabel(topShare: 0.4), .even)
        XCTAssertEqual(ConcentrationLabel(topShare: 0.1), .balanced)
    }

    func testCompletionMathAndDrift() {
        let anchor = day(2025, 6, 10)
        let blocks = [
            // 60 planned, logged 65 actual
            InsightsBlock(date: anchor, category: "FOCUS", plannedDuration: 60, actualStart: 540, actualEnd: 605),
            // 30 planned, logged 30 actual
            InsightsBlock(date: anchor, category: "BREAK", plannedDuration: 30, actualStart: 660, actualEnd: 690),
            // 30 planned, NOT logged
            InsightsBlock(date: anchor, category: "TASK", plannedDuration: 30),
        ]
        let s = summarize(blocks: blocks, period: .day, anchorDate: anchor, calendar: utcCal)
        XCTAssertEqual(s.plannedMinutes, 120)
        XCTAssertEqual(s.actualMinutes, 95)
        XCTAssertEqual(s.missedMinutes, 30)
        XCTAssertEqual(s.missedCount, 1)
        XCTAssertEqual(s.loggedCount, 2)
        XCTAssertEqual(s.totalCount, 3)
        XCTAssertEqual(s.driftMinutes, -25)
        // 95/120 = 79.16% -> 79
        XCTAssertEqual(s.completionPercent, 79)
        XCTAssertTrue(s.hasPlan)
    }

    func testWeekVsDayWindowing() {
        let anchor = day(2025, 6, 10)
        let blocks = [
            InsightsBlock(date: day(2025, 6, 10), category: "FOCUS", plannedDuration: 60),  // today
            InsightsBlock(date: day(2025, 6, 7),  category: "FOCUS", plannedDuration: 60),  // 3 days ago (in week)
            InsightsBlock(date: day(2025, 6, 2),  category: "FOCUS", plannedDuration: 60),  // 8 days ago (out of week)
            InsightsBlock(date: day(2025, 6, 12), category: "FOCUS", plannedDuration: 60),  // future (excluded)
        ]
        let dayS = summarize(blocks: blocks, period: .day, anchorDate: anchor, calendar: utcCal)
        XCTAssertEqual(dayS.totalCount, 1)
        XCTAssertEqual(dayS.plannedMinutes, 60)

        let weekS = summarize(blocks: blocks, period: .week, anchorDate: anchor, calendar: utcCal)
        // today + 3-days-ago, but not 8-days-ago and not the future block.
        XCTAssertEqual(weekS.totalCount, 2)
        XCTAssertEqual(weekS.plannedMinutes, 120)

        let monthS = summarize(blocks: blocks, period: .month, anchorDate: anchor, calendar: utcCal)
        // all 3 past/today blocks fall in the trailing 30 days; future still excluded.
        XCTAssertEqual(monthS.totalCount, 3)
    }

    func testNoPlanSummary() {
        let s = summarize(blocks: [], period: .day, anchorDate: day(2025, 6, 10), calendar: utcCal)
        XCTAssertFalse(s.hasPlan)
        XCTAssertEqual(s.completionPercent, 0)
        XCTAssertNil(s.topCategory)
        XCTAssertEqual(s.concentration, .balanced)
    }
}

final class DeriveFindingsTests: XCTestCase {
    private func planSummary(completion: Int = 80, missed: Int = 0,
                             rows: [CategoryBreakdownRow] = []) -> PeriodSummary {
        PeriodSummary(
            period: .day, plannedMinutes: 100, actualMinutes: completion,
            missedMinutes: 0, completionPercent: completion, driftMinutes: 0,
            loggedCount: 1, missedCount: missed, totalCount: 1 + missed,
            categoryRows: rows)
    }

    func testOrderingCriticalToInfo() {
        let summary = planSummary(completion: 40, missed: 0)
        let ctx = FindingsContext(
            demandingMinutes: 6 * 60 + 30,   // > 5h by 1.5h -> critical overload
            totalScheduledMinutes: 0,
            sleepReadiness: .rested,          // info
            bestHabitStreak: 10)              // info
        let findings = deriveFindings(summary: summary, context: ctx)
        XCTAssertFalse(findings.isEmpty)
        // Must be sorted non-decreasing by severity.
        for i in 1..<findings.count {
            XCTAssertLessThanOrEqual(findings[i - 1].severity.rawValue, findings[i].severity.rawValue)
        }
        XCTAssertEqual(findings.first?.severity, .critical)
        XCTAssertEqual(findings.last?.severity, .info)
    }

    func testDepletedSleepIsCritical() {
        let findings = deriveFindings(
            summary: planSummary(completion: 90),
            context: FindingsContext(sleepReadiness: .depleted))
        XCTAssertTrue(findings.contains { $0.id == "sleep.depleted" && $0.severity == .critical })
    }

    func testLowAdherenceCritical() {
        let findings = deriveFindings(
            summary: planSummary(completion: 90),
            context: FindingsContext(medicationAdherence: 0.3))
        XCTAssertTrue(findings.contains { $0.id == "medication.low" && $0.severity == .critical })
    }

    func testDeterministicOutput() {
        let summary = planSummary(completion: 30, missed: 4)
        let ctx = FindingsContext(demandingMinutes: 6 * 60, sleepReadiness: .depleted, bestHabitStreak: 8)
        let a = deriveFindings(summary: summary, context: ctx)
        let b = deriveFindings(summary: summary, context: ctx)
        XCTAssertEqual(a, b)
    }

    func testHighConcentrationWarning() {
        let rows = [
            CategoryBreakdownRow(category: "FOCUS", minutes: 80, share: 0.8, progress: 1.0),
            CategoryBreakdownRow(category: "BREAK", minutes: 20, share: 0.2, progress: 0.25),
        ]
        let findings = deriveFindings(summary: planSummary(completion: 90, rows: rows),
                                      context: FindingsContext())
        XCTAssertTrue(findings.contains { $0.id == "concentration.high" && $0.severity == .warning })
    }
}
