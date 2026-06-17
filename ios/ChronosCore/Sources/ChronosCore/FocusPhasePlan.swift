import Foundation

// Block-bounded focus phase planning — the pure logic behind the iOS FocusTimerModel, ported from
// the Android `FocusPhasePlanner` (feature/daydial). A SELECTED block's finite duration is split
// into a fixed alternating work/break phase sequence per a chosen preset. The session is bounded by
// the block: it does NOT loop forever. The in-app model holds at each phase boundary
// (awaitingPhaseAdvance) until the user advances; nothing here auto-advances.

/// The kind of a single focus phase.
public enum FocusPhaseKind: String, Sendable, Equatable, Codable {
    case work
    case `break`
}

/// One segment of a split (Pomodoro-style) focus session.
public struct FocusPhase: Sendable, Equatable, Codable {
    public let kind: FocusPhaseKind
    public let durationMinutes: Int

    public init(kind: FocusPhaseKind, durationMinutes: Int) {
        self.kind = kind
        self.durationMinutes = durationMinutes
    }
}

/// The four work/break split presets offered when configuring a focus session, mirroring the
/// Android preset chips. `noBreaks` is a single flat work phase for the whole block.
public enum FocusSplitPreset: String, Sendable, Equatable, Codable, CaseIterable {
    case noBreaks
    case p25_5
    case p50_10
    case p30_5

    /// Work-interval length for this preset, in minutes.
    public var workMinutes: Int {
        switch self {
        case .noBreaks: return 0   // unused — no breaks means one flat block
        case .p25_5: return 25
        case .p50_10: return 50
        case .p30_5: return 30
        }
    }

    /// Break length for this preset, in minutes. Zero for `noBreaks`.
    public var breakMinutes: Int {
        switch self {
        case .noBreaks: return 0
        case .p25_5: return 5
        case .p50_10: return 10
        case .p30_5: return 5
        }
    }

    /// Short label for the preset chip.
    public var label: String {
        switch self {
        case .noBreaks: return "No breaks"
        case .p25_5: return "25 · 5"
        case .p50_10: return "50 · 10"
        case .p30_5: return "30 · 5"
        }
    }
}

/// Splits a block's planned duration into alternating work/break phases for the given preset.
///
/// Mirrors Android `FocusPhasePlanner.plan`: divide the block into full `work + break` cycles, then
/// add any remainder (shorter than a full cycle) as a trailing work phase — never a trailing break.
/// A `noBreaks` preset, or a block too short to fit even one work interval plus a break, yields a
/// single flat work phase spanning the whole block (legacy "no splits" behavior). The summed phase
/// durations always equal `blockMinutes` (clamped to at least 1).
///
/// - Parameters:
///   - blockMinutes: The SELECTED block's finite duration in minutes. Clamped to at least 1.
///   - preset: The work/break split preset.
/// - Returns: An ordered, non-empty list of `FocusPhase`.
public func planFocusPhases(blockMinutes: Int, preset: FocusSplitPreset) -> [FocusPhase] {
    let total = max(blockMinutes, 1)
    let work = max(preset.workMinutes, 1)
    let brk = max(preset.breakMinutes, 0)

    // No breaks requested, or the block can't fit one work interval plus a break: a single flat
    // work phase covering the whole block.
    if preset == .noBreaks || brk == 0 || total < work + brk {
        return [FocusPhase(kind: .work, durationMinutes: total)]
    }

    let cycle = work + brk
    let fullCycles = total / cycle
    let remainder = total % cycle

    var phases: [FocusPhase] = []
    phases.reserveCapacity(fullCycles * 2 + 1)
    for _ in 0..<fullCycles {
        phases.append(FocusPhase(kind: .work, durationMinutes: work))
        phases.append(FocusPhase(kind: .break, durationMinutes: brk))
    }
    if remainder > 0 {
        // Tail too small for another cycle: spend it all on focus.
        phases.append(FocusPhase(kind: .work, durationMinutes: remainder))
    }
    return phases
}

/// Total work minutes across a plan (excludes breaks). The Android `focusMinutes` analogue.
public func focusMinutes(in phases: [FocusPhase]) -> Int {
    phases.filter { $0.kind == .work }.reduce(0) { $0 + $1.durationMinutes }
}

/// The boundary prompt shown when holding at a phase transition (awaitingPhaseAdvance), naming the
/// phase the user is about to start. Mirrors Android `focusPhaseBoundaryText`.
public func focusPhaseBoundaryText(nextPhase: FocusPhase) -> String {
    switch nextPhase.kind {
    case .break:
        return "Time for a \(nextPhase.durationMinutes)m break — tap to continue"
    case .work:
        return "Back to focus for \(nextPhase.durationMinutes)m — tap to continue"
    }
}
