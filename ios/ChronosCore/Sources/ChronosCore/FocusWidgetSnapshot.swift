import Foundation

// Cross-process snapshot of the live focus session for the HOME-SCREEN focus widget. The widget runs
// in the WidgetKit extension process and cannot read the in-app, in-memory `FocusTimerModel`, so the
// app publishes a tiny Codable snapshot to App-Group `UserDefaults` (the same channel the digest line
// and `FocusCommandBridge` use) and the widget reads it. This is the read/write logic; the SwiftUI
// rendering lives in the widget target.
//
// Mirrors the Android home-screen focus glance, whose widget reads the FocusService's published
// "live update" state rather than touching the in-process timer. A FLAT (single-phase) session
// carries an empty `phasePlanEncoded`; a split (Pomodoro) session carries the same compact plan
// string the Live Activity uses ("F25,B5,F25,B5"), so the widget can render the segmented bar via
// `parseFocusPhasePlan` + `focusBarSegments`.

// MARK: - Snapshot

/// A point-in-time snapshot of the running focus session, written by the app and read by the widget.
/// All fields are value types so it survives the process boundary as JSON. When `isActive` is false
/// the widget shows its idle "Start focus" state.
public struct FocusWidgetSnapshot: Sendable, Equatable, Codable {
    /// Whether a session is currently running or paused (false → idle widget).
    public var isActive: Bool
    /// The block/session title shown on the widget.
    public var blockTitle: String
    /// The current phase's kind (work / break) for the icon + accent.
    public var phaseIsBreak: Bool
    /// Whether the session is paused (controls the Pause/Resume button glyph).
    public var isPaused: Bool
    /// Whether the session is holding at a phase boundary awaiting "tap to continue".
    public var awaitingAdvance: Bool
    /// 1-based index of the current phase.
    public var phaseNumber: Int
    /// Total phases in the planned (block-bounded) sequence.
    public var totalPhases: Int
    /// Wall-clock instant the current phase ends; the widget renders a live countdown from this.
    public var phaseEndsAt: Date
    /// The current phase's full length in seconds (live, so a +/-5m adjustment is reflected); used to
    /// size the current segment of the split bar.
    public var currentPhaseTotalSeconds: Int
    /// 0-based index of the phase counting down within the plan (for `focusBarSegments`).
    public var currentPhaseIndex: Int
    /// The compact phase plan string ("F25,B5,F25,B5"); empty for a flat session.
    public var phasePlanEncoded: String
    /// When the snapshot was written; lets the widget ignore a stale snapshot left by a crashed app.
    public var updatedAt: Date

    public init(
        isActive: Bool,
        blockTitle: String,
        phaseIsBreak: Bool,
        isPaused: Bool,
        awaitingAdvance: Bool,
        phaseNumber: Int,
        totalPhases: Int,
        phaseEndsAt: Date,
        currentPhaseTotalSeconds: Int,
        currentPhaseIndex: Int,
        phasePlanEncoded: String,
        updatedAt: Date = .init()
    ) {
        self.isActive = isActive
        self.blockTitle = blockTitle
        self.phaseIsBreak = phaseIsBreak
        self.isPaused = isPaused
        self.awaitingAdvance = awaitingAdvance
        self.phaseNumber = phaseNumber
        self.totalPhases = totalPhases
        self.phaseEndsAt = phaseEndsAt
        self.currentPhaseTotalSeconds = currentPhaseTotalSeconds
        self.currentPhaseIndex = currentPhaseIndex
        self.phasePlanEncoded = phasePlanEncoded
        self.updatedAt = updatedAt
    }

    /// The idle snapshot — no session running. The widget renders its "Start focus" affordance.
    public static let idle = FocusWidgetSnapshot(
        isActive: false,
        blockTitle: "",
        phaseIsBreak: false,
        isPaused: false,
        awaitingAdvance: false,
        phaseNumber: 0,
        totalPhases: 0,
        phaseEndsAt: .init(),
        currentPhaseTotalSeconds: 0,
        currentPhaseIndex: 0,
        phasePlanEncoded: "",
        updatedAt: .init(timeIntervalSince1970: 0)
    )

    /// Whether this session splits into multiple phases (drives the segmented bar vs a plain bar).
    public var isSplit: Bool { totalPhases > 1 && !phasePlanEncoded.isEmpty }

    /// The render-ready split-bar segments for this snapshot (empty for a flat session). Resolves the
    /// live bar state from `isPaused` and the seconds left, mirroring the Live Activity's bar.
    public func barSegments(asOf now: Date = .init()) -> [FocusBarSegment] {
        let plan = parseFocusPhasePlan(phasePlanEncoded)
        let timeLeft = max(Int(phaseEndsAt.timeIntervalSince(now).rounded()), 0)
        let state = focusBarState(isPaused: isPaused, timeLeftSeconds: timeLeft)
        return focusBarSegments(
            plan: plan,
            currentPhaseIndex: currentPhaseIndex,
            currentPhaseTotalSeconds: max(currentPhaseTotalSeconds, 1),
            state: state
        )
    }
}

// MARK: - App-Group bridge

/// Reads/writes the home-screen focus widget snapshot to App-Group `UserDefaults`. The app calls
/// `publish` whenever the live session changes (start / pause / advance / extend / stop); the widget
/// calls `read` from its `TimelineProvider`. A `nil` `defaults` falls back to `.standard` so unit
/// tests and previews work without an App Group.
public enum FocusWidgetBridge {
    /// The App-Group `UserDefaults` key the snapshot is stored under. Distinct from the
    /// `FocusCommandBridge` command queue so the two never clobber each other.
    public static let storageKey = "focus.widget.snapshot"

    /// Sessions older than this are treated as stale (the app died without clearing the snapshot) and
    /// the widget falls back to idle. Generous enough to span a normal phase, since the widget can be
    /// woken to refresh well after the last `publish`.
    public static let staleAfterSeconds: TimeInterval = 6 * 60 * 60

    /// Encode + store the snapshot. Pass `nil` to clear it (session ended) — equivalent to publishing
    /// `.idle`.
    public static func publish(_ snapshot: FocusWidgetSnapshot?, to defaults: UserDefaults) {
        let value = snapshot ?? .idle
        if let data = try? JSONEncoder().encode(value) {
            defaults.set(data, forKey: storageKey)
        }
    }

    /// Read the stored snapshot. Returns `.idle` when nothing is stored, when decoding fails, when the
    /// stored session is inactive, or when it is older than `staleAfterSeconds` relative to `now`.
    public static func read(from defaults: UserDefaults, now: Date = .init()) -> FocusWidgetSnapshot {
        guard let data = defaults.data(forKey: storageKey),
              let snapshot = try? JSONDecoder().decode(FocusWidgetSnapshot.self, from: data),
              snapshot.isActive,
              now.timeIntervalSince(snapshot.updatedAt) <= staleAfterSeconds
        else { return .idle }
        return snapshot
    }
}
