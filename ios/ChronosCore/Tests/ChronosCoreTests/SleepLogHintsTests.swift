import XCTest
@testable import ChronosCore

/// Tests for the portable manual sleep-log validation hints (duration math, window/overnight tag,
/// vs-target delta, short/long-night warnings, restfulness synthesis). Pure / deterministic so they
/// run on the Windows/Linux CI that builds ChronosCore.
final class SleepLogHintsTests: XCTestCase {

    private typealias H = SleepLogHints

    private let bed11pm = 23 * 60       // 1380
    private let wake7am = 7 * 60         // 420  → 8h overnight
    private let wake5am = 5 * 60         // 300  → 6h overnight (boundary)
    private let wake4am = 4 * 60         // 240  → 5h overnight (short)

    // MARK: Duration

    func testDurationWrapsPastMidnight() {
        XCTAssertEqual(H.durationMinutes(bedMinute: bed11pm, wakeMinute: wake7am), 8 * 60)
    }

    func testDurationSameDay() {
        XCTAssertEqual(H.durationMinutes(bedMinute: 60, wakeMinute: 6 * 60), 5 * 60)
    }

    func testDurationEqualIsZero() {
        XCTAssertEqual(H.durationMinutes(bedMinute: 120, wakeMinute: 120), 0)
    }

    func testFormatDuration() {
        XCTAssertEqual(H.formatDuration(7 * 60 + 30), "7h 30m")
        XCTAssertEqual(H.formatDuration(8 * 60), "8h")
        XCTAssertEqual(H.formatDuration(45), "45m")
    }

    func testFormatClock() {
        XCTAssertEqual(H.formatClock(23 * 60), "11:00 PM")
        XCTAssertEqual(H.formatClock(7 * 60), "7:00 AM")
        XCTAssertEqual(H.formatClock(0), "12:00 AM")
        XCTAssertEqual(H.formatClock(12 * 60), "12:00 PM")
    }

    // MARK: Window + overnight

    func testWindowLabelOvernightTag() {
        XCTAssertEqual(H.windowLabel(bedMinute: bed11pm, wakeMinute: wake7am),
                       "11:00 PM → 7:00 AM (next day)")
    }

    func testWindowLabelSameDayNoTag() {
        XCTAssertEqual(H.windowLabel(bedMinute: 60, wakeMinute: 6 * 60),
                       "1:00 AM → 6:00 AM")
    }

    func testWindowLabelNilWhenUnset() {
        XCTAssertNil(H.windowLabel(bedMinute: nil, wakeMinute: wake7am))
        XCTAssertNil(H.windowLabel(bedMinute: bed11pm, wakeMinute: nil))
    }

    func testIsOvernight() {
        XCTAssertTrue(H.isOvernight(bedMinute: bed11pm, wakeMinute: wake7am))
        XCTAssertFalse(H.isOvernight(bedMinute: 60, wakeMinute: 6 * 60))
        XCTAssertFalse(H.isOvernight(bedMinute: nil, wakeMinute: nil))
    }

    func testDurationSummary() {
        XCTAssertEqual(H.durationSummary(bedMinute: bed11pm, wakeMinute: wake7am), "8h in bed")
        XCTAssertNil(H.durationSummary(bedMinute: 120, wakeMinute: 120))   // zero span
        XCTAssertNil(H.durationSummary(bedMinute: nil, wakeMinute: wake7am))
    }

    // MARK: vs-target delta

    func testVsTargetOnTarget() {
        XCTAssertEqual(H.vsTargetLabel(bedMinute: bed11pm, wakeMinute: wake7am),
                       "On target for 8 hours")
    }

    func testVsTargetShort() {
        // 11pm → 5am = 6h, 2h short.
        XCTAssertEqual(H.vsTargetLabel(bedMinute: bed11pm, wakeMinute: wake5am),
                       "2h short of 8 hours")
    }

    func testVsTargetOver() {
        // 10pm → 8am = 10h, 2h over.
        XCTAssertEqual(H.vsTargetLabel(bedMinute: 22 * 60, wakeMinute: 8 * 60),
                       "2h over 8 hours")
    }

    // MARK: Short / long-night warnings

    func testShortNightWarning() {
        XCTAssertEqual(H.durationWarning(bedMinute: bed11pm, wakeMinute: wake4am), .short)   // 5h
        XCTAssertNotNil(H.warningLabel(bedMinute: bed11pm, wakeMinute: wake4am))
    }

    func testLongNightWarning() {
        // 9pm → 7am = 10h.
        XCTAssertEqual(H.durationWarning(bedMinute: 21 * 60, wakeMinute: 7 * 60), .long)
    }

    func testHealthyNightNoWarning() {
        // 11pm → 7am = 8h.
        XCTAssertNil(H.durationWarning(bedMinute: bed11pm, wakeMinute: wake7am))
        XCTAssertNil(H.warningLabel(bedMinute: bed11pm, wakeMinute: wake7am))
        // 6h boundary is not "short" (threshold is strictly < 6h).
        XCTAssertNil(H.durationWarning(bedMinute: bed11pm, wakeMinute: wake5am))
    }

    func testEqualTimesWarn() {
        XCTAssertEqual(H.durationWarning(bedMinute: 120, wakeMinute: 120), .short)
        XCTAssertEqual(H.warningLabel(bedMinute: 120, wakeMinute: 120),
                       "Bed and wake times match — set different times.")
    }

    // MARK: Quality + restfulness

    func testQualityLabels() {
        XCTAssertEqual(H.qualityLabel(1), "Poor")
        XCTAssertEqual(H.qualityLabel(3), "Okay")
        XCTAssertEqual(H.qualityLabel(5), "Great")
        XCTAssertEqual(H.qualityLabel(0), "Poor")   // clamped
        XCTAssertEqual(H.qualityLabel(9), "Great")  // clamped
    }

    func testRestfulness() {
        XCTAssertEqual(H.restfulnessLabel(quality: 5, interruptions: 4), "Disrupted night")
        XCTAssertEqual(H.restfulnessLabel(quality: 2, interruptions: 0), "Rough night")
        XCTAssertEqual(H.restfulnessLabel(quality: 5, interruptions: 0), "Restful night")
        XCTAssertEqual(H.restfulnessLabel(quality: 3, interruptions: 2), "Average night")
    }
}
