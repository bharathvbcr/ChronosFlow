import XCTest
@testable import ChronosCore

final class FocusPhasePlanTests: XCTestCase {

    // MARK: Canonical splits

    func test60mAt25_5_splitsIntoTwoWorkBreakCycles() {
        let phases = planFocusPhases(blockMinutes: 60, preset: .p25_5)
        XCTAssertEqual(phases, [
            FocusPhase(kind: .work, durationMinutes: 25),
            FocusPhase(kind: .break, durationMinutes: 5),
            FocusPhase(kind: .work, durationMinutes: 25),
            FocusPhase(kind: .break, durationMinutes: 5),
        ])
        // cycle = 30, 60 / 30 = 2 full cycles, no remainder.
        XCTAssertEqual(phases.reduce(0) { $0 + $1.durationMinutes }, 60)
        XCTAssertEqual(focusMinutes(in: phases), 50)
    }

    func test90mAt50_10() {
        // cycle = 60. 90 / 60 = 1 full cycle (work50 + break10), remainder 30 -> trailing focus.
        let phases = planFocusPhases(blockMinutes: 90, preset: .p50_10)
        XCTAssertEqual(phases, [
            FocusPhase(kind: .work, durationMinutes: 50),
            FocusPhase(kind: .break, durationMinutes: 10),
            FocusPhase(kind: .work, durationMinutes: 30),
        ])
        XCTAssertEqual(phases.reduce(0) { $0 + $1.durationMinutes }, 90)
        XCTAssertEqual(phases.last?.kind, .work)
    }

    func test30mAt30_5() {
        // cycle = 35; 30 < 35 -> single flat block.
        let phases = planFocusPhases(blockMinutes: 30, preset: .p30_5)
        XCTAssertEqual(phases, [FocusPhase(kind: .work, durationMinutes: 30)])
    }

    func test65mAt30_5_hasTrailingFocusRemainder() {
        // cycle = 35; 65 / 35 = 1 full cycle, remainder 30 -> trailing focus.
        let phases = planFocusPhases(blockMinutes: 65, preset: .p30_5)
        XCTAssertEqual(phases, [
            FocusPhase(kind: .work, durationMinutes: 30),
            FocusPhase(kind: .break, durationMinutes: 5),
            FocusPhase(kind: .work, durationMinutes: 30),
        ])
        XCTAssertEqual(phases.reduce(0) { $0 + $1.durationMinutes }, 65)
        XCTAssertEqual(phases.last?.kind, .work)
    }

    // MARK: Exact fits

    func testExactFit_120mAt25_5() {
        // cycle = 30; 120 / 30 = 4 full cycles exactly, no remainder, ends on a break.
        let phases = planFocusPhases(blockMinutes: 120, preset: .p25_5)
        XCTAssertEqual(phases.count, 8)
        XCTAssertEqual(phases.reduce(0) { $0 + $1.durationMinutes }, 120)
        XCTAssertEqual(phases.last?.kind, .break)
        XCTAssertEqual(focusMinutes(in: phases), 100)
    }

    func testExactSingleCycle_30mAt25_5() {
        // cycle = 30; exactly one cycle, no trailing focus.
        let phases = planFocusPhases(blockMinutes: 30, preset: .p25_5)
        XCTAssertEqual(phases, [
            FocusPhase(kind: .work, durationMinutes: 25),
            FocusPhase(kind: .break, durationMinutes: 5),
        ])
    }

    // MARK: Short blocks

    func testShortBlock_shorterThanOneWorkInterval() {
        // 10 < work(25) + break(5) -> single flat block covering the whole 10 minutes.
        let phases = planFocusPhases(blockMinutes: 10, preset: .p25_5)
        XCTAssertEqual(phases, [FocusPhase(kind: .work, durationMinutes: 10)])
    }

    func testBlockEqualToWorkButNoRoomForBreak() {
        // total(25) < work(25) + break(5) = 30 -> single flat block.
        let phases = planFocusPhases(blockMinutes: 25, preset: .p25_5)
        XCTAssertEqual(phases, [FocusPhase(kind: .work, durationMinutes: 25)])
    }

    func testZeroOrNegativeBlockClampsToOne() {
        XCTAssertEqual(planFocusPhases(blockMinutes: 0, preset: .p25_5),
                       [FocusPhase(kind: .work, durationMinutes: 1)])
        XCTAssertEqual(planFocusPhases(blockMinutes: -5, preset: .noBreaks),
                       [FocusPhase(kind: .work, durationMinutes: 1)])
    }

    // MARK: No breaks

    func testNoBreaks_isSingleWorkPhaseSpanningWholeBlock() {
        let phases = planFocusPhases(blockMinutes: 60, preset: .noBreaks)
        XCTAssertEqual(phases, [FocusPhase(kind: .work, durationMinutes: 60)])
        XCTAssertEqual(focusMinutes(in: phases), 60)
    }

    // MARK: Invariants

    func testSummedDurationsAlwaysEqualBlockMinutes() {
        for minutes in [1, 7, 25, 30, 47, 60, 90, 120, 137, 240] {
            for preset in FocusSplitPreset.allCases {
                let phases = planFocusPhases(blockMinutes: minutes, preset: preset)
                XCTAssertFalse(phases.isEmpty, "\(minutes)/\(preset) produced no phases")
                XCTAssertEqual(phases.reduce(0) { $0 + $1.durationMinutes }, max(minutes, 1),
                               "\(minutes)/\(preset) lost time")
            }
        }
    }

    func testBoundaryText() {
        XCTAssertEqual(focusPhaseBoundaryText(nextPhase: FocusPhase(kind: .break, durationMinutes: 5)),
                       "Time for a 5m break — tap to continue")
        XCTAssertEqual(focusPhaseBoundaryText(nextPhase: FocusPhase(kind: .work, durationMinutes: 25)),
                       "Back to focus for 25m — tap to continue")
    }
}
