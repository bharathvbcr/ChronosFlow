import XCTest
@testable import ChronosCore

/// Tests for the offline add-task smart-fill parser. A fixed, deterministic `now` is used
/// (Wednesday 2026-06-10 09:00 UTC) with a UTC calendar so weekday/relative-date math is stable
/// on any host OS (including the Windows/Linux CI that builds ChronosCore).
final class SmartFillTests: XCTestCase {

    /// Wednesday, 2026-06-10. weekday(.weekday) for Wed == 4 (Sun=1).
    private let now: Date = {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        var c = DateComponents()
        c.year = 2026; c.month = 6; c.day = 10; c.hour = 9; c.minute = 0
        return cal.date(from: c)!
    }()

    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private func parse(_ text: String) -> SmartFillResult {
        parseSmartFill(text, now: now, calendar: cal)
    }

    /// Convenience: the start-of-day Date `days` after the fixed today.
    private func day(_ offsetDays: Int) -> Date {
        cal.date(byAdding: .day, value: offsetDays, to: cal.startOfDay(for: now))!
    }

    private func ymd(_ date: Date?) -> DateComponents? {
        guard let date else { return nil }
        return cal.dateComponents([.year, .month, .day], from: date)
    }

    // MARK: Dates

    func testToday() {
        let r = parse("Pay rent today")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(0)))
        XCTAssertTrue(r.detections.contains("Due today"))
    }

    func testTomorrow() {
        let r = parse("Call dentist tomorrow")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(1)))
        XCTAssertTrue(r.detections.contains("Due tomorrow"))
    }

    func testDayAfterTomorrow() {
        let r = parse("Submit form day after tomorrow")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(2)))
    }

    func testInNDays() {
        let r = parse("Renew passport in 3 days")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(3)))
    }

    func testInTwoWeeks() {
        let r = parse("Dentist checkup in two weeks")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(14)))
    }

    func testNextTuesday() {
        // Wed 6/10 → next Tuesday is 6/16.
        let r = parse("Team sync next tuesday")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(6)))
        XCTAssertTrue(r.detections.contains { $0.hasPrefix("Due") })
    }

    func testThisFridayUsesNextOrSame() {
        // Wed 6/10 → this Friday is 6/12.
        let r = parse("Report by friday")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(2)))
    }

    func testThisWeekend() {
        // nextOrSame Saturday from Wed 6/10 is 6/13.
        let r = parse("Clean garage this weekend")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(3)))
        XCTAssertTrue(r.detections.contains("Due this weekend"))
    }

    func testEndOfMonth() {
        let r = parse("File taxes end of month")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(20)))   // June has 30 days → 6/30 is 20 days out.
    }

    func testNextWeekIsNextMonday() {
        // Wed 6/10 → next Monday is 6/15.
        let r = parse("Plan sprint next week")
        XCTAssertEqual(ymd(r.dueDate), ymd(day(5)))
    }

    func testNextMonth() {
        let r = parse("Review budget next month")
        XCTAssertEqual(ymd(r.dueDate), ymd(cal.date(byAdding: .month, value: 1, to: cal.startOfDay(for: now))))
    }

    // MARK: Times

    func testExplicitClockTime() {
        let r = parse("Standup at 9:30")
        XCTAssertEqual(r.timeMinuteOfDay, 9 * 60 + 30)
        XCTAssertTrue(r.detections.contains("9:30 AM"))
    }

    func testClockTimePM() {
        let r = parse("Gym at 6 pm")
        XCTAssertEqual(r.timeMinuteOfDay, 18 * 60)
        XCTAssertTrue(r.detections.contains("6:00 PM"))
    }

    func testNoon() {
        let r = parse("Lunch meeting at noon")
        XCTAssertEqual(r.timeMinuteOfDay, 12 * 60)
    }

    func testFirstThing() {
        let r = parse("Email boss first thing")
        XCTAssertEqual(r.timeMinuteOfDay, 8 * 60)
    }

    func testEarlyMorning() {
        let r = parse("Run early morning")
        XCTAssertEqual(r.timeMinuteOfDay, 7 * 60)
    }

    // MARK: Durations

    func testMinutesDuration() {
        let r = parse("Workout 30 min")
        XCTAssertEqual(r.durationMinutes, 30)
        XCTAssertTrue(r.detections.contains("30 min"))
    }

    func testAnHourDuration() {
        let r = parse("Study an hour")
        XCTAssertEqual(r.durationMinutes, 60)
        XCTAssertTrue(r.detections.contains("1 hour"))
    }

    func testHalfAnHour() {
        let r = parse("Meditate half an hour")
        XCTAssertEqual(r.durationMinutes, 30)
    }

    func testHoursAndMinutes() {
        let r = parse("Deep work 1 hour and 30 minutes")
        XCTAssertEqual(r.durationMinutes, 90)
    }

    // MARK: Priority

    func testUrgentIsHigh() {
        let r = parse("Fix prod urgent")
        XCTAssertEqual(r.priority, .high)
        XCTAssertTrue(r.detections.contains("High priority"))
    }

    func testAsapIsHigh() {
        XCTAssertEqual(parse("Reply to client asap").priority, .high)
    }

    func testLowPriority() {
        let r = parse("Organize photos someday")
        XCTAssertEqual(r.priority, .low)
        XCTAssertTrue(r.detections.contains("Low priority"))
    }

    func testP1IsHigh() {
        XCTAssertEqual(parse("Ship release p1").priority, .high)
    }

    // MARK: Recurrence

    func testEveryDay() {
        let r = parse("Take vitamins every day")
        XCTAssertEqual(r.recurrence?.frequency, .daily)
        XCTAssertEqual(r.recurrence?.interval, 1)
        XCTAssertTrue(r.detections.contains("Every day"))
    }

    func testWeekly() {
        let r = parse("Weekly review")
        XCTAssertEqual(r.recurrence?.frequency, .weekly)
        XCTAssertEqual(r.recurrence?.interval, 1)
    }

    func testEvery2Weeks() {
        let r = parse("Pay cleaner every 2 weeks")
        XCTAssertEqual(r.recurrence?.frequency, .weekly)
        XCTAssertEqual(r.recurrence?.interval, 2)
    }

    func testBiweeklyIsIntervalTwo() {
        XCTAssertEqual(parse("Biweekly sync").recurrence?.interval, 2)
    }

    func testEveryMonday() {
        let r = parse("Every monday standup")
        XCTAssertEqual(r.recurrence?.frequency, .weekly)
        XCTAssertEqual(r.recurrence?.weekdays, [2])   // Monday == 2
        XCTAssertTrue(r.detections.contains("Every Mon"))
    }

    func testWeekdays() {
        let r = parse("Journal weekdays")
        XCTAssertEqual(r.recurrence?.frequency, .weekly)
        XCTAssertEqual(r.recurrence?.weekdays, [2, 3, 4, 5, 6])
        XCTAssertTrue(r.detections.contains("Weekdays"))
    }

    func testMonthly() {
        let r = parse("Monthly report")
        XCTAssertEqual(r.recurrence?.frequency, .monthly)
        XCTAssertEqual(r.recurrence?.interval, 1)
    }

    func testQuarterlyIsIntervalThree() {
        let r = parse("Quarterly taxes")
        XCTAssertEqual(r.recurrence?.frequency, .monthly)
        XCTAssertEqual(r.recurrence?.interval, 3)
    }

    // MARK: Contact / action

    func testEmailAction() {
        let r = parse("Email john@example.com about invoice")
        XCTAssertEqual(r.actionHint?.kind, .email)
        XCTAssertEqual(r.actionHint?.value, "john@example.com")
        XCTAssertTrue(r.detections.contains("Email john@example.com"))
    }

    func testPhoneAction() {
        let r = parse("Call 555-123-4567 about quote")
        XCTAssertEqual(r.actionHint?.kind, .phone)
        XCTAssertNotNil(r.actionHint?.value)
    }

    func testUrlAction() {
        let r = parse("Read https://example.com/post later")
        XCTAssertEqual(r.actionHint?.kind, .url)
        XCTAssertTrue(r.actionHint?.value.contains("example.com") ?? false)
    }

    // MARK: Cleaned title

    func testCleanedTitleStripsTokens() {
        let r = parse("Pay rent tomorrow urgent")
        // "tomorrow" + "urgent" stripped → "Pay rent".
        XCTAssertEqual(r.cleanedTitle, "Pay rent")
    }

    func testCleanedTitleStripsLeadingFiller() {
        let r = parse("remind me to buy milk")
        XCTAssertEqual(r.cleanedTitle, "Buy milk")
    }

    func testShortTitleNotStrippedToEmpty() {
        // "urgent" is parseable (priority → high), but stripping it would empty the title — so the
        // cleaned title falls back to the original capture rather than going blank.
        let r = parse("urgent")
        XCTAssertEqual(r.priority, .high)
        XCTAssertEqual(r.cleanedTitle, "urgent")
    }

    func testBelowMinLengthIsNoOp() {
        // Fewer than 3 chars: the parser short-circuits entirely.
        let r = parse("hi")
        XCTAssertNil(r.priority)
        XCTAssertFalse(r.hasDetection)
        XCTAssertEqual(r.cleanedTitle, "hi")
    }

    func testNoDetectionLeavesTitleIntact() {
        let r = parse("Buy groceries")
        XCTAssertFalse(r.hasDetection)
        XCTAssertEqual(r.cleanedTitle, "Buy groceries")
        XCTAssertTrue(r.detections.isEmpty)
    }

    // MARK: Combined mega-case

    func testMegaCase() {
        let r = parse("Email mom@home.com about taxes tomorrow at 9am urgent every week")
        // Date: tomorrow (6/11) with the time folded in.
        XCTAssertEqual(ymd(r.dueDate), ymd(day(1)))
        XCTAssertEqual(r.timeMinuteOfDay, 9 * 60)
        XCTAssertEqual(r.priority, .high)
        XCTAssertEqual(r.recurrence?.frequency, .weekly)
        XCTAssertEqual(r.actionHint?.kind, .email)
        XCTAssertEqual(r.actionHint?.value, "mom@home.com")
        // The due Date should carry the 9:00 time-of-day.
        if let due = r.dueDate {
            XCTAssertEqual(cal.component(.hour, from: due), 9)
            XCTAssertEqual(cal.component(.minute, from: due), 0)
        } else {
            XCTFail("expected a due date")
        }
        // Cleaned title keeps the subject, drops the noise.
        XCTAssertTrue(r.cleanedTitle.lowercased().contains("taxes"))
        XCTAssertFalse(r.cleanedTitle.lowercased().contains("urgent"))
        XCTAssertFalse(r.cleanedTitle.lowercased().contains("tomorrow"))
        // Several chips surfaced.
        XCTAssertGreaterThanOrEqual(r.detections.count, 4)
    }
}
