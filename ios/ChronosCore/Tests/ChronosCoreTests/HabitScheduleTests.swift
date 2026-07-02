import XCTest
@testable import ChronosCore

final class HabitScheduleTests: XCTestCase {
    // A Gregorian, UTC calendar so weekday/week math is deterministic across machines.
    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        cal.date(from: DateComponents(year: y, month: m, day: d))!
    }

    // MARK: - Parsing (mirrors Android parseLegacyHabitCadence + the iOS editor's display strings)

    func testParsesDisplayAndLegacyForms() {
        XCTAssertEqual(parseHabitCadence("Daily"), .daily)
        XCTAssertEqual(parseHabitCadence("DAILY"), .daily)
        XCTAssertEqual(parseHabitCadence("Weekdays"), .weekdays)
        XCTAssertEqual(parseHabitCadence("Weekends"), .weekends)
        XCTAssertEqual(parseHabitCadence("Weekly"), .weeklyInterval(interval: 1, weekdays: []))
        XCTAssertEqual(parseHabitCadence("Every 2 days"), .everyNDays(2))
        XCTAssertEqual(parseHabitCadence("every 5 days"), .everyNDays(5))
        XCTAssertEqual(parseHabitCadence("2x / week"), .quota(target: 2, period: .week, interval: 1))
        XCTAssertEqual(parseHabitCadence("3x/week"), .quota(target: 3, period: .week, interval: 1))
        XCTAssertEqual(parseHabitCadence("3 times per week"), .quota(target: 3, period: .week, interval: 1))
        XCTAssertEqual(parseHabitCadence("1x / month"), .quota(target: 1, period: .month, interval: 1))
        XCTAssertEqual(parseHabitCadence("2 times every 2 weeks"), .quota(target: 2, period: .week, interval: 2))
        XCTAssertEqual(parseHabitCadence("Every 2 weeks on Mon"), .weeklyInterval(interval: 2, weekdays: [1]))
    }

    func testParsesWeekdaySets() {
        XCTAssertEqual(parseHabitCadence("Mon/Wed/Fri"), .selectedWeekdays([1, 3, 5]))
        XCTAssertEqual(parseHabitCadence("Tue/Thu"), .selectedWeekdays([2, 4]))
    }

    func testUnknownFallsBackToDaily() {
        XCTAssertEqual(parseHabitCadence("whenever"), .daily)
        XCTAssertEqual(parseHabitCadence(""), .daily)
    }

    // MARK: - Scheduled due-derivation

    func testWeekdaysAndWeekends() {
        // 2026-07-01 is a Wednesday; 2026-07-04 a Saturday.
        XCTAssertTrue(habitIsDue(cadence: "Weekdays", on: date(2026, 7, 1), completions: [], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "Weekdays", on: date(2026, 7, 4), completions: [], calendar: cal))
        XCTAssertTrue(habitIsDue(cadence: "Weekends", on: date(2026, 7, 4), completions: [], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "Weekends", on: date(2026, 7, 1), completions: [], calendar: cal))
    }

    func testSelectedWeekdays() {
        // Mon/Wed/Fri: due Wed 7/1, not due Thu 7/2.
        XCTAssertTrue(habitIsDue(cadence: "Mon/Wed/Fri", on: date(2026, 7, 1), completions: [], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "Mon/Wed/Fri", on: date(2026, 7, 2), completions: [], calendar: cal))
    }

    func testEveryNDaysAnchorsOnEarliestCompletion() {
        let anchor = date(2026, 7, 1)   // first completion
        // Every 3 days from 7/1: due 7/1, 7/4, 7/7; not 7/2, 7/3.
        XCTAssertTrue(habitIsDue(cadence: "Every 3 days", on: date(2026, 7, 4), completions: [anchor], calendar: cal))
        XCTAssertTrue(habitIsDue(cadence: "Every 3 days", on: date(2026, 7, 7), completions: [anchor], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "Every 3 days", on: date(2026, 7, 2), completions: [anchor], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "Every 3 days", on: date(2026, 7, 3), completions: [anchor], calendar: cal))
    }

    func testEveryNDaysWithNoHistoryIsDueToStart() {
        XCTAssertTrue(habitIsDue(cadence: "Every 4 days", on: date(2026, 7, 10), completions: [], calendar: cal))
    }

    func testWeeklyIntervalEveryTwoWeeks() {
        // Anchor Wed 7/1 (week of Mon 6/29). Every 2 weeks, no explicit weekday → due on anchor's
        // weekday (Wed) in on-weeks only.
        let anchor = date(2026, 7, 1)
        XCTAssertTrue(habitIsDue(cadence: "Every 2 weeks", on: date(2026, 7, 1), completions: [anchor], calendar: cal))
        XCTAssertTrue(habitIsDue(cadence: "Every 2 weeks", on: date(2026, 7, 15), completions: [anchor], calendar: cal)) // +2wk Wed
        XCTAssertFalse(habitIsDue(cadence: "Every 2 weeks", on: date(2026, 7, 8), completions: [anchor], calendar: cal)) // off week
        XCTAssertFalse(habitIsDue(cadence: "Every 2 weeks", on: date(2026, 7, 16), completions: [anchor], calendar: cal)) // on week, wrong weekday
    }

    // MARK: - Quota due-derivation

    func testQuotaDueUntilTargetHitThisWeek() {
        // 3x/week. Week of Mon 6/29..Sun 7/5. Two completions this week → still due; a third → not.
        let twoThisWeek = [date(2026, 6, 30), date(2026, 7, 1)]
        XCTAssertTrue(habitIsDue(cadence: "3x / week", on: date(2026, 7, 2), completions: twoThisWeek, calendar: cal))
        let threeThisWeek = twoThisWeek + [date(2026, 7, 2)]
        XCTAssertFalse(habitIsDue(cadence: "3x / week", on: date(2026, 7, 3), completions: threeThisWeek, calendar: cal))
    }

    func testQuotaResetsNextPeriod() {
        // Last week's completions don't count against this week's quota.
        let lastWeek = [date(2026, 6, 22), date(2026, 6, 23), date(2026, 6, 24)]
        XCTAssertTrue(habitIsDue(cadence: "3x / week", on: date(2026, 7, 1), completions: lastWeek, calendar: cal))
    }

    func testQuotaMonthly() {
        XCTAssertTrue(habitIsDue(cadence: "1x / month", on: date(2026, 7, 15), completions: [], calendar: cal))
        XCTAssertFalse(habitIsDue(cadence: "1x / month", on: date(2026, 7, 20),
                                  completions: [date(2026, 7, 3)], calendar: cal))
        // A completion in a prior month doesn't satisfy July.
        XCTAssertTrue(habitIsDue(cadence: "1x / month", on: date(2026, 7, 20),
                                 completions: [date(2026, 6, 30)], calendar: cal))
    }
}
