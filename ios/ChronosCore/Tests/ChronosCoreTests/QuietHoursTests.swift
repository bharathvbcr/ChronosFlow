import XCTest
@testable import ChronosCore

final class QuietHoursTests: XCTestCase {

    // MARK: isQuiet — wrapping window 22:00 → 07:00

    func testWrappingWindowQuietBeforeMidnight() {
        XCTAssertTrue(QuietHours.isQuiet(minute: 23 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testWrappingWindowQuietAfterMidnight() {
        XCTAssertTrue(QuietHours.isQuiet(minute: 3 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testWrappingWindowStartIsQuiet() {
        XCTAssertTrue(QuietHours.isQuiet(minute: 22 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testWrappingWindowEndIsAllowed() {
        // Half-open: 07:00 is the first allowed minute.
        XCTAssertFalse(QuietHours.isQuiet(minute: 7 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testWrappingWindowMiddayAllowed() {
        XCTAssertFalse(QuietHours.isQuiet(minute: 12 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    // MARK: isQuiet — same-day window 13:00 → 14:00

    func testSameDayWindowInside() {
        XCTAssertTrue(QuietHours.isQuiet(minute: 13 * 60 + 30, startMinute: 13 * 60, endMinute: 14 * 60))
    }

    func testSameDayWindowOutside() {
        XCTAssertFalse(QuietHours.isQuiet(minute: 15 * 60, startMinute: 13 * 60, endMinute: 14 * 60))
        XCTAssertFalse(QuietHours.isQuiet(minute: 12 * 60, startMinute: 13 * 60, endMinute: 14 * 60))
    }

    // MARK: isQuiet — empty window (start == end) never quiet

    func testEmptyWindowNeverQuiet() {
        XCTAssertFalse(QuietHours.isQuiet(minute: 9 * 60, startMinute: 9 * 60, endMinute: 9 * 60))
        XCTAssertFalse(QuietHours.isQuiet(minute: 0, startMinute: 0, endMinute: 0))
    }

    // MARK: nextAllowedMinute

    func testNextAllowedShiftsQuietToWindowEnd() {
        XCTAssertEqual(
            QuietHours.nextAllowedMinute(minute: 23 * 60, startMinute: 22 * 60, endMinute: 7 * 60),
            7 * 60)
        XCTAssertEqual(
            QuietHours.nextAllowedMinute(minute: 3 * 60, startMinute: 22 * 60, endMinute: 7 * 60),
            7 * 60)
    }

    func testNextAllowedLeavesNonQuietUnchanged() {
        XCTAssertEqual(
            QuietHours.nextAllowedMinute(minute: 12 * 60, startMinute: 22 * 60, endMinute: 7 * 60),
            12 * 60)
    }

    func testNextAllowedNormalizesInput() {
        // -30 normalizes to 23:30, which is quiet → shifts to 07:00.
        XCTAssertEqual(
            QuietHours.nextAllowedMinute(minute: -30, startMinute: 22 * 60, endMinute: 7 * 60),
            7 * 60)
    }

    // MARK: nextAllowedRollsToNextDay

    func testRollsToNextDayForPreMidnightQuiet() {
        XCTAssertTrue(QuietHours.nextAllowedRollsToNextDay(minute: 23 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testNoRollForPostMidnightQuiet() {
        // 03:00 → 07:00 is the same calendar day.
        XCTAssertFalse(QuietHours.nextAllowedRollsToNextDay(minute: 3 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testNoRollWhenNotQuiet() {
        XCTAssertFalse(QuietHours.nextAllowedRollsToNextDay(minute: 12 * 60, startMinute: 22 * 60, endMinute: 7 * 60))
    }

    func testNoRollForSameDayWindow() {
        XCTAssertFalse(QuietHours.nextAllowedRollsToNextDay(minute: 13 * 60 + 30, startMinute: 13 * 60, endMinute: 14 * 60))
    }
}
