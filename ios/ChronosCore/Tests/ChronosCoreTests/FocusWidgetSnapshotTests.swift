import XCTest
@testable import ChronosCore

/// Pure-logic guard for the home-screen focus widget snapshot + App-Group bridge: round-trip,
/// idle/stale fallbacks, and the split-bar derivation a flat vs. split session yields.
final class FocusWidgetSnapshotTests: XCTestCase {

    private func makeDefaults() -> UserDefaults {
        let suite = "FocusWidgetSnapshotTests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }

    private func activeSnapshot(plan: String, updatedAt: Date = .init()) -> FocusWidgetSnapshot {
        FocusWidgetSnapshot(
            isActive: true,
            blockTitle: "Deep work",
            phaseIsBreak: false,
            isPaused: false,
            awaitingAdvance: false,
            phaseNumber: 1,
            totalPhases: 4,
            phaseEndsAt: updatedAt.addingTimeInterval(1500),
            currentPhaseTotalSeconds: 1500,
            currentPhaseIndex: 0,
            phasePlanEncoded: plan,
            updatedAt: updatedAt
        )
    }

    // MARK: Round-trip

    func testPublishThenReadReturnsActiveSnapshot() {
        let defaults = makeDefaults()
        let now = Date()
        let snapshot = activeSnapshot(plan: "F25,B5,F25,B5", updatedAt: now)
        FocusWidgetBridge.publish(snapshot, to: defaults)
        let read = FocusWidgetBridge.read(from: defaults, now: now)
        XCTAssertEqual(read, snapshot)
    }

    func testReadFromEmptyDefaultsIsIdle() {
        XCTAssertEqual(FocusWidgetBridge.read(from: makeDefaults()), .idle)
        XCTAssertFalse(FocusWidgetSnapshot.idle.isActive)
    }

    func testPublishNilClearsToIdle() {
        let defaults = makeDefaults()
        FocusWidgetBridge.publish(activeSnapshot(plan: ""), to: defaults)
        FocusWidgetBridge.publish(nil, to: defaults)
        XCTAssertEqual(FocusWidgetBridge.read(from: defaults), .idle)
    }

    // MARK: Fallbacks

    func testInactiveStoredSnapshotReadsAsIdle() {
        let defaults = makeDefaults()
        var inactive = activeSnapshot(plan: "F25,B5")
        inactive.isActive = false
        FocusWidgetBridge.publish(inactive, to: defaults)
        XCTAssertEqual(FocusWidgetBridge.read(from: defaults), .idle)
    }

    func testStaleSnapshotReadsAsIdle() {
        let defaults = makeDefaults()
        let old = Date(timeIntervalSince1970: 1_000_000)
        FocusWidgetBridge.publish(activeSnapshot(plan: "F25,B5", updatedAt: old), to: defaults)
        // Read far in the future — beyond the stale window.
        let later = old.addingTimeInterval(FocusWidgetBridge.staleAfterSeconds + 60)
        XCTAssertEqual(FocusWidgetBridge.read(from: defaults, now: later), .idle)
    }

    func testFreshSnapshotWithinStaleWindowSurvives() {
        let defaults = makeDefaults()
        let t = Date(timeIntervalSince1970: 2_000_000)
        let snapshot = activeSnapshot(plan: "F25,B5", updatedAt: t)
        FocusWidgetBridge.publish(snapshot, to: defaults)
        let stillFresh = t.addingTimeInterval(FocusWidgetBridge.staleAfterSeconds - 1)
        XCTAssertTrue(FocusWidgetBridge.read(from: defaults, now: stillFresh).isActive)
    }

    // MARK: Bar derivation

    func testFlatSessionYieldsNoBarSegments() {
        let flat = activeSnapshot(plan: "")
        XCTAssertFalse(flat.isSplit)
        XCTAssertTrue(flat.barSegments().isEmpty)
    }

    func testSplitSessionYieldsSegmentPerPhase() {
        let now = Date()
        let split = activeSnapshot(plan: "F25,B5,F25,B5", updatedAt: now)
        XCTAssertTrue(split.isSplit)
        let segments = split.barSegments(asOf: now)
        XCTAssertEqual(segments.count, 4)
        XCTAssertTrue(segments[0].isCurrent)
        XCTAssertTrue(segments[1].isBreak)
        // Widths sum to ~1.
        XCTAssertEqual(segments.reduce(0) { $0 + $1.fractionalWidth }, 1, accuracy: 1e-9)
    }

    func testPausedSessionMarksCurrentSegmentPaused() {
        let now = Date()
        var split = activeSnapshot(plan: "F25,B5", updatedAt: now)
        split.isPaused = true
        let segments = split.barSegments(asOf: now)
        XCTAssertEqual(segments.first?.colorKind, .workCurrent(.paused))
    }
}
