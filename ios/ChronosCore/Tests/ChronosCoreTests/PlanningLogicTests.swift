import XCTest
@testable import ChronosCore

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()
private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d))!
}

final class RecurrenceTests: XCTestCase {
    func testDailyEveryOtherDay() {
        let rule = RecurrenceRule(frequency: .daily, interval: 2)
        let occ = expandRecurrence(rule, from: day(2025, 1, 1), to: day(2025, 1, 7), calendar: utcCal)
        XCTAssertEqual(occ, [day(2025,1,1), day(2025,1,3), day(2025,1,5), day(2025,1,7)])
    }

    func testDailyWithCount() {
        let rule = RecurrenceRule(frequency: .daily, interval: 1, count: 3)
        let occ = expandRecurrence(rule, from: day(2025, 1, 1), to: day(2025, 12, 31), calendar: utcCal)
        XCTAssertEqual(occ.count, 3)
        XCTAssertEqual(occ.last, day(2025, 1, 3))
    }

    func testWeeklyMonWedFri() {
        // Jan 1 2025 is a Wednesday. weekday: Sun=1…Sat=7, so Mon=2, Wed=4, Fri=6.
        let rule = RecurrenceRule(frequency: .weekly, interval: 1, weekdays: [2, 4, 6])
        let occ = expandRecurrence(rule, from: day(2025, 1, 1), to: day(2025, 1, 10), calendar: utcCal)
        XCTAssertTrue(occ.contains(day(2025, 1, 1)))  // Wed
        XCTAssertTrue(occ.contains(day(2025, 1, 3)))  // Fri
        XCTAssertTrue(occ.contains(day(2025, 1, 6)))  // Mon
        XCTAssertEqual(occ.count, 5)
    }

    func testMonthlyClampsShortMonths() {
        let rule = RecurrenceRule(frequency: .monthly, interval: 1)
        let occ = expandRecurrence(rule, from: day(2025, 1, 31), to: day(2025, 3, 31), calendar: utcCal)
        XCTAssertEqual(occ, [day(2025,1,31), day(2025,2,28), day(2025,3,31)])
    }
}

final class HabitStreakTests: XCTestCase {
    func testConsecutiveStreak() {
        let today = day(2025, 6, 10)
        let dates = [day(2025,6,10), day(2025,6,9), day(2025,6,8)]
        XCTAssertEqual(currentStreak(completionDates: dates, today: today, calendar: utcCal), 3)
    }

    func testTodayIncompleteDoesNotBreakStreak() {
        let today = day(2025, 6, 10)
        let dates = [day(2025,6,9), day(2025,6,8)] // today not yet done
        XCTAssertEqual(currentStreak(completionDates: dates, today: today, calendar: utcCal), 2)
    }

    func testGapBreaksStreak() {
        let today = day(2025, 6, 10)
        let dates = [day(2025,6,10), day(2025,6,8)] // missing the 9th
        XCTAssertEqual(currentStreak(completionDates: dates, today: today, calendar: utcCal), 1)
    }

    func testCompletionRate() {
        let today = day(2025, 6, 10)
        let dates = [day(2025,6,10), day(2025,6,8), day(2025,6,4)] // 3 of trailing 7
        XCTAssertEqual(completionRate(completionDates: dates, window: 7, today: today, calendar: utcCal),
                       3.0/7.0, accuracy: 0.0001)
    }
}

final class DailyReviewTests: XCTestCase {
    func testExecutionScoreAndDrift() {
        let blocks = [
            ReviewBlock(plannedStart: 540, plannedDuration: 60, actualStart: 550, actualEnd: 615), // logged, +10 drift
            ReviewBlock(plannedStart: 660, plannedDuration: 30, actualStart: 650, actualEnd: 690), // logged, -10 -> 10 drift
            ReviewBlock(plannedStart: 720, plannedDuration: 45),                                    // not logged
        ]
        let review = computeDailyReview(blocks)
        XCTAssertEqual(review.totalCount, 3)
        XCTAssertEqual(review.loggedCount, 2)
        XCTAssertEqual(review.executionScore, 2.0/3.0, accuracy: 0.0001)
        XCTAssertEqual(review.plannedMinutes, 135)
        XCTAssertEqual(review.actualMinutes, 65 + 40)
        XCTAssertEqual(review.startDriftMinutes, 10)
    }

    func testEmptyDay() {
        let review = computeDailyReview([])
        XCTAssertEqual(review.executionScore, 0)
        XCTAssertEqual(review.totalCount, 0)
    }
}

final class GapFillTests: XCTestCase {
    func testPacksByPriorityIntoWindows() {
        let windows = [
            FreeWindow(startMinute: 540, durationMinutes: 90),   // 9:00, 90m
            FreeWindow(startMinute: 780, durationMinutes: 60),   // 13:00, 60m
        ]
        let tasks = [
            PendingTask(id: "a", title: "Low 30", priority: 1, durationMinutes: 30),
            PendingTask(id: "b", title: "High 60", priority: 3, durationMinutes: 60),
            PendingTask(id: "c", title: "Med 30", priority: 2, durationMinutes: 30),
        ]
        let s = gapFill(tasks: tasks, windows: windows)
        // High(60)@540 + Med(30)@600 exactly fill window1 (90m); Low(30) overflows to window2 @780.
        XCTAssertEqual(s.count, 3)
        XCTAssertEqual(s[0].taskID, "b"); XCTAssertEqual(s[0].startMinute, 540)
        XCTAssertEqual(s[1].taskID, "c"); XCTAssertEqual(s[1].startMinute, 600)
        XCTAssertEqual(s[2].taskID, "a"); XCTAssertEqual(s[2].startMinute, 780)
    }

    func testOverflowFallsToNextWindowOrDrops() {
        let windows = [FreeWindow(startMinute: 540, durationMinutes: 60)]
        let tasks = [
            PendingTask(id: "x", title: "Fills it", priority: 2, durationMinutes: 60),
            PendingTask(id: "y", title: "No room", priority: 1, durationMinutes: 30),
        ]
        let s = gapFill(tasks: tasks, windows: windows)
        XCTAssertEqual(s.map(\.taskID), ["x"]) // y dropped, no room
    }
}
