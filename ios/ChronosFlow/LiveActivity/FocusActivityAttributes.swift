import ActivityKit
import Foundation
import ChronosCore

/// Live Activity payload for an active focus session. This is the iOS-native equivalent of the
/// Android "Live Update" / foreground focus notification noted in the focus-splits memory —
/// it surfaces the running timer on the Lock Screen and in the Dynamic Island.
///
/// The session is BLOCK-BOUNDED: a finite phase sequence is planned from the selected block, and we
/// HOLD at each phase boundary (`awaitingAdvance`) instead of auto-rolling — the iOS analogue of the
/// Android `awaitingPhaseAdvance` + per-phase EXTRA_TERMINAL mirroring.
struct FocusActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// The phase being shown (work or break), mirroring the Android per-phase mirroring.
        var phase: Phase
        /// When the current phase ends; the widget renders a live countdown from this.
        var phaseEndsAt: Date
        var isPaused: Bool
        /// True while holding at a phase boundary, waiting for the user to tap to continue.
        var awaitingAdvance: Bool
        /// 1-based index of the current phase within the planned sequence.
        var phaseNumber: Int
        /// Total number of phases in the planned (block-bounded) sequence.
        var totalPhases: Int
        /// The current block's title, shown on the lock screen alongside the phase.
        var blockTitle: String
        /// When the current block started; used for the time-window display (e.g. "10:00 – 11:00").
        var blockStartsAt: Date?
        /// When the current block ends; used for the time-window display.
        var blockEndsAt: Date?
        /// Title of the next scheduled block, shown as up-next context below the timer.
        var nextBlockTitle: String?
        /// Overall progress through the current block (0.0–1.0) for the segmented progress bar.
        var completionPercent: Double
        /// Render-ready segments for the multi-phase (Pomodoro) progress bar — one per work/break
        /// phase, sized by fractional width, with the current phase's color carrying the live bar
        /// state. Empty for a flat (single-phase) session, which renders a plain bar. Built from
        /// `ChronosCore.focusBarSegments`. Mirrors the Android segmented foreground-notification bar.
        var phaseSegments: [PhaseSegmentInfo]
        /// 0-based index of the phase currently counting down within `phaseSegments`.
        var currentPhaseIndex: Int

        /// A Hashable mirror of `ChronosCore.FocusBarSegment` (ActivityKit `ContentState` must be
        /// `Hashable`, which `FocusBarSegment` is not). Carries the per-segment width / kind / live
        /// state the lock-screen bar renders. Mirrors the Android `FocusBarSegment`.
        struct PhaseSegmentInfo: Codable, Hashable {
            /// Fraction of the whole bar this segment occupies (0...1).
            var fractionalWidth: Double
            var isBreak: Bool
            var isCurrent: Bool
            /// The current focus segment's live bar state (running / endingSoon / paused). nil for
            /// breaks and non-current focus segments.
            var currentState: Phase.BarState?

            init(from segment: FocusBarSegment) {
                fractionalWidth = segment.fractionalWidth
                isBreak = segment.isBreak
                isCurrent = segment.isCurrent
                switch segment.colorKind {
                case .workCurrent(let state):
                    currentState = Phase.BarState(state)
                case .break, .workSteady:
                    currentState = nil
                }
            }
        }

        enum Phase: String, Codable, Hashable {
            case work, shortBreak, longBreak
            var title: String {
                switch self {
                case .work: "Focus"
                case .shortBreak: "Short break"
                case .longBreak: "Long break"
                }
            }

            /// Hashable mirror of `ChronosCore.FocusBarState` for the current focus segment's color.
            enum BarState: String, Codable, Hashable {
                case running, endingSoon, paused

                init(_ state: FocusBarState) {
                    switch state {
                    case .running: self = .running
                    case .endingSoon: self = .endingSoon
                    case .paused: self = .paused
                    }
                }
            }
        }
    }

    /// Static info for the session.
    var blockTitle: String
    /// Total planned duration of this block's phase sequence in minutes.
    /// Used by the lock-screen MetricStyle big-countdown (mirrors Android's MetricStyle gate).
    var totalPhaseDurationMinutes: Int
}
