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
    @Environment(ShellState.self) private var shell: ShellState?
    @Query(sort: \TimeBlock.startMinuteOfDay) private var allBlocks: [TimeBlock]
    @State private var timer = FocusTimerModel.shared
    /// Drives the repeating opacity pulse on the active phase dot (see `phaseDots`).
    @State private var pulseOpacity: Double = 1.0
    /// Scroll target when a notification/deep link pre-selects a block (`chronosflow://focus?blockId=…`).
    @State private var scrollTarget: String?
    var prefilledBlockID: String? = nil

    private var todayBlocks: [TimeBlock] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        return allBlocks.filter { cal.isDate($0.date, inSameDayAs: today) }
    }

    private var nowMinute: Int {
        let cal = Calendar.current
        return cal.component(.hour, from: .now) * 60 + cal.component(.minute, from: .now)
    }

    var body: some View {
        NavigationStack {
            chromedSurface
            .onAppear {
                FocusCommandObserver.startIfNeeded()
                drainPendingFocusBlock()
                timer.attach(context: context)
                timer.recoverBoundaryIfNeeded()
                drainFocusCommands()
                if timer.phase == .idle || timer.phase == .completed {
                    timer.preset = ChronosSettings.shared.focusLastPreset
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active {
                    drainPendingFocusBlock()
                    timer.recoverBoundaryIfNeeded()
                    timer.attach(context: context)
                    drainFocusCommands()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: FocusCommandObserver.didReceive)) { _ in
                drainFocusCommands()
            }
            .onChange(of: shell?.pendingFocusBlockID) { _, id in
                if id != nil { drainPendingFocusBlock() }
            }
            .sensoryFeedback(.impact, trigger: timer.phase)
            .sensoryFeedback(.success, trigger: timer.completedWorkSessions)
        }
    }

    private var chromedSurface: some View {
        focusSurface
            .navigationTitle("Focus")
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
    }

    private var focusSurface: some View {
        ZStack {
            ChronosBackdrop()
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: ChronosSpacing.hero) {
                        if !todayBlocks.isEmpty {
                            focusSection {
                                focusDialCard
                                    .id(FocusScrollAnchor.dial)
                            }
                        }
                        focusSection {
                            Text(phaseTitle).font(.chronosTitle).foregroundStyle(.secondary)
                            timerRing
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
                    }
                    .padding(.vertical, ChronosSpacing.standard)
                }
                .scrollContentBackground(.hidden)
                .onChange(of: scrollTarget) { _, target in
                    guard let target else { return }
                    withAnimation(ChronosMotion.snappy) { proxy.scrollTo(target, anchor: .center) }
                    scrollTarget = nil
                }
            }
        }
    }

    private func focusSection<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().padding(.horizontal, ChronosSpacing.standard)
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
        // The ring is the hero element; expose one clean spoken value instead of letting VoiceOver
        // read the decorative sub-icon + "25:00" as digits-and-"colon" (§7). `children: .ignore`
        // collapses the ring into a single element carrying the countdown as words.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Focus timer")
        .accessibilityValue(ringAccessibilityValue)
    }

    /// Spoken value for the timer ring — the countdown as words ("24 minutes 30 seconds remaining"),
    /// which reads far better than the visual "MM:SS". Covers idle / running / paused / boundary-hold.
    private var ringAccessibilityValue: String {
        if timer.awaitingPhaseAdvance {
            return timer.nextPhase == nil ? "Block complete" : "Phase complete, ready to continue"
        }
        let total = Int(timer.phase == .idle ? TimeInterval(timer.workMinutes * 60) : timer.remaining)
        let m = total / 60, s = total % 60
        var spoken = m > 0 ? "\(m) minute\(m == 1 ? "" : "s")" : ""
        if s > 0 { spoken += (m > 0 ? " " : "") + "\(s) second\(s == 1 ? "" : "s")" }
        if spoken.isEmpty { spoken = "0 seconds" }
        return timer.isPaused ? "\(spoken) remaining, paused" : "\(spoken) remaining"
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
                    // Selection is shown visually via tint; announce it to VoiceOver too so the
                    // active split isn't conveyed by color alone (§7).
                    .accessibilityAddTraits(timer.preset == preset ? [.isSelected] : [])
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

    /// Read-only day dial — highlights the deep-linked / pre-selected block (Android FocusTab block card).
    private var focusDialCard: some View {
        VStack(spacing: ChronosSpacing.small) {
            if let block = block {
                Text(block.title).font(.chronosLabel).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            ChronosDialCanvas(
                blocks: todayBlocks,
                nowMinute: nowMinute,
                selectedBlockID: prefilledBlockID,
                conflictBlockIDs: Set(PlannerMath.conflicts(in: todayBlocks).flatMap { [$0.firstID, $0.secondID] }),
                showNowHand: true
            )
            .allowsHitTesting(false)
            .frame(maxWidth: 320)
            .frame(maxWidth: .infinity)
        }
        .accessibilityLabel(block.map { "Selected block: \($0.title)" } ?? "Today's schedule")
    }

    /// Notification / deep link (`chronosflow://focus?blockId=…`) — scroll the dial into view.
    private func drainPendingFocusBlock() {
        guard let id = shell?.pendingFocusBlockID ?? prefilledBlockID else { return }
        shell?.pendingFocusBlockID = nil
        guard todayBlocks.contains(where: { $0.id == id }) else { return }
        scrollTarget = FocusScrollAnchor.dial
    }

    private enum FocusScrollAnchor {
        static let dial = "focus-dial"
    }

    private var block: TimeBlock? {
        guard let id = prefilledBlockID else { return nil }
        return try? context.fetch(FetchDescriptor<TimeBlock>(
            predicate: #Predicate { $0.id == id })).first
    }

    private var blockTitle: String { block?.title ?? "Focus session" }
    private var blockMinutes: Int { block?.durationMinutes ?? 25 }
}

#Preview { FocusView().modelContainer(ChronosStore.previewContainer()) }
