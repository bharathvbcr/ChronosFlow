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

    /// SwiftData context used to persist the split-session snapshot (F03) so a session survives an
    /// app kill. Set once via `attach(context:)` from app-entry / FocusView.onAppear; nil means
    /// snapshots are skipped (no store available).
    private var snapshotContext: ModelContext?

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

    /// Insert an immediate short break WITHOUT ending the current work phase.
    /// The break plays out, then awaitingPhaseAdvance fires so the user taps to resume work.
    /// This is the iOS port of the Android onInjectBreak() fix.
    func injectBreak(minutes: Int) {
        guard phase == .work, !isPaused, !awaitingPhaseAdvance, minutes > 0 else { return }
        // Pause the current work phase at the boundary by setting awaitingPhaseAdvance, then
        // splice a synthetic short-break phase immediately after the current position so that
        // advancePhase() runs it next. The work phases that follow are preserved unchanged.
        let breakPhase = FocusPhase(kind: .break, durationMinutes: minutes)
        // Insert the injected break right after the current phase index.
        let insertAt = currentPhaseIndex + 1
        phasePlan.insert(breakPhase, at: insertAt)
        // Hold at the boundary — the UI will show the normal "Start break" boundary controls.
        ticker?.invalidate()
        remaining = 0
        if currentFocusPhase?.kind == .work { completedWorkSessions += 1 }
        awaitingPhaseAdvance = true
        updateLiveActivity()
    }

    func stop(context: ModelContext? = nil) {
        ticker?.invalidate()
        // Completed if the block ran its full course OR the session already finished; an early stop
        // (user ends mid-phase) logs as not completed.
        logSession(context: context, completed: phase == .completed || blockFullyElapsed)
        phase = .completed
        awaitingPhaseAdvance = false
        clearSnapshot(context: context)
        endLiveActivity()
    }

    /// The mid-session break lengths offered as chips during an active work phase (minutes). Mirrors
    /// the Android `FocusMidSessionBreakPresets` (5 / 10 / 15). `injectBreak(minutes:)` accepts any
    /// of these.
    var breakPresets: [Int] { [5, 10, 15] }

    /// End an in-progress break early. Mirrors Android `DayDialFocusDelegate.endBreakNow()`:
    /// - Returns `false` and no-ops when not currently on a break.
    /// - Returns `true` when the break is the LAST phase — there is nothing to return to, so the
    ///   caller should finish the session (`stop(context:)`).
    /// - Otherwise drops to the phase boundary (PAUSED-equivalent: holds at `awaitingPhaseAdvance`
    ///   for the next focus phase) and returns `false`.
    @discardableResult
    func endBreakNow() -> Bool {
        guard phase == .shortBreak || phase == .longBreak else { return false }
        // Last phase: nothing to advance into — caller finishes.
        if currentPhaseIndex >= phasePlan.count - 1 {
            ticker?.invalidate()
            blockFullyElapsed = true
            return true
        }
        // Drop to the boundary so the user taps to continue into the next focus phase.
        ticker?.invalidate()
        remaining = 0
        awaitingPhaseAdvance = true
        updateLiveActivity()
        return false
    }

    // MARK: Cross-process command bridge (W01)

    /// Apply a `FocusCommandBridge.Command` drained on app-entry / foreground to the live timer. This
    /// is the app-side mapping the bridge's TODO described — it lets a Live-Activity / widget control
    /// drive the in-memory session. `.start` is intentionally NOT handled here: starting a session
    /// needs the selected `TimeBlock` (title / minutes / preset) resolved from the store, which the
    /// app-entry drain owns; this method only steers an already-running session.
    func apply(command: FocusCommandBridge.Command, context: ModelContext? = nil) {
        switch command {
        case .togglePause:
            togglePause()
        case .stop:
            stop(context: context)
        case .extend(let minutes):
            extend(minutes: minutes)
        case .advance:
            advancePhase()
        case .start:
            // Owned by the app-entry drain (needs TimeBlock resolution); no-op on the live model.
            break
        }
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
        // Note: FocusSession logging happens in stop(context:) where the ModelContext is available.
        clearSnapshot(context: snapshotContext)
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
        // Compute overall block progress for the segmented Live Activity bar.
        let totalSecs = TimeInterval(plannedBlockMinutes * 60)
        let elapsed = totalSecs - (phaseEndsAt.map { max(0, $0.timeIntervalSinceNow) } ?? 0)
        let completion = totalSecs > 0 ? min(1, max(0, elapsed / totalSecs)) : 0
        // Render-ready segmented bar via ChronosCore (empty for a flat session). The current phase's
        // live total is fed in so a +/-5m adjustment is reflected; the bar state mirrors pause /
        // ending-soon emphasis.
        let plan = focusPhaseSegments(from: phasePlan)
        let currentTotalSeconds = Int((phaseTotal ?? 0).rounded())
        let barState = focusBarState(isPaused: isPaused, timeLeftSeconds: Int(remaining.rounded()))
        let segments = focusBarSegments(
            plan: plan,
            currentPhaseIndex: currentPhaseIndex,
            currentPhaseTotalSeconds: max(currentTotalSeconds, 1),
            state: barState)
        return .init(
            phase: mapped,
            phaseEndsAt: phaseEndsAt ?? .now,
            isPaused: isPaused,
            awaitingAdvance: awaitingPhaseAdvance,
            phaseNumber: phaseNumber,
            totalPhases: max(totalPhases, 1),
            blockTitle: blockTitle,
            blockStartsAt: sessionStart,
            blockEndsAt: sessionStart.map { $0.addingTimeInterval(TimeInterval(plannedBlockMinutes * 60)) },
            nextBlockTitle: nil,
            completionPercent: completion,
            phaseSegments: segments.map(FocusActivityAttributes.ContentState.PhaseSegmentInfo.init(from:)),
            currentPhaseIndex: min(max(currentPhaseIndex, 0), max(phasePlan.count - 1, 0)))
    }

    private func startLiveActivity() {
        guard ChronosSettings.shared.focusLiveActivityEnabled else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attributes = FocusActivityAttributes(blockTitle: blockTitle,
                                                  totalPhaseDurationMinutes: plannedBlockMinutes)
        activity = try? Activity.request(
            attributes: attributes,
            content: .init(state: contentState(), staleDate: nil))
    }

    private func updateLiveActivity() {
        // Persist the split snapshot on every state change (F03) so a kill mid-phase resumes exactly.
        saveSnapshot()
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

    // MARK: Split-session persistence (F03)

    /// Attach the SwiftData context and restore any in-flight split session left by a previous app
    /// run. Call once from app-entry / FocusView.onAppear. Mirrors Android
    /// `DayDialFocusDelegate.restoreFromPersistedSession`: only a RUNNING/PAUSED split snapshot is
    /// resumed; a stale or completed snapshot is cleared. Returns true if a session was restored.
    @discardableResult
    func attach(context: ModelContext) -> Bool {
        snapshotContext = context
        // Only restore into a fresh/idle model — never clobber a live session.
        guard phase == .idle else { return false }
        guard let snap = latestSnapshot(in: context) else { return false }
        guard snap.statusValue == .running || snap.statusValue == .paused,
              !snap.phaseList.isEmpty else {
            // Stale/completed snapshot — drop it.
            context.delete(snap)
            try? context.save()
            return false
        }
        restore(from: snap)
        return true
    }

    /// Rehydrate the live timer from a persisted snapshot and resume ticking (unless it was paused).
    private func restore(from snap: FocusSessionSnapshot) {
        blockTitle = snap.blockTitle ?? "Focus session"
        blockID = snap.blockID
        preset = snap.presetValue
        plannedBlockMinutes = max(snap.plannedBlockMinutes, 1)
        defaultBlockMinutes = plannedBlockMinutes
        phasePlan = snap.phaseList
        currentPhaseIndex = min(max(snap.currentIndex, 0), max(phasePlan.count - 1, 0))
        completedWorkSessions = snap.completedWorkSessions
        interruptions = snap.interruptions
        blockFullyElapsed = false
        sessionStart = snap.startedAt ?? .now
        awaitingPhaseAdvance = snap.awaitingAdvance

        guard let p = currentFocusPhase else { phase = .idle; return }
        phase = (p.kind == .work) ? .work : .shortBreak

        if snap.awaitingAdvance {
            // Was holding at a boundary — keep holding; no ticker.
            isPaused = false
            remaining = 0
            phaseEndsAt = nil
        } else if snap.statusValue == .paused {
            isPaused = true
            remaining = max(0, TimeInterval(snap.remainingSeconds))
            phaseEndsAt = nil
        } else {
            // RUNNING: continue counting down from where we left off (wall-clock based).
            isPaused = false
            remaining = max(0, TimeInterval(snap.remainingSeconds))
            phaseEndsAt = Date.now.addingTimeInterval(remaining)
            runTicker()
        }
        startLiveActivity()
        updateLiveActivity()
    }

    /// Persist the current split session, mirroring Android's "only while a SPLIT session is RUNNING
    /// or PAUSED" rule. A flat (single-phase) or terminal session clears any stored snapshot.
    private func saveSnapshot() {
        guard let context = snapshotContext else { return }
        let isLive = phase == .work || phase == .shortBreak || phase == .longBreak
        guard isSplitSession, isLive else {
            clearSnapshot(context: context)
            return
        }
        let status: FocusSessionSnapshot.Status =
            awaitingPhaseAdvance ? .paused : (isPaused ? .paused : .running)
        let snap = latestSnapshot(in: context) ?? {
            let s = FocusSessionSnapshot()
            context.insert(s)
            return s
        }()
        snap.blockID = blockID
        snap.blockTitle = blockTitle
        snap.presetRaw = preset.rawValue
        snap.plannedBlockMinutes = plannedBlockMinutes
        snap.encodePhases(phasePlan)
        snap.currentIndex = currentPhaseIndex
        snap.statusRaw = status.rawValue
        snap.awaitingAdvance = awaitingPhaseAdvance
        snap.remainingSeconds = Int(remaining.rounded())
        snap.completedWorkSessions = completedWorkSessions
        snap.interruptions = interruptions
        snap.startedAt = sessionStart
        snap.updatedAt = .now
        try? context.save()
    }

    /// Remove any persisted snapshot (session ended / not a split / not live).
    private func clearSnapshot(context: ModelContext?) {
        guard let context = context ?? snapshotContext else { return }
        let all = (try? context.fetch(FetchDescriptor<FocusSessionSnapshot>())) ?? []
        guard !all.isEmpty else { return }
        all.forEach { context.delete($0) }
        try? context.save()
    }

    /// Most-recently-updated snapshot (there should be at most one live session).
    private func latestSnapshot(in context: ModelContext) -> FocusSessionSnapshot? {
        var descriptor = FocusSessionSnapshot.fetchDescriptor
        descriptor.fetchLimit = 1
        return (try? context.fetch(descriptor))?.first
    }
}

/// SwiftData snapshot of an in-flight SPLIT focus session, so a session survives an app kill (F03).
/// The iOS analogue of the Android `FocusSplitSessionStore` (SharedPreferences-backed). Persisted
/// only while a split session is RUNNING/PAUSED and cleared otherwise. All stored properties are
/// optional or default-initialized (CloudKit-safe); the phase list is stored as a Codable JSON blob.
@Model
final class FocusSessionSnapshot {
    /// Coarse lifecycle status of the snapshot. Only RUNNING/PAUSED snapshots are resumed.
    enum Status: String, Codable, Sendable {
        case running
        case paused
        case completed
    }

    var blockID: String?
    var blockTitle: String?
    /// `FocusSplitPreset.rawValue`; resolved via `presetValue`.
    var presetRaw: String?
    var plannedBlockMinutes: Int = 25
    /// The planned phase sequence, JSON-encoded `[FocusPhase]`; read via `phaseList`.
    var phasesData: Data?
    var currentIndex: Int = 0
    /// `Status.rawValue`; read via `statusValue`.
    var statusRaw: String?
    var awaitingAdvance: Bool = false
    /// Seconds left in the current phase at the moment of the last save (for PAUSED/RUNNING resume).
    var remainingSeconds: Int = 0
    var completedWorkSessions: Int = 0
    var interruptions: Int = 0
    var startedAt: Date?
    var updatedAt: Date = Date.now

    init() {}

    /// Decoded phase plan (empty if missing/corrupt).
    var phaseList: [FocusPhase] {
        guard let phasesData,
              let decoded = try? JSONDecoder().decode([FocusPhase].self, from: phasesData) else { return [] }
        return decoded
    }

    /// Store the phase plan as a JSON blob.
    func encodePhases(_ phases: [FocusPhase]) {
        phasesData = try? JSONEncoder().encode(phases)
    }

    /// Resolved status (defaults to `.running` for legacy/missing values).
    var statusValue: Status { statusRaw.flatMap(Status.init(rawValue:)) ?? .running }

    /// Resolved preset (defaults to the 25·5 default to match `FocusTimerModel.preset`).
    var presetValue: FocusSplitPreset { presetRaw.flatMap(FocusSplitPreset.init(rawValue:)) ?? .p25_5 }

    /// Newest-first fetch descriptor (the live session, if any).
    static var fetchDescriptor: FetchDescriptor<FocusSessionSnapshot> {
        FetchDescriptor<FocusSessionSnapshot>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)])
    }
}
