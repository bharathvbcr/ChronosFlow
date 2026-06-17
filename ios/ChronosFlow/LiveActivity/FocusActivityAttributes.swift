import ActivityKit
import Foundation

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

        enum Phase: String, Codable, Hashable {
            case work, shortBreak, longBreak
            var title: String {
                switch self {
                case .work: "Focus"
                case .shortBreak: "Short break"
                case .longBreak: "Long break"
                }
            }
        }
    }

    /// Static info for the session.
    var blockTitle: String
}
