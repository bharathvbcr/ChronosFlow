import XCTest
@testable import ChronosCore

// Tests for the centralized SleepReadinessCore facade: readiness derivation (mirroring Android's
// SleepReadinessTest), sleep-window exclusion from free windows (GapFillPlanner.excludeSleepMinutes),
// and the single-task demanding floor (ScheduleTaskIntoDayUseCase). All deterministic — no clock
// reads; the floor's "today" gate is injected via `isSameDay`.

// MARK: - Readiness derivation (centralized entry point)

final class SleepReadinessCoreDerivationTests: XCTestCase {

    private func night(
        quality: Int = 0,
        actualStartMinute: Int? = nil,
        actualEndMinute: Int? = nil,
        interruptedCount: Int = 0
    ) -> SleepNight {
        SleepNight(
            sleepQuality: quality,
            interruptedCount: interruptedCount,
            actualStartMinute: actualStartMinute,
            actualEndMinute: actualEndMinute
        )
    }

    func testMissingNightIsUnknown() {
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: nil), .unknown)
    }

    func testUnratedNightWithNoMeasurableWindowIsUnknown() {
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 0)), .unknown)
    }

    func testLowQualityRatingsAreDepleted() {
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 1)), .depleted)
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 2)), .depleted)
    }

    func testMiddleQualityRatingIsNormal() {
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 3)), .normal)
    }

    func testHighQualityRatingsAreRested() {
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 4)), .rested)
        XCTAssertEqual(SleepReadinessCore.deriveReadiness(lastNight: night(quality: 5)), .rested)
    }

    func testHeavyInterruptionsDragGoodRatingToDepleted() {
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(lastNight: night(quality: 5, interruptedCount: 3)),
            .depleted
        )
    }

    func testInterruptionThresholdIsExactlyThree() {
        // 2 interruptions on a great night stays rested; 3 tips it to depleted.
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(lastNight: night(quality: 5, interruptedCount: 2)),
            .rested
        )
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(lastNight: night(quality: 5, interruptedCount: 3)),
            .depleted
        )
    }

    func testDurationStandsInWhenUnrated() {
        // 23:00 -> 04:30 = 5h30m, under the 6h depleted floor.
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 23 * 60, actualEndMinute: 4 * 60 + 30)),
            .depleted
        )
        // 23:00 -> 06:00 = 7h, between the floors.
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 23 * 60, actualEndMinute: 6 * 60)),
            .normal
        )
        // 22:30 -> 06:30 = 8h, at or above the rested floor.
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 22 * 60 + 30, actualEndMinute: 6 * 60 + 30)),
            .rested
        )
    }

    func testDurationFloorBoundaries() {
        // Exactly 6h is NOT depleted (the floor is "< 6h").
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 0, actualEndMinute: 6 * 60)),
            .normal
        )
        // Exactly 7h30m is rested (">= 7h30m").
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 0, actualEndMinute: 7 * 60 + 30)),
            .rested
        )
    }

    func testUserRatingOutweighsLongMeasuredWindow() {
        XCTAssertEqual(
            SleepReadinessCore.deriveReadiness(
                lastNight: night(quality: 1, actualStartMinute: 22 * 60, actualEndMinute: 7 * 60)),
            .depleted
        )
    }

    func testRawFieldsOverloadMatchesNightOverload() {
        let viaFields = SleepReadinessCore.deriveReadiness(
            sleepQuality: 0, interruptedCount: 1,
            actualStartMinute: 23 * 60, actualEndMinute: 4 * 60 + 30)
        XCTAssertEqual(viaFields, .depleted)
        // Mirrors the SleepNight-based call.
        XCTAssertEqual(
            viaFields,
            SleepReadinessCore.deriveReadiness(
                lastNight: night(actualStartMinute: 23 * 60, actualEndMinute: 4 * 60 + 30,
                                 interruptedCount: 1))
        )
    }
}

// MARK: - PlannedSleepWindow.contains

final class PlannedSleepWindowTests: XCTestCase {

    func testInactiveWhenEndpointsEqual() {
        let w = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 22 * 60)
        XCTAssertFalse(w.isActive)
        XCTAssertFalse(w.contains(22 * 60))
    }

    func testDaytimeWindowContainsHalfOpenSpan() {
        // 13:00 -> 14:00 nap window.
        let w = PlannedSleepWindow(startMinute: 13 * 60, endMinute: 14 * 60)
        XCTAssertFalse(w.isOvernight)
        XCTAssertTrue(w.contains(13 * 60))        // start inclusive
        XCTAssertTrue(w.contains(13 * 60 + 59))
        XCTAssertFalse(w.contains(14 * 60))       // end exclusive
        XCTAssertFalse(w.contains(12 * 60 + 59))
    }

    func testOvernightWindowWrapsMidnight() {
        // 22:00 -> 06:00.
        let w = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertTrue(w.isOvernight)
        XCTAssertTrue(w.contains(23 * 60))        // before midnight
        XCTAssertTrue(w.contains(0))              // midnight
        XCTAssertTrue(w.contains(5 * 60 + 59))    // before wake
        XCTAssertFalse(w.contains(6 * 60))        // wake exclusive
        XCTAssertFalse(w.contains(12 * 60))       // midday
    }

    func testFromOptionalEndpoints() {
        XCTAssertNil(PlannedSleepWindow.from(plannedStartMinute: nil, plannedEndMinute: 6 * 60))
        XCTAssertNil(PlannedSleepWindow.from(plannedStartMinute: 22 * 60, plannedEndMinute: nil))
        XCTAssertEqual(
            PlannedSleepWindow.from(plannedStartMinute: 22 * 60, plannedEndMinute: 6 * 60),
            PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        )
    }
}

// MARK: - excludeSleepMinutesFromWindows

final class ExcludeSleepMinutesTests: XCTestCase {

    func testNilOrInactiveWindowReturnsInputUnchanged() {
        let windows = [FreeWindow(startMinute: 6 * 60, durationMinutes: 60)]
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: nil),
            windows
        )
        let inactive = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 22 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: inactive),
            windows
        )
    }

    func testWindowFullyOutsideSleepIsUnchanged() {
        // Free 09:00-12:00, sleep 22:00-06:00 — no overlap.
        let windows = [FreeWindow(startMinute: 9 * 60, durationMinutes: 180)]
        let sleep = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: sleep),
            windows
        )
    }

    func testWindowFullyInsideSleepIsRemoved() {
        // Free 23:00-23:30, entirely inside 22:00-06:00 sleep.
        let windows = [FreeWindow(startMinute: 23 * 60, durationMinutes: 30)]
        let sleep = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: sleep),
            []
        )
    }

    func testWindowClippedAtSleepStart() {
        // Free 20:00-23:00, sleep starts 22:00 → only 20:00-22:00 survives (120 min).
        let windows = [FreeWindow(startMinute: 20 * 60, durationMinutes: 180)]
        let sleep = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: sleep),
            [FreeWindow(startMinute: 20 * 60, durationMinutes: 120)]
        )
    }

    func testWindowClippedAtSleepEnd() {
        // Free 05:00-08:00, sleep ends 06:00 → only 06:00-08:00 survives (120 min).
        let windows = [FreeWindow(startMinute: 5 * 60, durationMinutes: 180)]
        let sleep = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: sleep),
            [FreeWindow(startMinute: 6 * 60, durationMinutes: 120)]
        )
    }

    func testDaytimeNapSplitsWindowIntoTwo() {
        // Free 12:00-16:00 with a 13:00-14:00 nap window → 12:00-13:00 and 14:00-16:00.
        let windows = [FreeWindow(startMinute: 12 * 60, durationMinutes: 240)]
        let nap = PlannedSleepWindow(startMinute: 13 * 60, endMinute: 14 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: nap),
            [
                FreeWindow(startMinute: 12 * 60, durationMinutes: 60),
                FreeWindow(startMinute: 14 * 60, durationMinutes: 120),
            ]
        )
    }

    func testOptionalEndpointsOverload() {
        let windows = [FreeWindow(startMinute: 20 * 60, durationMinutes: 180)]
        // nil schedule = unchanged.
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(
                windows, plannedStartMinute: nil, plannedEndMinute: 6 * 60),
            windows
        )
        // Concrete schedule clips like the struct overload.
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(
                windows, plannedStartMinute: 22 * 60, plannedEndMinute: 6 * 60),
            [FreeWindow(startMinute: 20 * 60, durationMinutes: 120)]
        )
    }

    func testMultipleWindowsPreserveOrder() {
        let windows = [
            FreeWindow(startMinute: 5 * 60, durationMinutes: 180),   // clipped to 06:00-08:00
            FreeWindow(startMinute: 12 * 60, durationMinutes: 60),   // untouched
            FreeWindow(startMinute: 23 * 60, durationMinutes: 30),   // fully inside sleep → gone
        ]
        let sleep = PlannedSleepWindow(startMinute: 22 * 60, endMinute: 6 * 60)
        XCTAssertEqual(
            SleepReadinessCore.excludeSleepMinutesFromWindows(windows, sleepWindow: sleep),
            [
                FreeWindow(startMinute: 6 * 60, durationMinutes: 120),
                FreeWindow(startMinute: 12 * 60, durationMinutes: 60),
            ]
        )
    }
}

// MARK: - applyReadinessFloor (single-task demanding floor)

final class ApplyReadinessFloorTests: XCTestCase {

    private let intense = EnergyIntensity.intense.rawValue   // 4
    private let low = EnergyIntensity.low.rawValue           // 1

    func testDepletedDefersDemandingTaskPastFloor() {
        // 09:00 intense 90m today → floored to 11:00.
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: 90,
            energyLevel: intense, readiness: .depleted, isSameDay: true)
        XCTAssertTrue(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 11 * 60)
    }

    func testNonDepletedReadinessIsNoOp() {
        for readiness in [SleepReadiness.normal, .rested, .unknown] {
            let r = SleepReadinessCore.applyReadinessFloor(
                currentStartMinute: 9 * 60, durationMinutes: 90,
                energyLevel: intense, readiness: readiness, isSameDay: true)
            XCTAssertFalse(r.wasDeferred, "\(readiness) should be a no-op")
            XCTAssertEqual(r.startMinute, 9 * 60)
        }
    }

    func testFutureDayIsNoOp() {
        // Last night only bears on today's plan; a future placement ignores readiness.
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: 90,
            energyLevel: intense, readiness: .depleted, isSameDay: false)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 9 * 60)
    }

    func testNonDemandingTaskIsNotFloored() {
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: 90,
            energyLevel: low, readiness: .depleted, isSameDay: true)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 9 * 60)
    }

    func testUserPreferredStartIsRespected() {
        // An explicit user choice is never overridden, even when demanding+depleted+today.
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: 90,
            energyLevel: intense, readiness: .depleted, isSameDay: true,
            hasUserPreferredStart: true)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 9 * 60)
    }

    func testAlreadyLateDemandingTaskIsUnchanged() {
        // Already at/after the floor → no movement (and never moved earlier).
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 14 * 60, durationMinutes: 60,
            energyLevel: intense, readiness: .depleted, isSameDay: true)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 14 * 60)
    }

    func testTaskExactlyAtFloorIsUnchanged() {
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 11 * 60, durationMinutes: 60,
            energyLevel: intense, readiness: .depleted, isSameDay: true)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 11 * 60)
    }

    func testSoftFallbackWhenFlooredPlacementOverrunsDay() {
        // A long demanding task that would overrun the day from 11:00 keeps its original start.
        let dayEnd = 23 * 60
        let duration = dayEnd - 11 * 60 + 1   // floor + duration = dayEnd + 1 → does not fit
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: duration,
            energyLevel: intense, readiness: .depleted, isSameDay: true,
            dayEndMinute: dayEnd)
        XCTAssertFalse(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 9 * 60)
    }

    func testFlooredPlacementThatExactlyFitsIsDeferred() {
        let dayEnd = 23 * 60
        let duration = dayEnd - 11 * 60   // floor + duration == dayEnd → fits exactly
        let r = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 9 * 60, durationMinutes: duration,
            energyLevel: intense, readiness: .depleted, isSameDay: true,
            dayEndMinute: dayEnd)
        XCTAssertTrue(r.wasDeferred)
        XCTAssertEqual(r.startMinute, 11 * 60)
    }

    func testConvenienceWrapperReturnsResolvedStart() {
        XCTAssertEqual(
            SleepReadinessCore.flooredStartMinute(
                currentStartMinute: 9 * 60, durationMinutes: 90,
                energyLevel: intense, readiness: .depleted, isSameDay: true),
            11 * 60
        )
        XCTAssertEqual(
            SleepReadinessCore.flooredStartMinute(
                currentStartMinute: 9 * 60, durationMinutes: 90,
                energyLevel: low, readiness: .depleted, isSameDay: true),
            9 * 60
        )
    }

    func testDeterministic() {
        let first = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 8 * 60, durationMinutes: 120,
            energyLevel: intense, readiness: .depleted, isSameDay: true)
        let second = SleepReadinessCore.applyReadinessFloor(
            currentStartMinute: 8 * 60, durationMinutes: 120,
            energyLevel: intense, readiness: .depleted, isSameDay: true)
        XCTAssertEqual(first, second)
    }
}
