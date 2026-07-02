import SwiftUI
import SwiftData
import ChronosCore

/// The Focus tab: turn a planned work block into a running BLOCK-BOUNDED focus session with a glass
/// timer ring, a fixed work/break phase split (preset chips), phase dots, and a Live Activity on the
/// Lock Screen / Dynamic Island. At each phase boundary the session HOLDS and shows a "tap to
/// continue" button instead of auto-rolling (the iOS port of Android `awaitingPhaseAdvance`).
struct FocusView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @State private var timer = FocusTimerModel.shared
    /// Drives the repeating opacity pulse on the active phase dot (see `phaseDots`).
    @State private var pulseOpacity: Double = 1.0
    var prefilledBlockID: String? = nil

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                VStack(spacing: ChronosSpacing.hero) {
                    Text(phaseTitle).font(.chronosTitle).foregroundStyle(.secondary)
                    timerRing
                    // Paused affordance. Mirrors Android's "Paused · tap play to resume" copy, shown
                    // only while genuinely paused mid-phase (not while holding at a phase boundary,
                    // where pause is a no-op and a different prompt applies).
                    if timer.isPaused && timer.phase != .idle && !timer.awaitingPhaseAdvance {
                        Label("Paused · tap play to resume", systemImage: "pause.fill")
                            .font(.chronosLabel).foregroundStyle(.secondary)
                            .transition(.opacity)
                    }
                    if timer.isSplitSession && timer.phase != .idle { phaseDots }
                    controls
                    if timer.completedWorkSessions > 0 {
                        Label("\(timer.completedWorkSessions) focus phases done", systemImage: "checkmark.seal.fill")
                            .font(.chronosLabel).foregroundStyle(ChronosColors.brandSecondary)
                    }
                }
                .padding(ChronosSpacing.medium)
            }
            .navigationTitle("Focus")
            .chronosCommandPaletteToolbar()
            // Apply any focus controls that arrived from the Live Activity / Today widget while we
            // were backgrounded or on another tab (FocusCommandBridge — see WidgetIntents.swift).
            .onAppear {
                FocusCommandObserver.startIfNeeded()
                // F03: explicitly restore an in-flight SPLIT session left by a previous app run BEFORE
                // anything else, so a session survives relaunch. Mirrors Android
                // `DayDialFocusDelegate.restoreActiveSplitFromStore()`: `attach` only rehydrates when
                // the live model is idle and a RUNNING/PAUSED snapshot exists, so it never clobbers an
                // already-running session. After restore, re-sync a boundary that may have elapsed
                // while the app was killed/backgrounded.
                timer.attach(context: context)
                timer.recoverBoundaryIfNeeded()
                drainFocusCommands()
                // Restore the user's last-used split preset (persisted in ChronosSettings) when idle,
                // so a new session defaults to their preference instead of the hard-coded 25 · 5.
                if timer.phase == .idle || timer.phase == .completed {
                    timer.preset = ChronosSettings.shared.focusLastPreset
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active {
                    // Re-sync across a backgrounded phase boundary (the Timer doesn't fire while
                    // suspended), then restore any session persisted by a kill, then drain controls.
                    timer.recoverBoundaryIfNeeded()
                    timer.attach(context: context)
                    drainFocusCommands()
                }
            }
            // A control tapped while we're already foregrounded on this tab applies immediately.
            .onReceive(NotificationCenter.default.publisher(for: FocusCommandObserver.didReceive)) { _ in
                drainFocusCommands()
            }
            // Tactile feedback for the key focus transitions, keyed to observable timer state so it
            // fires regardless of which control (in-app, Live Activity, widget) drove the change.
            // `.sensoryFeedback` automatically respects the system haptics setting.
            // Phase flips on start and at every boundary advance (start / advance / break transitions).
            .sensoryFeedback(.impact, trigger: timer.phase)
            // A focus-phase completion accent — mirrors SleepView's `.success` precedent.
            .sensoryFeedback(.success, trigger: timer.completedWorkSessions)
        }
    }

    /// Drains queued cross-process focus commands and applies them to the live timer.
    private func drainFocusCommands() {
        FocusCommandBridge.drain { command in
            switch command {
            case .start(let blockID):
                let id = blockID
                let block = try? context.fetch(
                    FetchDescriptor<TimeBlock>(predicate: #Predicate { $0.id == id })).first
                timer.start(blockTitle: block?.title ?? "Focus session",
                            blockID: blockID,
                            blockMinutes: block?.durationMinutes ?? 25)
            case .togglePause: timer.togglePause()
            case .stop: timer.stop(context: context)
            case .extend(let mins): timer.extend(minutes: mins)
            case .advance: timer.advancePhase()
            }
        }
    }

    private var phaseTitle: String {
        if timer.awaitingPhaseAdvance { return timer.nextPhase == nil ? "Block complete" : "Phase complete" }
        switch timer.phase {
        case .idle: return "Ready to focus"
        case .work: return "Focus"
        case .shortBreak, .longBreak: return "Break"
        case .completed: return "Session complete"
        }
    }

    // MARK: Timer ring

    private var timerRing: some View {
        ZStack {
            Circle().stroke(.secondary.opacity(0.2), lineWidth: 16)
            Circle()
                .trim(from: 0, to: timer.phase == .idle ? 0 : timer.progress)
                .stroke(ringColor.gradient, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(ChronosMotion.smooth, value: timer.progress)
            VStack(spacing: ChronosSpacing.micro) {
                if timer.awaitingPhaseAdvance {
                    Image(systemName: timer.nextPhase?.kind == .break ? "cup.and.saucer.fill" : "checkmark.circle.fill")
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(ringColor)
                        .symbolEffect(.bounce, value: timer.awaitingPhaseAdvance)
                } else {
                    HStack(spacing: ChronosSpacing.small) {
                        if timer.phase != .idle {
                            Image(systemName: timer.isPaused ? "pause.fill" : "circle.fill")
                                .font(.caption2)
                                .foregroundStyle(ringColor)
                                .symbolEffect(.pulse, isActive: !timer.isPaused)
                        }
                        Text(timeString)
                            .font(.system(size: 56, weight: .bold, design: .rounded)).monospacedDigit()
                    }
                }
            }
        }
        .frame(width: 260, height: 260)
        .padding(ChronosSpacing.medium)
    }

    private var ringColor: Color {
        let breakish = timer.phase == .shortBreak || timer.phase == .longBreak || timer.nextPhase?.kind == .break
        return breakish ? ChronosColors.brandSecondary : ChronosColors.brandPrimary
    }

    private var timeString: String {
        let total = timer.phase == .idle ? TimeInterval(timer.workMinutes * 60) : timer.remaining
        return String(format: "%02d:%02d", Int(total) / 60, Int(total) % 60)
    }

    // MARK: Phase dots

    /// Honors the system/app reduced-motion setting (same source as `PressableScale`), so the
    /// decorative dot pulse never runs when motion is disabled (UX principles §4).
    private var reduceMotion: Bool { ChronosSettings.shared.motionDisabled(systemReduceMotion) }

    /// True while the current phase is actively running AND motion is allowed (drives the
    /// active-dot pulse). Gating here stops the purely decorative pulse under reduced motion.
    private var phaseIsRunning: Bool {
        timer.phase != .idle && !timer.isPaused && !timer.awaitingPhaseAdvance && !reduceMotion
    }

    private var phaseDots: some View {
        HStack(spacing: ChronosSpacing.small) {
            ForEach(0..<timer.totalPhases, id: \.self) { index in
                let isCurrent = index == timer.phaseNumber - 1
                Circle()
                    .fill(dotColor(for: index))
                    .frame(width: isCurrent ? 12 : 8, height: isCurrent ? 12 : 8)
                    // Continuously pulse the active dot while the phase is running (Android's
                    // active-phase emphasis). `Circle` is a Shape, not an SF Symbol, so we drive the
                    // pulse with a repeating opacity animation rather than `.symbolEffect`.
                    .opacity(isCurrent && phaseIsRunning ? pulseOpacity : 1.0)
                    .animation(ChronosMotion.bouncy, value: timer.phaseNumber)
                    .animation(
                        isCurrent && phaseIsRunning
                            ? ChronosMotion.smooth.repeatForever(autoreverses: true)
                            : .default,
                        value: pulseOpacity)
            }
        }
        .onAppear { pulseOpacity = 0.4 }
        .accessibilityLabel("Phase \(timer.phaseNumber) of \(timer.totalPhases)")
    }

    private func dotColor(for index: Int) -> Color {
        if index < timer.phaseNumber - 1 { return ChronosColors.brandSecondary.opacity(0.7) } // done
        if index == timer.phaseNumber - 1 { return ChronosColors.brandPrimary }               // current
        return .secondary.opacity(0.25)                                                        // upcoming
    }

    // MARK: Controls

    @ViewBuilder private var controls: some View {
        if timer.phase == .idle || timer.phase == .completed {
            VStack(spacing: ChronosSpacing.medium) {
                presetChips
                Button {
                    timer.start(blockTitle: blockTitle, blockID: prefilledBlockID,
                                blockMinutes: blockMinutes, preset: timer.preset)
                } label: {
                    Label("Start focus", systemImage: "play.fill").frame(maxWidth: 220)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
            }
        } else if timer.awaitingPhaseAdvance {
            boundaryControls
        } else {
            runningControls
        }
    }

    private var presetChips: some View {
        VStack(spacing: 6) {
            Text("Split").font(.chronosCaption).foregroundStyle(.secondary)
            HStack(spacing: ChronosSpacing.small) {
                ForEach(FocusSplitPreset.allCases, id: \.self) { preset in
                    Button {
                        timer.preset = preset
                        // Remember the choice across launches (Android FocusTab resets per session,
                        // but persisting is the noted iOS UX polish — see parity F: preset config).
                        ChronosSettings.shared.focusLastPreset = preset
                    } label: {
                        Text(preset.label)
                            .font(.chronosLabel)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                    }
                    .buttonStyle(.bordered)
                    .tint(timer.preset == preset ? ChronosColors.brandPrimary : .secondary)
                    .buttonBorderShape(.capsule)
                }
            }
        }
    }

    private var boundaryControls: some View {
        VStack(spacing: ChronosSpacing.medium) {
            if let prompt = timer.boundaryPrompt {
                Text(prompt).font(.chronosBody).multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
            }
            if timer.nextPhase != nil {
                Button { timer.advancePhase() } label: {
                    Label(continueLabel, systemImage: timer.nextPhase?.kind == .break ? "cup.and.saucer.fill" : "play.fill")
                        .frame(maxWidth: 220)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
            } else {
                Button { timer.stop(context: context) } label: {
                    Label("Finish block", systemImage: "checkmark.circle.fill").frame(maxWidth: 220)
                }
                .buttonStyle(.borderedProminent).tint(ChronosColors.brandSecondary).controlSize(.large)
            }
            Button { timer.stop(context: context) } label: {
                Text("End session").font(.chronosLabel)
            }
            .buttonStyle(.plain).foregroundStyle(.secondary)
        }
    }

    private var continueLabel: String {
        timer.nextPhase?.kind == .break ? "Start break" : "Start next focus"
    }

    private var runningControls: some View {
        VStack(spacing: ChronosSpacing.medium) {
            // Mid-session break injection — only during an ACTIVE work phase (not paused / boundary),
            // matching Android's FocusTab guard rails. Offers the 5 / 10 / 15m presets.
            if timer.phase == .work && !timer.isPaused {
                breakInjectionChips
            }
            // Early end-of-break — only while a break is actively running, mirroring Android's
            // onEndBreak. Finishes the block when the break is the last phase.
            if (timer.phase == .shortBreak || timer.phase == .longBreak) && !timer.isPaused {
                Button {
                    if timer.endBreakNow() { timer.stop(context: context) }
                } label: {
                    Label("End break", systemImage: "forward.end.fill")
                        .font(.chronosCaption)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(ChronosColors.brandSecondary.opacity(0.15), in: Capsule())
                }
                .buttonStyle(.plain)
                .foregroundStyle(ChronosColors.brandSecondary)
            }
            // Adjust the current phase length on the fly (Android +/- session controls).
            adjustControls
            HStack(spacing: ChronosSpacing.large) {
            Button { timer.togglePause() } label: {
                Image(systemName: timer.isPaused ? "play.fill" : "pause.fill").font(.title)
            }
            .buttonStyle(.bordered).buttonBorderShape(.circle).controlSize(.large)
            .accessibilityLabel(timer.isPaused ? "Resume" : "Pause")

            Button { timer.stop(context: context) } label: {
                Image(systemName: "stop.fill").font(.title)
            }
            .buttonStyle(.borderedProminent).tint(ChronosColors.brandAccent)
            .buttonBorderShape(.circle).controlSize(.large)
            .accessibilityLabel("Stop session")

            // Skip advances to the next phase boundary (Android's ⏭ skip). Only meaningful for a
            // multi-phase split session — for a flat session there's no next phase to skip into, so
            // the Stop button is the correct affordance. Hidden while paused to match Android.
            if timer.isSplitSession && !timer.isPaused {
                Button { timer.skipPhase() } label: {
                    Image(systemName: "forward.fill").font(.title)
                }
                .buttonStyle(.bordered).buttonBorderShape(.circle).controlSize(.large)
                .accessibilityLabel("Skip phase")
            }
            }
        }
    }

    /// The 5 / 10 / 15m mid-session break chips (Android `FocusMidSessionBreakPresets`). Each chip
    /// injects a break right after the current work phase via `injectBreak(minutes:)`.
    private var breakInjectionChips: some View {
        VStack(spacing: 6) {
            Text("Take a break").font(.chronosCaption).foregroundStyle(.secondary)
            HStack(spacing: ChronosSpacing.small) {
                ForEach(timer.breakPresets, id: \.self) { minutes in
                    Button { timer.injectBreak(minutes: minutes) } label: {
                        Label("\(minutes)m", systemImage: "cup.and.saucer.fill")
                            .font(.chronosLabel)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                    }
                    .buttonStyle(.bordered)
                    .tint(ChronosColors.brandSecondary)
                    .buttonBorderShape(.capsule)
                }
            }
        }
    }

    /// Lengthen (+5m / +15m) or shorten (-5m / -15m) the CURRENT phase mid-session. Mirrors Android's
    /// FocusTab two-row extend/shorten controls (`onExtend` / `onShorten`): extend adds to the live
    /// countdown, shorten subtracts with a 1-minute floor (`FocusTimerModel.shorten`, the analogue of
    /// Android's `coerceAtLeast(5)`). All four are hidden while holding at a boundary (handled by the
    /// caller showing `runningControls` only when not awaiting advance).
    private var adjustControls: some View {
        HStack(spacing: ChronosSpacing.medium) {
            Button { timer.shorten(minutes: 15) } label: {
                Label("15m", systemImage: "minus").font(.chronosCaption)
                    .padding(.horizontal, 10).padding(.vertical, 6)
            }
            .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(ChronosColors.brandSecondary)
            .accessibilityLabel("Subtract 15 minutes")

            Button { timer.shorten(minutes: 5) } label: {
                Label("5m", systemImage: "minus").font(.chronosCaption)
                    .padding(.horizontal, 10).padding(.vertical, 6)
            }
            .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(ChronosColors.brandSecondary)
            .accessibilityLabel("Subtract 5 minutes")

            Button { timer.extend(minutes: 5) } label: {
                Label("5m", systemImage: "plus").font(.chronosCaption)
                    .padding(.horizontal, 10).padding(.vertical, 6)
            }
            .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(ChronosColors.brandPrimary)
            .accessibilityLabel("Add 5 minutes")

            Button { timer.extend(minutes: 15) } label: {
                Label("15m", systemImage: "plus").font(.chronosCaption)
                    .padding(.horizontal, 10).padding(.vertical, 6)
            }
            .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(ChronosColors.brandPrimary)
            .accessibilityLabel("Add 15 minutes")
        }
    }

    // MARK: Block lookup

    private var block: TimeBlock? {
        guard let id = prefilledBlockID else { return nil }
        return try? context.fetch(FetchDescriptor<TimeBlock>(
            predicate: #Predicate { $0.id == id })).first
    }

    private var blockTitle: String { block?.title ?? "Focus session" }
    private var blockMinutes: Int { block?.durationMinutes ?? 25 }
}

#Preview { FocusView().modelContainer(ChronosStore.previewContainer()) }
