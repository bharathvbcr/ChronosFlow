import XCTest
@testable import ChronosCore

// Mirrors the quality-tiering assertions of Android's SleepSessionMapperTest so the iOS port
// scores nights identically. The HealthKit sample → minutes mapping is verified on device; this
// covers the pure tiering math (deep+REM share → 5 tiers, fragmentation dock).
final class SleepStagingTests: XCTestCase {

    func testQualityDefaultsWhenNoStageBreakdown() {
        // An 8h "asleep" block with no light/deep/REM detail → neutral default.
        let stages = SleepStageMinutes(unspecifiedAsleepMinutes: 8 * 60)
        XCTAssertEqual(deriveSleepQuality(stages, interruptions: 0), 3)
    }

    func testQualityRisesWithHighDeepAndRemShare() {
        // 8h asleep, ~4h restorative (deep + REM) → ~50% share → top score.
        let stages = SleepStageMinutes(
            lightMinutes: 4 * 60,   // 2h front + 2h back
            deepMinutes: 150,       // 2h30
            remMinutes: 90          // 1h30
        )
        XCTAssertEqual(deriveSleepQuality(stages, interruptions: 0), 5)
    }

    func testQualityIsLowWhenLittleDeepOrRem() {
        // 8h asleep, only 30min restorative → ~6% share → bottom score.
        let stages = SleepStageMinutes(lightMinutes: 7 * 60 + 30, deepMinutes: 30)
        XCTAssertEqual(deriveSleepQuality(stages, interruptions: 0), 1)
    }

    func testMidTiersTrackRestorativeShare() {
        // 40% share → tier 4.
        XCTAssertEqual(
            deriveSleepQuality(SleepStageMinutes(lightMinutes: 360, deepMinutes: 240), interruptions: 0), 4)
        // 25% share → tier 3.
        XCTAssertEqual(
            deriveSleepQuality(SleepStageMinutes(lightMinutes: 450, deepMinutes: 150), interruptions: 0), 3)
        // 15% share → tier 2.
        XCTAssertEqual(
            deriveSleepQuality(SleepStageMinutes(lightMinutes: 510, deepMinutes: 90), interruptions: 0), 2)
    }

    func testFragmentationDocksALevelButNeverBelowOne() {
        // ~50% share (tier 5) docked to 4 by heavy fragmentation.
        let restful = SleepStageMinutes(lightMinutes: 4 * 60, deepMinutes: 150, remMinutes: 90)
        XCTAssertEqual(deriveSleepQuality(restful, interruptions: fragmentedInterruptions), 4)
        XCTAssertEqual(deriveSleepQuality(restful, interruptions: 3), 5) // under threshold → no dock

        // A bottom-tier night stays clamped at 1 even when docked.
        let poor = SleepStageMinutes(lightMinutes: 7 * 60 + 30, deepMinutes: 30)
        XCTAssertEqual(deriveSleepQuality(poor, interruptions: 8), 1)
    }

    func testZeroAsleepFallsBackToDefault() {
        XCTAssertEqual(deriveSleepQuality(SleepStageMinutes(), interruptions: 0), 3)
    }
}
