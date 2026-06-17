import XCTest
@testable import ChronosCore

final class DialMathTests: XCTestCase {
    let dial = DialMath()

    func testMinuteToAngleAnchors() {
        XCTAssertEqual(dial.minuteToAngle(0), -90, accuracy: 0.001)    // midnight at top
        XCTAssertEqual(dial.minuteToAngle(360), 0, accuracy: 0.001)    // 6:00 at right
        XCTAssertEqual(dial.minuteToAngle(720), 90, accuracy: 0.001)   // noon at bottom
        XCTAssertEqual(dial.minuteToAngle(1080), 180, accuracy: 0.001) // 18:00 at left
    }

    func testAngleToMinuteRoundTrips() {
        for minute in stride(from: 0, to: 1440, by: 37) {
            let back = dial.angleToMinute(dial.minuteToAngle(minute))
            XCTAssertEqual(back, minute, "round trip failed for \(minute)")
        }
    }

    func testSnap() {
        XCTAssertEqual(dial.snap(67, grid: 15), 60)
        XCTAssertEqual(dial.snap(68, grid: 15), 75)
        XCTAssertEqual(dial.snap(1439, grid: 15), 0)   // wraps
        XCTAssertEqual(dial.snap(-1, grid: 5), 0)      // normalizes negatives
    }

    func testSweep() {
        XCTAssertEqual(dial.sweepDegrees(durationMinutes: 1440), 360, accuracy: 0.001)
        XCTAssertEqual(dial.sweepDegrees(durationMinutes: 720), 180, accuracy: 0.001)
        XCTAssertEqual(dial.sweepDegrees(durationMinutes: 0), dial.sweepDegrees(durationMinutes: 1)) // clamps to >=1
    }
}

final class SleepReadinessTests: XCTestCase {
    func testRatingLeads() {
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(sleepQuality: 5)), .rested)
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(sleepQuality: 1)), .depleted)
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(sleepQuality: 3)), .normal)
    }

    func testHeavyInterruptionDepletes() {
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(sleepQuality: 5, interruptedCount: 3)), .depleted)
    }

    func testDurationFallback() {
        // ~5h -> depleted, ~8h -> rested
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(actualStartMinute: 0, actualEndMinute: 300)), .depleted)
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight(actualStartMinute: 1380, actualEndMinute: 480)), .rested) // 23:00->08:00 = 9h
    }

    func testUnknownIsNoOp() {
        XCTAssertEqual(deriveSleepReadiness(lastNight: nil), .unknown)
        XCTAssertEqual(deriveSleepReadiness(lastNight: SleepNight()), .unknown) // no rating, no window
    }
}

final class PlannerMathTests: XCTestCase {
    func testConflictsDetected() {
        let blocks = [
            BlockSpan(id: "a", startMinute: 540, durationMinutes: 120), // 9:00-11:00
            BlockSpan(id: "b", startMinute: 600, durationMinutes: 60),  // 10:00-11:00 (overlaps a)
            BlockSpan(id: "c", startMinute: 720, durationMinutes: 30),  // 12:00-12:30 (clear)
        ]
        let conflicts = PlannerMath.conflicts(in: blocks)
        XCTAssertEqual(conflicts.count, 1)
        XCTAssertEqual(conflicts.first?.overlapMinutes, 60)
    }

    func testFreeWindows() {
        let blocks = [
            BlockSpan(id: "a", startMinute: 540, durationMinutes: 60),  // 9:00-10:00
            BlockSpan(id: "b", startMinute: 720, durationMinutes: 60),  // 12:00-13:00
        ]
        // Day 6:00-23:00. Expect gaps: 6:00-9:00, 10:00-12:00, 13:00-23:00.
        let windows = PlannerMath.freeWindows(in: blocks)
        XCTAssertEqual(windows.count, 3)
        XCTAssertEqual(windows[0], FreeWindow(startMinute: 360, durationMinutes: 180))
        XCTAssertEqual(windows[1], FreeWindow(startMinute: 600, durationMinutes: 120))
        XCTAssertEqual(windows[2], FreeWindow(startMinute: 780, durationMinutes: 600))
    }

    func testClockTimeFormatsWithoutCrashing() {
        XCTAssertFalse(clockTime(545).isEmpty)
        XCTAssertFalse(clockTime(0).isEmpty)
    }
}
