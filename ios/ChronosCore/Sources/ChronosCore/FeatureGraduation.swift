import Foundation

// Feature-graduation marker system — the pure, Foundation-only port of Android's
// `FeatureGraduation` pattern (core/ui/.../settings/ChronosUiSettings.kt:97-130, 188-206, 365-380).
//
// Some features shipped *disabled* and later graduated to *on-by-default*. Installs that persisted
// a stale `false` for such a flag before graduation must NOT be stranded off — they should be
// promoted on-by-default exactly once. A per-wave "promotion marker" tracks whether that one-time
// promotion has already happened:
//
//   • While the marker is UNSET, the feature reads as enabled (`true`) regardless of any stored
//     value (so legacy parked-`false` installs light the feature up once).
//   • The first time the user makes a *deliberate* choice (any write to a graduated flag), the
//     marker is set and the stored value is respected from then on — including an explicit `false`.
//
// This file is pure logic only: it performs NO I/O. Callers own persistence; `graduationForFlag`
// tells them both the effective value to use *and* whether the promotion marker must now be set.

/// A wave of feature flags that graduate from off-to-on together, guarded by a single promotion
/// marker. Mirrors Android's private `FeatureGraduation` data class (one marker → many feature keys).
public struct FeatureGraduation: Sendable, Equatable {
    /// Persisted key whose `true` value records that this wave's one-time promotion already ran.
    public let markerKey: String
    /// The feature-flag keys promoted together under `markerKey`.
    public let featureKeys: Set<String>

    public init(markerKey: String, featureKeys: Set<String>) {
        self.markerKey = markerKey
        self.featureKeys = featureKeys
    }
}

/// The outcome of resolving a graduated flag: the value the caller should treat as effective, plus
/// whether the caller must now persist the promotion marker as `true`.
///
/// `newMarker` is `true` only when this resolution actually *promotes* a not-yet-promoted wave
/// (i.e. the marker was unset). Callers that merely read a flag can ignore `newMarker`; callers that
/// want to make the one-time promotion durable should write the marker when `newMarker` is `true`.
public struct FeatureGraduationResult: Sendable, Equatable {
    public let effective: Bool
    public let newMarker: Bool

    public init(effective: Bool, newMarker: Bool) {
        self.effective = effective
        self.newMarker = newMarker
    }
}

/// Canonical persisted keys for the graduated feature flags and their promotion markers. Raw string
/// values are byte-identical to Android's `ChronosUiSettingsKeys` so a future shared store would
/// interoperate, and so parity tests can be written against the same key names.
public enum FeatureGraduationKeys {
    // Promotion markers (one per wave).
    public static let habitsMedicationPromoted = "feature.habitsMedicationDefaultsPromoted"
    public static let companionPromoted = "feature.companionDefaultsPromoted"

    // Wave 1 — habits + medication.
    public static let habitsEnabled = "feature.habitsEnabled"
    public static let medicationEnabled = "feature.medicationEnabled"

    // Wave 2 — companion features (review/insights, AI advisor, goals, journal, sleep).
    public static let reviewEnabled = "feature.reviewEnabled"
    public static let aiAdvisorEnabled = "feature.aiAdvisorEnabled"
    public static let goalsEnabled = "feature.goalsEnabled"
    public static let journalEnabled = "feature.journalEnabled"
    public static let sleepEnabled = "feature.sleepEnabled"
}

/// The pure feature-graduation engine. Stateless; all state is passed in by the caller.
public enum FeatureGraduations {

    /// The catalog of graduation waves, in declaration order. Mirrors Android's `FeatureGraduations`
    /// list exactly: wave 1 = habits + medication, wave 2 = the companion features.
    public static let all: [FeatureGraduation] = [
        FeatureGraduation(
            markerKey: FeatureGraduationKeys.habitsMedicationPromoted,
            featureKeys: [
                FeatureGraduationKeys.habitsEnabled,
                FeatureGraduationKeys.medicationEnabled
            ]
        ),
        FeatureGraduation(
            markerKey: FeatureGraduationKeys.companionPromoted,
            featureKeys: [
                FeatureGraduationKeys.reviewEnabled,
                FeatureGraduationKeys.aiAdvisorEnabled,
                FeatureGraduationKeys.goalsEnabled,
                FeatureGraduationKeys.journalEnabled,
                FeatureGraduationKeys.sleepEnabled
            ]
        )
    ]

    /// The graduation wave that owns `key`, or `nil` if the key is not a graduated flag.
    /// Mirrors Android's `graduationForKey`.
    public static func graduation(forKey key: String) -> FeatureGraduation? {
        all.first { $0.featureKeys.contains(key) }
    }

    /// The promotion-marker key for `key`'s wave, or `nil` if `key` is not graduated.
    public static func markerKey(forKey key: String) -> String? {
        graduation(forKey: key)?.markerKey
    }

    /// True if `key` participates in any graduation wave.
    public static func isGraduated(_ key: String) -> Bool {
        graduation(forKey: key) != nil
    }

    /// Resolve a *graduated* flag's effective value from its stored value and its wave's marker.
    ///
    /// This is the pure core requested by the spec, matching Android's `readBooleanSetting`
    /// (force-on while the marker is unset) plus the write-side promotion in
    /// `writeChronosUiBooleanSetting` (a deliberate write sets the marker):
    ///
    ///   • `markerSet == false` → the wave has not been promoted yet. The feature reads as `true`
    ///     regardless of `stored`, and `newMarker == true` to tell the caller the one-time
    ///     promotion is now due (persist the marker so the next read respects the stored value).
    ///   • `markerSet == true` → the user has made a deliberate choice. The stored value is
    ///     respected (`stored ?? defaultOn`), and `newMarker == false` (no marker change needed).
    ///
    /// - Parameters:
    ///   - stored: The persisted flag value, or `nil` if the flag was never written.
    ///   - markerSet: Whether the wave's promotion marker is already set to `true`.
    ///   - defaultOn: The default applied once the marker is set and no value is stored.
    /// - Returns: The effective value and whether the marker must now be set.
    public static func graduationForFlag(
        stored: Bool?,
        markerSet: Bool,
        defaultOn: Bool
    ) -> (effective: Bool, newMarker: Bool) {
        if !markerSet {
            // Wave not yet promoted: force the feature on, and signal the promotion is due.
            return (effective: true, newMarker: true)
        }
        // Promoted: the deliberate stored choice (or the default) wins.
        return (effective: stored ?? defaultOn, newMarker: false)
    }

    /// Convenience overload returning a `FeatureGraduationResult` value type for callers that prefer
    /// it over a tuple. Identical semantics to ``graduationForFlag(stored:markerSet:defaultOn:)``.
    public static func resolve(
        stored: Bool?,
        markerSet: Bool,
        defaultOn: Bool
    ) -> FeatureGraduationResult {
        let r = graduationForFlag(stored: stored, markerSet: markerSet, defaultOn: defaultOn)
        return FeatureGraduationResult(effective: r.effective, newMarker: r.newMarker)
    }

    /// Resolve a feature flag *by key*, applying graduation only when the key is graduated.
    ///
    /// For a non-graduated key this is a plain `stored ?? defaultOn` with `newMarker == false`
    /// (there is no marker to set). For a graduated key it delegates to
    /// ``graduationForFlag(stored:markerSet:defaultOn:)`` using the supplied marker state.
    ///
    /// - Parameters:
    ///   - key: The flag's persisted key.
    ///   - stored: The persisted flag value, or `nil` if never written.
    ///   - markerSet: Whether the key's wave marker is set. Ignored for non-graduated keys.
    ///   - defaultOn: The default applied when the stored value is respected but absent.
    public static func resolveFlag(
        key: String,
        stored: Bool?,
        markerSet: Bool,
        defaultOn: Bool
    ) -> FeatureGraduationResult {
        guard isGraduated(key) else {
            return FeatureGraduationResult(effective: stored ?? defaultOn, newMarker: false)
        }
        return resolve(stored: stored, markerSet: markerSet, defaultOn: defaultOn)
    }
}
