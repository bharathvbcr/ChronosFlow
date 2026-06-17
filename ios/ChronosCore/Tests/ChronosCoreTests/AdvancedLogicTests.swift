import XCTest
@testable import ChronosCore

private var utc: Calendar = {
    var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!; return c
}()
private func d(_ y: Int, _ m: Int, _ day: Int) -> Date {
    utc.date(from: DateComponents(year: y, month: m, day: day))!
}

final class ConflictResolveTests: XCTestCase {
    func testMovesMovableOverlapToFreeWindow() {
        let blocks = [
            ResolvableBlock(id: "fixed", startMinute: 540, durationMinutes: 120, isMovable: false), // 9-11 fixed
            ResolvableBlock(id: "move", startMinute: 600, durationMinutes: 60, isMovable: true),     // 10-11 overlaps
        ]
        let moves = resolveConflicts(blocks)
        XCTAssertEqual(moves.count, 1)
        XCTAssertEqual(moves[0].blockID, "move")
        // earliest free slot from dayStart(360) before the fixed block (360+60<=540) -> 360
        XCTAssertEqual(moves[0].toStart, 360)
    }

    func testFixedOverlapIsNotMoved() {
        let blocks = [
            ResolvableBlock(id: "a", startMinute: 540, durationMinutes: 120, isMovable: false),
            ResolvableBlock(id: "b", startMinute: 600, durationMinutes: 60, isMovable: false),
        ]
        XCTAssertTrue(resolveConflicts(blocks).isEmpty)
    }

    func testNoConflictNoMoves() {
        let blocks = [
            ResolvableBlock(id: "a", startMinute: 540, durationMinutes: 60, isMovable: true),
            ResolvableBlock(id: "b", startMinute: 660, durationMinutes: 60, isMovable: true),
        ]
        XCTAssertTrue(resolveConflicts(blocks).isEmpty)
    }
}

final class BestWindowTests: XCTestCase {
    func testPicksHighestEnergyFocusBand() {
        let samples = [
            CheckInSample(minuteOfDay: 540, energyScore: 5, focusScore: 5),  // morning
            CheckInSample(minuteOfDay: 600, energyScore: 4, focusScore: 5),  // morning
            CheckInSample(minuteOfDay: 900, energyScore: 2, focusScore: 2),  // afternoon
            CheckInSample(minuteOfDay: 960, energyScore: 2, focusScore: 3),  // afternoon
        ]
        let best = bestDeepWorkWindow(samples, minSamples: 2)
        XCTAssertEqual(best?.part, .morning)
        XCTAssertEqual(best?.averageScore ?? 0, 4.75, accuracy: 0.001)
    }

    func testRespectsMinSamples() {
        let samples = [CheckInSample(minuteOfDay: 540, energyScore: 5, focusScore: 5)]
        XCTAssertNil(bestDeepWorkWindow(samples, minSamples: 2))
    }
}

final class AdherenceTests: XCTestCase {
    func testAdherenceRate() {
        // 3 of last 5 days taken (1 dose/day) -> 0.6
        let taken = [d(2025,6,10), d(2025,6,9), d(2025,6,7)]
        let stats = adherenceStats(takenDates: taken, dosesPerDay: 1, overDays: 5,
                                   today: d(2025,6,10), calendar: utc)
        XCTAssertEqual(stats.takenCount, 3)
        XCTAssertEqual(stats.expectedCount, 5)
        XCTAssertEqual(stats.rate, 0.6, accuracy: 0.0001)
        XCTAssertEqual(stats.missedCount, 2)
    }

    func testRefillProjection() {
        XCTAssertEqual(daysUntilRefill(remainingDoses: 30, dosesPerDay: 2), 15)
        XCTAssertNil(daysUntilRefill(remainingDoses: 10, dosesPerDay: 0))
    }
}

final class HabitRepairTests: XCTestCase {
    func testFindsGapInWindow() {
        // window 6:00-22:00, busy 9:00-10:00, want 30m, now 8:00 -> first gap at 480 (8:00)
        let s = suggestHabitRepair(windowStart: 360, windowEnd: 1320, durationMinutes: 30,
                                   busy: [(540, 600)], nowMinute: 480)
        XCTAssertEqual(s?.startMinute, 480)
    }

    func testSkipsPastBusyToNextGap() {
        // now 9:30 lands inside the busy 9:00-10:00 block -> next free is 10:00 (600)
        let s = suggestHabitRepair(windowStart: 360, windowEnd: 1320, durationMinutes: 30,
                                   busy: [(540, 600)], nowMinute: 570)
        XCTAssertEqual(s?.startMinute, 600)
    }

    func testNilWhenWindowExhausted() {
        let s = suggestHabitRepair(windowStart: 360, windowEnd: 400, durationMinutes: 60,
                                   busy: [], nowMinute: 360)
        XCTAssertNil(s)
    }
}

final class DialDragTests: XCTestCase {
    func testMovePreservesDurationAndSnaps() {
        let e = dragMove(currentStart: 540, durationMinutes: 60, deltaMinutes: 30, grid: 15)
        XCTAssertEqual(e.startMinute, 570)
        XCTAssertEqual(e.durationMinutes, 60)
    }

    func testMoveSnapsSmallDelta() {
        let e = dragMove(currentStart: 540, durationMinutes: 60, deltaMinutes: 7, grid: 15)
        XCTAssertEqual(e.startMinute, 540) // 547 snaps back to 540
    }

    func testMoveWrapsPastMidnight() {
        let e = dragMove(currentStart: 1430, durationMinutes: 30, deltaMinutes: 30, grid: 15)
        XCTAssertEqual(e.startMinute, 15)  // 1460 -> 20 -> snap 15
        XCTAssertEqual(e.durationMinutes, 30)
    }

    func testResizeEnd() {
        let e = dragResizeEnd(currentStart: 540, targetEndMinute: 660, grid: 15)
        XCTAssertEqual(e.startMinute, 540)
        XCTAssertEqual(e.durationMinutes, 120)
    }

    func testResizeEndClampsToMinimum() {
        let e = dragResizeEnd(currentStart: 540, targetEndMinute: 550, grid: 15, minDuration: 15)
        XCTAssertEqual(e.durationMinutes, 15) // 550 snaps to 555 -> span 15
    }

    func testResizeStart() {
        let e = dragResizeStart(currentStart: 540, durationMinutes: 120, targetStartMinute: 600, grid: 15)
        XCTAssertEqual(e.startMinute, 600)
        XCTAssertEqual(e.durationMinutes, 60) // end stays at 660
    }
}
