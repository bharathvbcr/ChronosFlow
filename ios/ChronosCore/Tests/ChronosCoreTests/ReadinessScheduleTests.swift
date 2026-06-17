import XCTest
@testable import ChronosCore

final class ReadinessScheduleParamTests: XCTestCase {
    func testDemandingFloorOnlyWhenDepleted() {
        XCTAssertEqual(ReadinessSchedule.demandingTaskEarliestStartMinute(.depleted), 11 * 60)
        XCTAssertNil(ReadinessSchedule.demandingTaskEarliestStartMinute(.normal))
        XCTAssertNil(ReadinessSchedule.demandingTaskEarliestStartMinute(.rested))
        XCTAssertNil(ReadinessSchedule.demandingTaskEarliestStartMinute(.unknown))
    }

    func testSkipEnergyPeakOnlyWhenDepleted() {
        XCTAssertTrue(ReadinessSchedule.shouldSkipEnergyPeakDeferral(.depleted))
        XCTAssertFalse(ReadinessSchedule.shouldSkipEnergyPeakDeferral(.normal))
        XCTAssertFalse(ReadinessSchedule.shouldSkipEnergyPeakDeferral(.rested))
        XCTAssertFalse(ReadinessSchedule.shouldSkipEnergyPeakDeferral(.unknown))
    }

    func testFocusStretchShortensWhenDepleted() {
        XCTAssertEqual(ReadinessSchedule.focusStretchMinutes(.depleted), 60)
        XCTAssertEqual(ReadinessSchedule.focusStretchMinutes(.normal), 90)
        XCTAssertEqual(ReadinessSchedule.focusStretchMinutes(.rested), 90)
        XCTAssertEqual(ReadinessSchedule.focusStretchMinutes(.unknown), 90)
    }

    func testRecoveryBreakLengthensWhenDepleted() {
        XCTAssertEqual(ReadinessSchedule.recoveryBreakMinutes(.depleted), 25)
        XCTAssertEqual(ReadinessSchedule.recoveryBreakMinutes(.normal), 20)
        XCTAssertEqual(ReadinessSchedule.recoveryBreakMinutes(.rested), 20)
        XCTAssertEqual(ReadinessSchedule.recoveryBreakMinutes(.unknown), 20)
    }
}

final class ApplyReadinessTests: XCTestCase {
    // A 9:00 intense block plus a fixed midday meeting.
    private func sampleBlocks() -> [ReadinessPlanBlock] {
        [
            ReadinessPlanBlock(id: "intense", startMinute: 9 * 60, durationMinutes: 90,
                               energyLevel: EnergyIntensity.intense.rawValue),
            ReadinessPlanBlock(id: "light", startMinute: 8 * 60, durationMinutes: 30,
                               energyLevel: EnergyIntensity.low.rawValue),
        ]
    }

    func testDepletedShiftsIntenseBlockPastFloor() {
        let out = applyReadiness(to: sampleBlocks(), readiness: .depleted)
        let intense = out.first { $0.id == "intense" }!
        let light = out.first { $0.id == "light" }!
        XCTAssertGreaterThanOrEqual(intense.startMinute, 11 * 60)  // floored past 11:00
        XCTAssertEqual(light.startMinute, 8 * 60)                  // low-energy untouched
    }

    func testDepletedRespectsObstacleAndFindsNextSlot() {
        // 9:00 intense (90m) must move past floor; an 11:00 fixed meeting (60m) occupies 11:00-12:00,
        // so the intense block should land at 12:00.
        let blocks = [
            ReadinessPlanBlock(id: "intense", startMinute: 9 * 60, durationMinutes: 90,
                               energyLevel: EnergyIntensity.intense.rawValue),
            ReadinessPlanBlock(id: "meeting", startMinute: 11 * 60, durationMinutes: 60,
                               energyLevel: EnergyIntensity.low.rawValue, isFixed: true),
        ]
        let out = applyReadiness(to: blocks, readiness: .depleted)
        let intense = out.first { $0.id == "intense" }!
        XCTAssertEqual(intense.startMinute, 12 * 60)
        // Fixed meeting never moves.
        XCTAssertEqual(out.first { $0.id == "meeting" }!.startMinute, 11 * 60)
    }

    func testFixedDemandingBlockIsNotMoved() {
        let blocks = [
            ReadinessPlanBlock(id: "locked", startMinute: 9 * 60, durationMinutes: 60,
                               energyLevel: EnergyIntensity.max.rawValue, isFixed: true),
        ]
        let out = applyReadiness(to: blocks, readiness: .depleted)
        XCTAssertEqual(out.first!.startMinute, 9 * 60)
    }

    func testAlreadyLateDemandingBlockUnchanged() {
        let blocks = [
            ReadinessPlanBlock(id: "afternoon", startMinute: 14 * 60, durationMinutes: 60,
                               energyLevel: EnergyIntensity.intense.rawValue),
        ]
        let out = applyReadiness(to: blocks, readiness: .depleted)
        XCTAssertEqual(out.first!.startMinute, 14 * 60)
    }

    func testNonDepletedReadinessIsNoOp() {
        for r in [SleepReadiness.normal, .rested, .unknown] {
            let original = sampleBlocks()
            let out = applyReadiness(to: original, readiness: r)
            XCTAssertEqual(out.map(\.startMinute), original.map(\.startMinute), "\(r) should be a no-op")
        }
    }

    func testDeterministicWithTwoShiftedBlocks() {
        let blocks = [
            ReadinessPlanBlock(id: "a", startMinute: 8 * 60, durationMinutes: 60,
                               energyLevel: EnergyIntensity.intense.rawValue),
            ReadinessPlanBlock(id: "b", startMinute: 9 * 60, durationMinutes: 60,
                               energyLevel: EnergyIntensity.intense.rawValue),
        ]
        let out = applyReadiness(to: blocks, readiness: .depleted)
        let a = out.first { $0.id == "a" }!
        let b = out.first { $0.id == "b" }!
        // Both move past the floor and must not overlap each other.
        XCTAssertGreaterThanOrEqual(a.startMinute, 11 * 60)
        XCTAssertGreaterThanOrEqual(b.startMinute, 11 * 60)
        XCTAssertTrue(a.endMinute <= b.startMinute || b.endMinute <= a.startMinute, "shifted blocks overlap")
        // Re-running yields identical placement.
        let out2 = applyReadiness(to: blocks, readiness: .depleted)
        XCTAssertEqual(out.map(\.startMinute), out2.map(\.startMinute))
    }
}
