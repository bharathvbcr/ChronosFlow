import XCTest
@testable import ChronosCore

/// Pure-logic guard for the split (Pomodoro) live-bar rendering, mirroring Android's
/// `FocusSplitSegmentTest.kt`: parsing the cross-process phase plan, sizing/ordering the segments
/// (with a live current-phase length) and their fractional widths, the cumulative progress point,
/// and the per-segment color-kind precedence.
final class FocusPhaseSegmentTests: XCTestCase {

    private let accuracy = 1e-9

    // MARK: Parsing

    func testParsesCompactPlanIntoSecondLengthSegments() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5")
        XCTAssertEqual(plan, [
            FocusPhaseSegment(isBreak: false, durationSeconds: 1500),
            FocusPhaseSegment(isBreak: true, durationSeconds: 300),
            FocusPhaseSegment(isBreak: false, durationSeconds: 1500),
            FocusPhaseSegment(isBreak: true, durationSeconds: 300),
        ])
    }

    func testParsingToleratesBlanksCaseAndMalformedTokens() {
        XCTAssertTrue(parseFocusPhasePlan(nil).isEmpty)
        XCTAssertTrue(parseFocusPhasePlan("").isEmpty)
        XCTAssertTrue(parseFocusPhasePlan("   ").isEmpty)
        // Lowercase initials accepted; junk / non-positive / single-char tokens dropped.
        XCTAssertEqual(
            parseFocusPhasePlan("f10, x9, B5, F0, F"),
            [
                FocusPhaseSegment(isBreak: false, durationSeconds: 600),
                FocusPhaseSegment(isBreak: true, durationSeconds: 300),
            ]
        )
    }

    func testParsingDropsNegativeAndNonNumericMinutes() {
        XCTAssertEqual(
            parseFocusPhasePlan("F-5,Babc,B10"),
            [FocusPhaseSegment(isBreak: true, durationSeconds: 600)]
        )
    }

    func testFocusPhaseSegmentsFromPhasesIsInverseOfParse() {
        let phases = [
            FocusPhase(kind: .work, durationMinutes: 25),
            FocusPhase(kind: .break, durationMinutes: 5),
            FocusPhase(kind: .work, durationMinutes: 30),
        ]
        XCTAssertEqual(focusPhaseSegments(from: phases), parseFocusPhasePlan("F25,B5,F30"))
    }

    func testFocusPhaseSegmentsFromPhasesDropsZeroDurations() {
        let phases = [
            FocusPhase(kind: .work, durationMinutes: 0),
            FocusPhase(kind: .break, durationMinutes: 5),
        ]
        XCTAssertEqual(focusPhaseSegments(from: phases),
                       [FocusPhaseSegment(isBreak: true, durationSeconds: 300)])
    }

    // MARK: Flat sessions yield no segments

    func testBarSegmentsEmptyForFlatSession() {
        XCTAssertTrue(focusBarSegments(plan: [], currentPhaseIndex: 0, currentPhaseTotalSeconds: 1500).isEmpty)
        XCTAssertTrue(
            focusBarSegments(
                plan: [FocusPhaseSegment(isBreak: false, durationSeconds: 1500)],
                currentPhaseIndex: 0,
                currentPhaseTotalSeconds: 1500
            ).isEmpty
        )
    }

    // MARK: Live current-phase length

    func testCurrentSegmentUsesLiveLengthSoAdjustmentIsReflected() {
        let plan = parseFocusPhasePlan("F25,B5,F25")
        // The user extended the current (index 1, the break) phase to 8m while running.
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 1, currentPhaseTotalSeconds: 8 * 60)

        XCTAssertEqual(segments.count, 3)
        XCTAssertEqual(segments[0].lengthSeconds, 1500)
        XCTAssertEqual(segments[1].lengthSeconds, 8 * 60) // live length, not the stale 300 from the plan
        XCTAssertTrue(segments[1].isCurrent)
        XCTAssertTrue(segments[1].isBreak)
        XCTAssertEqual(segments[2].lengthSeconds, 1500)
    }

    func testCurrentPhaseIndexIsClampedIntoRange() {
        let plan = parseFocusPhasePlan("F25,B5")
        let high = focusBarSegments(plan: plan, currentPhaseIndex: 99, currentPhaseTotalSeconds: 60)
        XCTAssertTrue(high.last?.isCurrent ?? false)
        XCTAssertFalse(high.first?.isCurrent ?? true)

        let low = focusBarSegments(plan: plan, currentPhaseIndex: -3, currentPhaseTotalSeconds: 60)
        XCTAssertTrue(low.first?.isCurrent ?? false)
    }

    func testCurrentPhaseLengthClampedToAtLeastOne() {
        let plan = parseFocusPhasePlan("F25,B5")
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 0, currentPhaseTotalSeconds: 0)
        XCTAssertEqual(segments[0].lengthSeconds, 1)
    }

    // MARK: Fractional widths

    func testFractionalWidthsAreProportionalAndSumToOne() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5") // 1500,300,1500,300 -> total 3600
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 0, currentPhaseTotalSeconds: 1500)
        XCTAssertEqual(segments[0].fractionalWidth, 1500.0 / 3600.0, accuracy: accuracy)
        XCTAssertEqual(segments[1].fractionalWidth, 300.0 / 3600.0, accuracy: accuracy)
        XCTAssertEqual(segments.reduce(0) { $0 + $1.fractionalWidth }, 1.0, accuracy: 1e-9)
    }

    func testFractionalWidthsUseLiveCurrentLength() {
        let plan = parseFocusPhasePlan("F25,B5,F25") // 1500,300,1500
        // Break extended to 8m=480 -> total 1500+480+1500 = 3480.
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 1, currentPhaseTotalSeconds: 480)
        XCTAssertEqual(segments[1].fractionalWidth, 480.0 / 3480.0, accuracy: accuracy)
        XCTAssertEqual(segments.reduce(0) { $0 + $1.fractionalWidth }, 1.0, accuracy: 1e-9)
    }

    // MARK: Progress point

    func testProgressPointSumsCompletedPhasesPlusElapsedInCurrent() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5")
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 2, currentPhaseTotalSeconds: 1500)
        // Phases 0 (1500) + 1 (300) done = 1800; 10:00 elapsed of the 25:00 third phase = +600.
        XCTAssertEqual(focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds: 15 * 60), 1800 + 600)
    }

    func testOverrunOfNonFinalPhaseFillsOnlyToThatPhaseEnd() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5")
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 2, currentPhaseTotalSeconds: 1500)
        XCTAssertEqual(focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds: -100), 1800 + 1500)
    }

    func testOverrunInFinalPhaseClampsToFullBar() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5")
        let lastSegments = focusBarSegments(plan: plan, currentPhaseIndex: 3, currentPhaseTotalSeconds: 300)
        XCTAssertEqual(
            focusSegmentedProgressPoint(lastSegments, currentPhaseTimeLeftSeconds: -100),
            lastSegments.reduce(0) { $0 + $1.lengthSeconds }
        )
    }

    func testProgressFractionMatchesPointOverTotal() {
        let plan = parseFocusPhasePlan("F25,B5,F25,B5")
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 2, currentPhaseTotalSeconds: 1500)
        let total = segments.reduce(0) { $0 + $1.lengthSeconds }
        let fraction = focusSegmentedProgressFraction(segments: segments, currentPhaseTimeLeftSeconds: 15 * 60)
        XCTAssertEqual(fraction, Double(1800 + 600) / Double(total), accuracy: accuracy)
    }

    func testProgressFractionIsZeroForEmptySegments() {
        XCTAssertEqual(focusSegmentedProgressFraction(segments: [], currentPhaseTimeLeftSeconds: 0), 0, accuracy: accuracy)
    }

    // MARK: Bar state

    func testBarStatePauseTakesPrecedenceOverEndingSoon() {
        XCTAssertEqual(focusBarState(isPaused: true, timeLeftSeconds: 30), .paused)
        XCTAssertEqual(focusBarState(isPaused: false, timeLeftSeconds: 30), .endingSoon)
        XCTAssertEqual(focusBarState(isPaused: false, timeLeftSeconds: focusBarEndingSoonThresholdSeconds), .endingSoon)
        XCTAssertEqual(focusBarState(isPaused: false, timeLeftSeconds: focusBarEndingSoonThresholdSeconds + 1), .running)
        // Zero / negative (over-run) is not "ending soon" — it has nothing left to emphasize.
        XCTAssertEqual(focusBarState(isPaused: false, timeLeftSeconds: 0), .running)
        XCTAssertEqual(focusBarState(isPaused: false, timeLeftSeconds: -10), .running)
    }

    // MARK: Color-kind precedence

    func testColorKindBreakAlwaysReadsAsBreakEvenWhenCurrent() {
        XCTAssertEqual(
            focusSegmentColorKind(isBreak: true, isCurrent: true, state: .running),
            .break
        )
        XCTAssertEqual(
            focusSegmentColorKind(isBreak: true, isCurrent: false, state: .paused),
            .break
        )
    }

    func testColorKindCurrentFocusCarriesLiveState() {
        XCTAssertEqual(
            focusSegmentColorKind(isBreak: false, isCurrent: true, state: .paused),
            .workCurrent(.paused)
        )
        XCTAssertEqual(
            focusSegmentColorKind(isBreak: false, isCurrent: true, state: .endingSoon),
            .workCurrent(.endingSoon)
        )
    }

    func testColorKindOtherFocusPhasesStayOnSteadyAccent() {
        // A non-current focus phase ignores the live state (Android: steady RUNNING accent).
        XCTAssertEqual(
            focusSegmentColorKind(isBreak: false, isCurrent: false, state: .endingSoon),
            .workSteady
        )
    }

    func testSegmentsCarryResolvedColorKinds() {
        let plan = parseFocusPhasePlan("F25,B5,F25")
        let segments = focusBarSegments(
            plan: plan,
            currentPhaseIndex: 0,
            currentPhaseTotalSeconds: 1500,
            state: .endingSoon
        )
        XCTAssertEqual(segments[0].colorKind, .workCurrent(.endingSoon)) // current focus
        XCTAssertEqual(segments[1].colorKind, .break)                    // break
        XCTAssertEqual(segments[2].colorKind, .workSteady)              // other focus
    }

    // MARK: Codable round-trip (Live Activity ContentState carries these across the widget boundary)

    func testSegmentCodableRoundTrip() throws {
        let plan = parseFocusPhasePlan("F25,B5,F25")
        let segments = focusBarSegments(plan: plan, currentPhaseIndex: 1, currentPhaseTotalSeconds: 480, state: .paused)
        let data = try JSONEncoder().encode(segments)
        let decoded = try JSONDecoder().decode([FocusBarSegment].self, from: data)
        XCTAssertEqual(decoded, segments)
    }
}
