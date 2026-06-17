import Foundation
import Observation
import ActivityKit
import SwiftData
import ChronosCore

/// Drives a BLOCK-BOUNDED focus session split into a fixed work/break phase sequence — the iOS port
/// of the Android in-app focus orchestrator (`FocusPhasePlanner` + `awaitingPhaseAdvance`).
///
/// The selected block's finite duration is planned into an ordered `[FocusPhase]` (see
/// `planFocusPhases`). The model walks that sequence and HOLDS at each phase boundary
/// (`awaitingPhaseAdvance`) instead of auto-rolling or looping forever — the user taps to advance.
/// The session COMPLETES once the last phase is advanced. The in-app model owns phase orchestration;
/// the Live Activity mirrors each phase (the iOS analogue of the EXTRA_TERMINAL per-phase mirroring).
@MainActor
@Observable
final class FocusTimerModel {
    /// Coarse phase the UI renders. `work` covers any focus phase; `shortBreak` covers break phases
    /// (kept for ring colour + Live-Activity title parity with the old model).
    enum Phase: Equatable { case idle, work, shortBreak, longBreak, completed }

    var phase: Phase = .idle
    var remaining: TimeInterval = 0
    var isPaused = false
    var completedWorkSessions = 0
    var blockTitle = "Focus session"
    var interruptions = 0

    /// The split preset chosen when configuring the session (default 25 · 5, the Android default).
    var preset: FocusSplitPreset = .p25_5

    // The planned, block-bounded phase sequence and where we are in it.
    private(set) var phasePlan: [FocusPhase] = []
    private(set) var currentPhaseIndex = 0
    /// True when the current phase hit 0 and we are HOLDING for the user to advance.
    private(set) var awaitingPhaseAdvance = false
    /// True once the FINAL phase has elapsed — the block ran its full course (vs. an early stop).
    private(set) var blockFullyElapsed = false

    /// Default per-phase length surfaced before a session starts / for the idle ring caption.
    var workMinutes: Int { preset == .noBreaks ? defaultBlockMinutes : max(preset.workMinutes, 1) }

    private var defaultBlockMinutes = 25

    private var ticker: Timer?
    private var phaseEndsAt: Date?
    private var activity: Activity<FocusActivityAttributes>?
    private var sessionStart: Date?
    private var blockID: String?
    private var plannedBlockMinutes = 25

    // MARK: Derived state for the UI

    /// Total phases in the current block-bounded plan.
    var totalPhases: Int { phasePlan.count }
    /// 1-based number of the current phase.
    var phaseNumber: Int { min(currentPhaseIndex + 1, max(totalPhases, 1)) }
    /// The phase we are about to start when awaiting advance (nil if none / session ending).
    var nextPhase: FocusPhase? { phasePlan.indices.contains(currentPhaseIndex + 1) ? phasePlan[currentPhaseIndex + 1] : nil }
    /// Whether the current plan actually splits into multiple phases.
    var isSplitSession: Bool { phasePlan.count > 1 }

    var progress: Double {
        guard let total = phaseTotal, total > 0 else { return awaitingPhaseAdvance ? 1 : 0 }
        return min(1, max(0, 1 - (remaining / total)))
    }

    /// Boundary prompt for the "tap to continue" button (e.g. "Time for a 5m break — tap to continue").
    var boundaryPrompt: String? {
        guard awaitingPhaseAdvance, let next = nextPhase else { return nil }
        return focusPhaseBoundaryText(nextPhase: next)
    }

    private var currentFocusPhase: FocusPhase? {
        phasePlan.indices.contains(currentPhaseIndex) ? phasePlan[currentPhaseIndex] : nil
    }

    private var phaseTotal: TimeInterval? {
        guard let p = currentFocusPhase else { return nil }
        return TimeInterval(p.durationMinutes * 60)
    }

    // MARK: Public entry points (preserved for FocusView / AppIntents callers)

    /// Start a block-bounded session. `blockMinutes` is the selected block's finite duration; the
    /// phase sequence is planned from it and the chosen `preset`. Defaults preserve the previous
    /// `start(blockTitle:blockID:)` call sites (single 25-minute work phase when nothing is passed).
    func start(blockTitle: String = "Focus session",
               blockID: String? = nil,
               blockMinutes: Int = 25,
               preset: FocusSplitPreset? = nil) {
        self.blockTitle = blockTitle
        self.blockID = blockID
        self.plannedBlockMinutes = max(blockMinutes, 1)
        self.defaultBlockMinutes = self.plannedBlockMinutes
        if let preset { self.preset = preset }
        self.sessionStart = .now
        self.completedWorkSessions = 0
        self.interruptions = 0
        self.blockFullyElapsed = false
        self.phasePlan = planFocusPhases(blockMinutes: self.plannedBlockMinutes, preset: self.preset)
        self.currentPhaseIndex = 0
        beginCurrentPhase()
        startLiveActivity()
    }

    func togglePause() {
        if awaitingPhaseAdvance { return } // pause is a no-op while holding at a boundary
        isPaused.toggle()
        if isPaused {
            ticker?.invalidate()
            interruptions += 1
        } else if phase != .idle && phase != .completed {
            phaseEndsAt = Date.now.addingTimeInterval(remaining)
            runTicker()
        }
        updateLiveActivity()
    }

    /// Explicit pause/resume (Android parity); `togglePause()` is what the UI binds.
    func pause() { if !isPaused { togglePause() } }
    func resume() { if isPaused { togglePause() } }

    /// Advance to the next planned phase. When holding at a boundary this is the "tap to continue".
    /// Advancing past the last phase COMPLETES the session (no looping).
    func advancePhase() {
        guard phase != .idle, phase != .completed else { return }
        if currentPhaseIndex >= phasePlan.count - 1 {
            completeSession()
            return
        }
        currentPhaseIndex += 1
        awaitingPhaseAdvance = false
        beginCurrentPhase()
    }

    /// Skip the current phase early — same destination as advancing (next phase, or completion).
    func skipPhase() {
        ticker?.invalidate()
        advancePhase()
    }

    /// Extend the current phase by `minutes` (the Live Activity / notification "+5m" control).
    /// If holding at a boundary, this re-opens the just-ended phase for the extra time. No-op when
    /// idle or completed.
    func extend(minutes: Int) {
        guard phase != .idle, phase != .completed, minutes > 0 else { return }
        awaitingPhaseAdvance = false
        remaining += TimeInterval(minutes * 60)
        if !isPaused {
            phaseEndsAt = Date.now.addingTimeInterval(remaining)
            runTicker()
        }
        updateLiveActivity()
    }

    func stop(context: ModelContext? = nil) {
        ticker?.invalidate()
        // Completed if the block ran its full course OR the session already finished; an early stop
        // (user ends mid-phase) logs as not completed.
        logSession(context: context, completed: phase == .completed || blockFullyElapsed)
        phase = .completed
        awaitingPhaseAdvance = false
        endLiveActivity()
    }

    // MARK: Phase machine

    private func beginCurrentPhase() {
        guard let p = currentFocusPhase else { completeSession(); return }
        phase = (p.kind == .work) ? .work : .shortBreak
        isPaused = false
        awaitingPhaseAdvance = false
        remaining = phaseTotal ?? 0
        phaseEndsAt = Date.now.addingTimeInterval(remaining)
        runTicker()
        updateLiveActivity()
    }

    /// Called when the current phase reaches 0. Instead of auto-advancing we HOLD at the boundary
    /// (Android `awaitingPhaseAdvance`); the last phase reaching 0 leaves the user to Stop/complete.
    private func reachPhaseBoundary() {
        ticker?.invalidate()
        remaining = 0
        if currentFocusPhase?.kind == .work { completedWorkSessions += 1 }
        // HOLD at the boundary instead of auto-rolling (Android `awaitingPhaseAdvance`). When this
        // was the last phase, `nextPhase == nil` so the UI shows a "Finish block" CTA; otherwise it
        // shows "Start break" / "Start next focus".
        if currentPhaseIndex >= phasePlan.count - 1 { blockFullyElapsed = true }
        awaitingPhaseAdvance = true
        updateLiveActivity()
    }

    private func completeSession() {
        ticker?.invalidate()
        phase = .completed
        awaitingPhaseAdvance = false
        // Note: persistence happens in stop(context:) where the ModelContext is available.
        endLiveActivity()
    }

    private func runTicker() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func tick() {
        guard !isPaused, !awaitingPhaseAdvance, let end = phaseEndsAt else { return }
        remaining = max(0, end.timeIntervalSinceNow)
        if remaining <= 0 {
            reachPhaseBoundary()
        }
    }

    private func logSession(context: ModelContext?, completed: Bool) {
        guard let context, let start = sessionStart else { return }
        let actual = Int(Date.now.timeIntervalSince(start) / 60)
        let session = FocusSession(
            blockID: blockID,
            plannedDurationMinutes: focusMinutes(in: phasePlan),
            actualDurationMinutes: actual,
            interruptions: interruptions,
            startedAt: start,
            completedAt: .now,
            isCompleted: completed)
        context.insert(session)
        try? context.save()
    }

    // MARK: Live Activity bridge

    private func contentState() -> FocusActivityAttributes.ContentState {
        let mapped: FocusActivityAttributes.ContentState.Phase = switch phase {
            case .shortBreak: .shortBreak
            case .longBreak: .longBreak
            default: .work
        }
        return .init(
            phase: mapped,
            phaseEndsAt: phaseEndsAt ?? .now,
            isPaused: isPaused,
            awaitingAdvance: awaitingPhaseAdvance,
            phaseNumber: phaseNumber,
            totalPhases: max(totalPhases, 1))
    }

    private func startLiveActivity() {
        guard ChronosSettings.shared.focusLiveActivityEnabled else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attributes = FocusActivityAttributes(blockTitle: blockTitle)
        activity = try? Activity.request(
            attributes: attributes,
            content: .init(state: contentState(), staleDate: nil))
    }

    private func updateLiveActivity() {
        // Mirror to the watch regardless of whether a Live Activity is running.
        publishWatchFocus(active: true)
        guard let activity else { return }
        Task { await activity.update(.init(state: contentState(), staleDate: nil)) }
    }

    private func endLiveActivity() {
        publishWatchFocus(active: false)
        guard let activity else { return }
        Task { await activity.end(nil, dismissalPolicy: .immediate) }
        self.activity = nil
    }

    /// Publish (or clear) the live focus state for the paired watch — the iOS analogue of the
    /// Android Wear OngoingActivity focus mirror. Independent of the Live Activity setting.
    private func publishWatchFocus(active: Bool) {
        if active {
            PhoneWatchSync.publishFocusState(WatchFocusState(
                blockTitle: blockTitle,
                phaseEndsAt: phaseEndsAt ?? .now,
                isPaused: isPaused,
                awaitingAdvance: awaitingPhaseAdvance,
                phaseNumber: phaseNumber,
                totalPhases: max(totalPhases, 1)))
        } else {
            PhoneWatchSync.publishFocusState(nil)
        }
        PhoneWatchSync.shared.pushSnapshot()
    }
}
