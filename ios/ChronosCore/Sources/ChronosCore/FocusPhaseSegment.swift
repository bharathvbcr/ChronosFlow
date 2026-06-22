import Foundation

// Render-ready focus-bar segments — the pure logic behind the iOS Focus Live Activity's segmented
// (Pomodoro) progress bar, ported from the Android live-update renderer (core/notifications
// `LiveUpdateGateway.kt`: `parseFocusPhasePlan` / `focusBarSegments` / `focusSegmentedProgressPoint`
// / `focusSegmentColorRes` and the `FocusSplitSegmentTest.kt` guards).
//
// A split (multi-phase) focus session is shown as a thin bar where each work/break phase is one
// segment, sized in proportion to its duration. The CURRENT phase's length is taken live (so a
// +/-5m adjustment while running is reflected even though the plan string is only re-sent on a
// phase change). Colors are expressed as a portable `FocusSegmentColorKind` rather than Android
// R.color resources, since ChronosCore is Foundation-only; the iOS widget maps kinds to actual
// SwiftUI colors. A flat (single-phase) session yields no segments — the caller renders a plain bar.

// MARK: - Cross-process phase plan

/// One work/break phase of a split (Pomodoro) focus session, as the renderer receives it across the
/// process boundary. Mirrors Android `FocusPhaseSegment(isBreak, durationSeconds)`.
public struct FocusPhaseSegment: Sendable, Equatable, Codable {
    public let isBreak: Bool
    public let durationSeconds: Int

    public init(isBreak: Bool, durationSeconds: Int) {
        self.isBreak = isBreak
        self.durationSeconds = durationSeconds
    }
}

/// Parses the compact phase plan the focus session carries across the process boundary
/// ("F25,B5,F25,B5" — kind initial + minutes), mirroring the in-app split encoding. Returns the
/// segments in seconds; blanks, malformed, and non-positive tokens are dropped. Mirrors Android
/// `parseFocusPhasePlan`.
public func parseFocusPhasePlan(_ encoded: String?) -> [FocusPhaseSegment] {
    guard let encoded, !encoded.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
    return encoded.split(separator: ",", omittingEmptySubsequences: false).compactMap { rawToken -> FocusPhaseSegment? in
        let trimmed = rawToken.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { return nil }
        let isBreak: Bool
        switch trimmed.first {
        case "B", "b": isBreak = true
        case "F", "f": isBreak = false
        default: return nil
        }
        guard let minutes = Int(trimmed.dropFirst()), minutes > 0 else { return nil }
        return FocusPhaseSegment(isBreak: isBreak, durationSeconds: minutes * 60)
    }
}

/// Builds the cross-process phase plan from an in-app `FocusPhase` list (the inverse of
/// `parseFocusPhasePlan`). Drops non-positive durations to match the parser's tolerance.
public func focusPhaseSegments(from phases: [FocusPhase]) -> [FocusPhaseSegment] {
    phases.compactMap { phase in
        guard phase.durationMinutes > 0 else { return nil }
        return FocusPhaseSegment(isBreak: phase.kind == .break, durationSeconds: phase.durationMinutes * 60)
    }
}

// MARK: - Bar state & color

/// Visual state of the live focus progress bar, driving the current focus segment's color.
/// Mirrors Android `FocusBarState`.
public enum FocusBarState: String, Sendable, Equatable, Codable {
    case running
    case endingSoon
    case paused
}

/// A running timer enters its "ending soon" emphasis when this many seconds (or fewer) remain.
/// Mirrors Android `FOCUS_BAR_ENDING_SOON_THRESHOLD_SECONDS`.
public let focusBarEndingSoonThresholdSeconds = 60

/// Derives the bar state; pause takes precedence over the ending-soon emphasis. Mirrors Android
/// `focusBarState`.
public func focusBarState(isPaused: Bool, timeLeftSeconds: Int) -> FocusBarState {
    if isPaused { return .paused }
    if (1...focusBarEndingSoonThresholdSeconds).contains(timeLeftSeconds) { return .endingSoon }
    return .running
}

/// The render color of one bar segment, portable across platforms (no R.color / UIColor here).
///
/// Mirrors Android `focusSegmentColorRes` precedence:
/// break  → always a break (regardless of being current);
/// work + current → carries the live bar `FocusBarState`;
/// work + other → the steady running accent.
public enum FocusSegmentColorKind: Sendable, Equatable, Codable {
    /// A break phase. Android tints these with the complementary teal (`focus_break_segment`).
    case `break`
    /// The current focus phase, carrying the live state (running / ending-soon / paused).
    case workCurrent(FocusBarState)
    /// A non-current focus phase; always the steady running accent.
    case workSteady
}

/// Per-segment color kind. Mirrors Android `focusSegmentColorRes`'s break → current-state → steady
/// precedence.
public func focusSegmentColorKind(isBreak: Bool, isCurrent: Bool, state: FocusBarState) -> FocusSegmentColorKind {
    if isBreak { return .break }
    if isCurrent { return .workCurrent(state) }
    return .workSteady
}

// MARK: - Render-ready segments

/// A bar segment ready to render: its (live) length, kind, whether it is the phase counting down,
/// its fractional width along the whole bar (0...1), and its resolved color kind. Mirrors Android
/// `FocusBarSegment` plus the fractional width the iOS bar layout needs.
public struct FocusBarSegment: Sendable, Equatable, Codable {
    public let lengthSeconds: Int
    public let isBreak: Bool
    public let isCurrent: Bool
    /// Fraction of the whole bar this segment occupies, in 0...1. The widths across a segment list
    /// sum to 1 (subject to floating-point rounding). Zero when the bar is degenerate.
    public let fractionalWidth: Double
    public let colorKind: FocusSegmentColorKind

    public init(
        lengthSeconds: Int,
        isBreak: Bool,
        isCurrent: Bool,
        fractionalWidth: Double,
        colorKind: FocusSegmentColorKind
    ) {
        self.lengthSeconds = lengthSeconds
        self.isBreak = isBreak
        self.isCurrent = isCurrent
        self.fractionalWidth = fractionalWidth
        self.colorKind = colorKind
    }
}

/// Builds the render-ready segment list from a phase plan.
///
/// Mirrors Android `focusBarSegments` (a flat session — `plan.size <= 1` — yields no segments; the
/// current phase's length is taken from the live `currentPhaseTotalSeconds` so a +/-5m adjustment is
/// reflected; every length is clamped to at least 1) and additionally resolves each segment's
/// fractional width and color kind so the iOS bar can render directly.
///
/// - Parameters:
///   - plan: The parsed phase plan. One or zero phases means a flat session → empty result.
///   - currentPhaseIndex: Index of the phase counting down (clamped into range).
///   - currentPhaseTotalSeconds: The current phase's live total length (clamped to at least 1).
///   - state: The live bar state for coloring the current focus segment. Defaults to `.running`.
/// - Returns: One `FocusBarSegment` per phase, in order, or an empty list for a flat session.
public func focusBarSegments(
    plan: [FocusPhaseSegment],
    currentPhaseIndex: Int,
    currentPhaseTotalSeconds: Int,
    state: FocusBarState = .running
) -> [FocusBarSegment] {
    guard plan.count > 1 else { return [] }
    let idx = min(max(currentPhaseIndex, 0), plan.count - 1)

    // Resolve each phase's live length first so the fractional widths divide by the same total the
    // progress point uses.
    let lengths: [Int] = plan.enumerated().map { i, segment in
        let raw = i == idx ? currentPhaseTotalSeconds : segment.durationSeconds
        return max(raw, 1)
    }
    let total = max(lengths.reduce(0, +), 1)

    return plan.enumerated().map { i, segment in
        let isCurrent = i == idx
        return FocusBarSegment(
            lengthSeconds: lengths[i],
            isBreak: segment.isBreak,
            isCurrent: isCurrent,
            fractionalWidth: Double(lengths[i]) / Double(total),
            colorKind: focusSegmentColorKind(isBreak: segment.isBreak, isCurrent: isCurrent, state: state)
        )
    }
}

/// Absolute progress point along the summed segment lengths: completed phases plus elapsed-in the
/// current phase. Overrunning a non-final phase fills only up to that phase's end (the next phase's
/// plan length isn't yet live); an overrun is clamped to the full bar. Mirrors Android
/// `focusSegmentedProgressPoint`.
///
/// - Parameters:
///   - segments: The render-ready segments (as from `focusBarSegments`).
///   - currentPhaseTimeLeftSeconds: Seconds left in the current phase (may be <= 0 on overrun).
/// - Returns: A point in 0...(summed lengths).
public func focusSegmentedProgressPoint(
    segments: [FocusBarSegment],
    currentPhaseTimeLeftSeconds: Int
) -> Int {
    let total = max(segments.reduce(0) { $0 + $1.lengthSeconds }, 1)
    let idx = segments.firstIndex(where: { $0.isCurrent }) ?? 0
    let before = segments.prefix(idx).reduce(0) { $0 + $1.lengthSeconds }
    let currentLength = idx < segments.count ? segments[idx].lengthSeconds : 1
    let elapsedInCurrent = min(max(currentLength - currentPhaseTimeLeftSeconds, 0), currentLength)
    return min(max(before + elapsedInCurrent, 0), total)
}

/// Fraction (0...1) of the whole bar that is filled, for callers that want a single completion ratio
/// rather than the absolute point. Convenience over `focusSegmentedProgressPoint`.
public func focusSegmentedProgressFraction(
    segments: [FocusBarSegment],
    currentPhaseTimeLeftSeconds: Int
) -> Double {
    let total = segments.reduce(0) { $0 + $1.lengthSeconds }
    guard total > 0 else { return 0 }
    let point = focusSegmentedProgressPoint(segments, currentPhaseTimeLeftSeconds: currentPhaseTimeLeftSeconds)
    return Double(point) / Double(total)
}

// Argument-label overload so both `focusSegmentedProgressPoint(x, currentPhaseTimeLeftSeconds:)` and
// the labeled form read naturally at call sites.
public func focusSegmentedProgressPoint(
    _ segments: [FocusBarSegment],
    currentPhaseTimeLeftSeconds: Int
) -> Int {
    focusSegmentedProgressPoint(segments: segments, currentPhaseTimeLeftSeconds: currentPhaseTimeLeftSeconds)
}
