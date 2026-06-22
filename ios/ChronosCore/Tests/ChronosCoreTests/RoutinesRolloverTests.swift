import XCTest
@testable import ChronosCore

/// Day-rollover parity tests for routine instantiation. Mirrors the Android
/// ApplyRoutineToDateUseCaseTest cases (core/domain/.../ApplyRoutineToDateUseCaseTest.kt:20-75),
/// which use Math.floorDiv/floorMod so midnight-crossing steps land on the NEXT date rather than
/// folding back to the same day.
final class RoutinesRolloverTests: XCTestCase {

    /// A deterministic UTC calendar — never read the wall clock, never mutated (`let`).
    private let utc: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private func date(_ y: Int, _ m: Int, _ d: Int) -> Date {
        var comps = DateComponents()
        comps.year = y; comps.month = m; comps.day = d
        return utc.date(from: comps)!
    }

    // MARK: - floorDiv / floorMod primitives

    func testFloorDivMatchesMathFloorDiv() {
        XCTAssertEqual(chronosFloorDiv(0, 1440), 0)
        XCTAssertEqual(chronosFloorDiv(1439, 1440), 0)
        XCTAssertEqual(chronosFloorDiv(1440, 1440), 1)
        XCTAssertEqual(chronosFloorDiv(1500, 1440), 1)
        XCTAssertEqual(chronosFloorDiv(2880, 1440), 2)
        XCTAssertEqual(chronosFloorDiv(2881, 1440), 2)
        // Negative anchors round toward negative infinity (NOT toward zero like `/`).
        XCTAssertEqual(chronosFloorDiv(-1, 1440), -1)
        XCTAssertEqual(chronosFloorDiv(-1440, 1440), -1)
        XCTAssertEqual(chronosFloorDiv(-1441, 1440), -2)
    }

    func testFloorModMatchesMathFloorMod() {
        XCTAssertEqual(chronosFloorMod(0, 1440), 0)
        XCTAssertEqual(chronosFloorMod(1439, 1440), 1439)
        XCTAssertEqual(chronosFloorMod(1440, 1440), 0)
        XCTAssertEqual(chronosFloorMod(1500, 1440), 60)
        XCTAssertEqual(chronosFloorMod(2880, 1440), 0)
        // Negative result is normalized to the positive 0..1439 range.
        XCTAssertEqual(chronosFloorMod(-1, 1440), 1439)
        XCTAssertEqual(chronosFloorMod(-30, 1440), 1410)
        XCTAssertEqual(chronosFloorMod(-1440, 1440), 0)
    }

    func testDayAndMinuteSplit() {
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: 360).daysOffset, 0)
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: 360).minuteOfDay, 360)
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: 1500).daysOffset, 1)
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: 1500).minuteOfDay, 60)
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: -30).daysOffset, -1)
        XCTAssertEqual(dayAndMinute(forAbsoluteMinute: -30).minuteOfDay, 1410)
    }

    // MARK: - instantiateRoutine day offsets

    func testSameDaySteps() {
        // Mirrors Android "creates one block per step at start plus offset" (start = 06:00).
        let steps = [
            RoutineStepSpec(title: "Stretch", offsetMinute: 0, durationMinutes: 10),
            RoutineStepSpec(title: "Journal", offsetMinute: 15, durationMinutes: 20),
        ]
        let blocks = instantiateRoutine(steps: steps, startMinute: 360)
        XCTAssertEqual(blocks.map(\.startMinuteOfDay), [360, 375])
        XCTAssertEqual(blocks.map(\.daysOffset), [0, 0])
    }

    /// The case the OLD test passed for the WRONG reason: it asserted 23:00 + 120m == 01:00 and was
    /// satisfied by the same-day modulo wrap, never noticing the lost day. With the fix the minute is
    /// still 60 but the block now correctly carries daysOffset == 1 (next calendar day).
    func testMidnightCrossingCarriesNextDayOffset() {
        let steps = [RoutineStepSpec(title: "Late", offsetMinute: 120, durationMinutes: 30)]
        let blocks = instantiateRoutine(steps: steps, startMinute: 23 * 60) // 23:00 + 02:00 = 25:00
        XCTAssertEqual(blocks[0].startMinuteOfDay, 60, "minute-of-day still normalizes to 01:00")
        XCTAssertEqual(blocks[0].daysOffset, 1, "but it must roll onto the NEXT day, not fold back")
    }

    /// Mirrors Android "steps crossing midnight roll onto the next date" (anchor 23:30 = minute 1410).
    func testWindDownRollsOntoNextDate() {
        let steps = [
            RoutineStepSpec(title: "Tea", offsetMinute: 0, durationMinutes: 15),
            RoutineStepSpec(title: "Lights out", offsetMinute: 60, durationMinutes: 30),
        ]
        let blocks = instantiateRoutine(steps: steps, startMinute: 1410)
        XCTAssertEqual(blocks.map(\.startMinuteOfDay), [1410, 30])
        XCTAssertEqual(blocks.map(\.daysOffset), [0, 1])
    }

    func testMultiDayRollover() {
        // 12:00 + 30h = 42h from anchor midnight = 1 full day + 18h -> anchor+1 day at 18:00.
        let steps = [RoutineStepSpec(title: "Far", offsetMinute: 30 * 60, durationMinutes: 30)]
        let blocks = instantiateRoutine(steps: steps, startMinute: 12 * 60)
        XCTAssertEqual(blocks[0].startMinuteOfDay, 18 * 60) // 18:00
        XCTAssertEqual(blocks[0].daysOffset, 1)
    }

    func testTwoFullDayRollover() {
        // 12:00 + 48h = exactly two days later at 12:00 -> daysOffset 2.
        let steps = [RoutineStepSpec(title: "Far", offsetMinute: 48 * 60, durationMinutes: 30)]
        let blocks = instantiateRoutine(steps: steps, startMinute: 12 * 60)
        XCTAssertEqual(blocks[0].startMinuteOfDay, 12 * 60) // 12:00
        XCTAssertEqual(blocks[0].daysOffset, 2)
    }

    func testNegativeOffsetRollsToPreviousDay() {
        let steps = [RoutineStepSpec(title: "Before", offsetMinute: -30, durationMinutes: 15)]
        let blocks = instantiateRoutine(steps: steps, startMinute: 0) // 00:00 - 30m = previous 23:30
        XCTAssertEqual(blocks[0].startMinuteOfDay, 1410)
        XCTAssertEqual(blocks[0].daysOffset, -1)
    }

    func testDurationClamped() {
        let steps = [
            RoutineStepSpec(title: "Zero", offsetMinute: 0, durationMinutes: 0),
            RoutineStepSpec(title: "Huge", offsetMinute: 0, durationMinutes: 5000),
        ]
        let blocks = instantiateRoutine(steps: steps, startMinute: 0)
        XCTAssertEqual(blocks[0].durationMinutes, 1)
        XCTAssertEqual(blocks[1].durationMinutes, 1440)
    }

    func testEmptyStepsProducesNoBlocks() {
        XCTAssertEqual(instantiateRoutine(steps: [], startMinute: 360).count, 0)
    }

    // MARK: - instantiateRoutineOnDates resolves concrete calendar dates

    func testOnDatesPlacesEachBlockOnCorrectDate() {
        let anchor = date(2026, 6, 11)
        let steps = [
            RoutineStepSpec(title: "Tea", offsetMinute: 0, durationMinutes: 15),
            RoutineStepSpec(title: "Lights out", offsetMinute: 60, durationMinutes: 30),
        ]
        let resolved = instantiateRoutineOnDates(
            steps: steps, startMinute: 1410, anchorDate: anchor, calendar: utc
        )
        XCTAssertNotNil(resolved)
        guard let resolved else { return }
        XCTAssertEqual(resolved.count, 2)
        XCTAssertEqual(resolved[0].block.startMinuteOfDay, 1410)
        XCTAssertEqual(resolved[0].date, date(2026, 6, 11))
        XCTAssertEqual(resolved[1].block.startMinuteOfDay, 30)
        XCTAssertEqual(resolved[1].date, date(2026, 6, 12)) // crossed midnight → next date
    }

    func testOnDatesAcrossMonthBoundary() {
        let anchor = date(2026, 6, 30) // last day of June
        let steps = [RoutineStepSpec(title: "Past midnight", offsetMinute: 120, durationMinutes: 20)]
        let resolved = instantiateRoutineOnDates(
            steps: steps, startMinute: 23 * 60, anchorDate: anchor, calendar: utc
        )
        XCTAssertEqual(resolved?.first?.date, date(2026, 7, 1))
        XCTAssertEqual(resolved?.first?.block.daysOffset, 1)
    }

    func testOnDatesNormalizesAnchorToStartOfDay() {
        // A mid-day anchor Date must still resolve relative to that day's start, not its wall time.
        let middayAnchor = utc.date(byAdding: .minute, value: 14 * 60, to: date(2026, 6, 11))!
        let steps = [RoutineStepSpec(title: "Same day", offsetMinute: 0, durationMinutes: 10)]
        let resolved = instantiateRoutineOnDates(
            steps: steps, startMinute: 360, anchorDate: middayAnchor, calendar: utc
        )
        XCTAssertEqual(resolved?.first?.date, date(2026, 6, 11))
    }
}
