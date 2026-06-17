import Foundation

// Sleep-stage quality tiering — the pure, HealthKit-free core of the iOS sleep importer. It is the
// Foundation-only port of Android's `SleepSessionMapper.deriveQuality`: a 1..5 score derived from the
// restorative (deep + REM) share of asleep time, docked a level when the night was heavily fragmented.
//
// This deliberately has NO `import HealthKit` so it compiles and unit-tests on any platform (incl.
// Windows CI). The HealthKit-specific sample → minutes mapping lives in the app target
// (`HealthKitSleepImporter`), which feeds the aggregated minutes into `deriveSleepQuality`.

/// Stage durations (in minutes) summed over a single night, as gathered from a sleep tracker.
public struct SleepStageMinutes: Sendable, Equatable {
    public let lightMinutes: Int
    public let deepMinutes: Int
    public let remMinutes: Int
    /// Time scored as "asleep" but without a finer stage breakdown (e.g. `.asleepUnspecified`).
    public let unspecifiedAsleepMinutes: Int

    public init(
        lightMinutes: Int = 0,
        deepMinutes: Int = 0,
        remMinutes: Int = 0,
        unspecifiedAsleepMinutes: Int = 0
    ) {
        self.lightMinutes = lightMinutes
        self.deepMinutes = deepMinutes
        self.remMinutes = remMinutes
        self.unspecifiedAsleepMinutes = unspecifiedAsleepMinutes
    }

    /// Total time spent asleep across every stage — the denominator for the restorative share.
    public var totalAsleepMinutes: Int {
        lightMinutes + deepMinutes + remMinutes + unspecifiedAsleepMinutes
    }

    /// True when the tracker gave a real light/deep/REM breakdown to reason about (vs. only an
    /// undifferentiated "asleep" block). Without it we have no signal and fall back to a neutral score.
    public var hasStageDetail: Bool {
        lightMinutes > 0 || deepMinutes > 0 || remMinutes > 0
    }
}

/// Quality used when there is no stage breakdown to reason about (1..5 scale). Mirrors Android's
/// `DEFAULT_QUALITY`.
public let defaultSleepQuality = 3

/// Interruption count at or above which the derived quality is docked a point for fragmentation.
/// Mirrors Android's `FRAGMENTED_INTERRUPTIONS`.
public let fragmentedInterruptions = 4

/// Estimates a 1..5 quality from the restorative (deep + REM) share of asleep time, docking a point
/// when the night was heavily fragmented. Trackers that don't break the night into stages give no
/// signal, so those nights fall back to `defaultSleepQuality` rather than scoring poorly.
///
/// This is an exact port of `SleepSessionMapper.deriveQuality` (same five tiers + same dock rule).
public func deriveSleepQuality(_ stages: SleepStageMinutes, interruptions: Int) -> Int {
    guard stages.hasStageDetail else { return defaultSleepQuality }

    let asleep = stages.totalAsleepMinutes
    guard asleep > 0 else { return defaultSleepQuality }

    let restorative = stages.deepMinutes + stages.remMinutes
    let restorativeShare = Double(restorative) / Double(asleep)
    let base: Int
    switch restorativeShare {
    case 0.45...: base = 5
    case 0.35...: base = 4
    case 0.22...: base = 3
    case 0.12...: base = 2
    default: base = 1
    }
    let docked = interruptions >= fragmentedInterruptions ? base - 1 : base
    return min(5, max(1, docked))
}
