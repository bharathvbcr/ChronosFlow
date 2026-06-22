import WidgetKit
import SwiftUI
import ChronosCore

// MARK: - Focus widget (home-screen) — the active+idle counterpart to the Focus Live Activity.
//
// Ports the Android home-screen focus glance. The widget runs in the extension process and cannot
// touch the in-app `FocusTimerModel`, so it reads a published `FocusWidgetSnapshot` from App-Group
// `UserDefaults` (via `FocusWidgetBridge`) and drives the session through `FocusCommandBridge`
// AppIntents — the buttons actually mutate state (the app applies the queued command to the live
// timer). When no session is running it shows an idle "Start focus" affordance that opens the app.

struct FocusEntry: TimelineEntry {
    let date: Date
    let snapshot: FocusWidgetSnapshot
}

struct FocusProvider: TimelineProvider {
    func placeholder(in context: Context) -> FocusEntry {
        FocusEntry(date: .now, snapshot: FocusWidgetSnapshot(
            isActive: true, blockTitle: "Deep work", phaseIsBreak: false, isPaused: false,
            awaitingAdvance: false, phaseNumber: 1, totalPhases: 4,
            phaseEndsAt: .now.addingTimeInterval(20 * 60),
            currentPhaseTotalSeconds: 25 * 60, currentPhaseIndex: 0, phasePlanEncoded: "F25,B5,F25,B5"))
    }

    func getSnapshot(in context: Context, completion: @escaping (FocusEntry) -> Void) { completion(load()) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FocusEntry>) -> Void) {
        let entry = load()
        // While a session is running, refresh at the phase boundary so the widget flips to the next
        // phase / idle promptly; otherwise check back in 15 minutes.
        let policy: TimelineReloadPolicy
        if entry.snapshot.isActive {
            policy = .after(entry.snapshot.phaseEndsAt.addingTimeInterval(2))
        } else {
            policy = .after(Calendar.current.date(byAdding: .minute, value: 15, to: .now) ?? .now)
        }
        completion(Timeline(entries: [entry], policy: policy))
    }

    private func load() -> FocusEntry {
        let defaults = UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
        return FocusEntry(date: .now, snapshot: FocusWidgetBridge.read(from: defaults))
    }
}

struct FocusWidgetView: View {
    var entry: FocusEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            if entry.snapshot.isActive {
                ActiveFocusView(snapshot: entry.snapshot, family: family)
            } else {
                IdleFocusView()
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

// MARK: - Active session

private struct ActiveFocusView: View {
    let snapshot: FocusWidgetSnapshot
    let family: WidgetFamily

    var body: some View {
        // Whole-card tap opens the Focus section (the buttons are precise interactive targets within).
        Link(destination: URL(string: "chronosflow://focus")!) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: leadingIcon)
                        .foregroundStyle(snapshot.phaseIsBreak ? ChronosColors.brandSecondary : ChronosColors.brandPrimary)
                    Text(phaseLabel).font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    if snapshot.totalPhases > 1 {
                        Text("\(snapshot.phaseNumber)/\(snapshot.totalPhases)")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                Text(snapshot.blockTitle.isEmpty ? "Focus session" : snapshot.blockTitle)
                    .font(.headline).lineLimit(1)

                if snapshot.awaitingAdvance {
                    Text("Tap to continue").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text(timerInterval: Date.now...snapshot.phaseEndsAt, countsDown: true)
                        .font(.system(.title2, design: .rounded).monospacedDigit())
                        .foregroundStyle(.primary)
                }

                // Segmented (Pomodoro) bar for a split session; a flat session shows nothing here.
                let segments = snapshot.barSegments()
                if segments.count > 1 {
                    FocusWidgetSegmentedBar(segments: segments)
                }

                Spacer(minLength: 0)
                controls
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }

    private var leadingIcon: String {
        if snapshot.awaitingAdvance { return "hand.tap.fill" }
        if snapshot.isPaused { return "pause.circle.fill" }
        return snapshot.phaseIsBreak ? "cup.and.saucer.fill" : "timer"
    }

    private var phaseLabel: String {
        if snapshot.awaitingAdvance { return "Holding" }
        return snapshot.phaseIsBreak ? "Break" : "Focus"
    }

    /// Interactive controls — same FocusCommandBridge intents the Live Activity uses, so they mutate
    /// the live session. Holding at a boundary shows a single "Continue"; otherwise Pause/Resume,
    /// +5m, and Stop.
    @ViewBuilder private var controls: some View {
        if snapshot.awaitingAdvance {
            Button(intent: FocusAdvanceIntent()) {
                Label("Continue", systemImage: "play.fill").font(.caption)
            }
            .buttonStyle(.borderedProminent)
            .tint(ChronosColors.brandPrimary)
        } else {
            HStack(spacing: 8) {
                Button(intent: FocusPauseIntent()) {
                    Label(snapshot.isPaused ? "Resume" : "Pause",
                          systemImage: snapshot.isPaused ? "play.fill" : "pause.fill")
                        .labelStyle(.iconOnly).font(.caption)
                }
                .buttonStyle(.bordered).tint(ChronosColors.brandPrimary)

                if family != .systemSmall {
                    Button(intent: FocusExtendIntent(minutes: 5)) {
                        Label("+5m", systemImage: "goforward.plus").font(.caption2)
                    }
                    .buttonStyle(.bordered)
                }

                Button(intent: FocusStopIntent()) {
                    Label("Stop", systemImage: "stop.fill").labelStyle(.iconOnly).font(.caption)
                }
                .buttonStyle(.bordered).tint(.red)
            }
        }
    }
}

// MARK: - Idle (no session)

private struct IdleFocusView: View {
    var body: some View {
        // No live session: a start affordance. `StartFocusIntent` opens the app and hands off the
        // request via FocusCommandBridge; an empty blockID means "start the default/quick session".
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "timer").foregroundStyle(ChronosColors.brandPrimary)
                Text("Focus").font(.caption).foregroundStyle(.secondary)
            }
            Text("No active session").font(.subheadline).foregroundStyle(.secondary)
            Spacer(minLength: 0)
            Button(intent: StartFocusIntent(blockID: "")) {
                Label("Start focus", systemImage: "play.fill").font(.caption)
            }
            .buttonStyle(.borderedProminent)
            .tint(ChronosColors.brandPrimary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

// MARK: - Segmented bar

/// Thin multi-segment progress bar for a split (Pomodoro) session — one capsule per work/break phase,
/// sized by `fractionalWidth` and tinted by `FocusSegmentColorKind`. Renders directly from
/// `ChronosCore.FocusBarSegment` (the same logic the Live Activity bar uses).
struct FocusWidgetSegmentedBar: View {
    let segments: [FocusBarSegment]

    var body: some View {
        GeometryReader { geo in
            HStack(spacing: 2) {
                ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                    Capsule()
                        .fill(color(for: segment))
                        .frame(width: max(2, segment.fractionalWidth * available(geo.size.width)))
                }
            }
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .frame(height: 5)
        .accessibilityHidden(true)
    }

    private func available(_ total: CGFloat) -> CGFloat {
        max(0, total - CGFloat(max(segments.count - 1, 0)) * 2)
    }

    /// Maps the portable color kind to the brand palette — mirrors Android `focusSegmentColorRes`
    /// precedence (break → current-state → steady), matching the Live Activity bar.
    private func color(for segment: FocusBarSegment) -> Color {
        switch segment.colorKind {
        case .break:
            return ChronosColors.brandSecondary
        case .workSteady:
            return ChronosColors.brandPrimary.opacity(0.55)
        case .workCurrent(let state):
            switch state {
            case .running:    return ChronosColors.brandPrimary
            case .endingSoon: return ChronosColors.brandAccent
            case .paused:     return ChronosColors.brandPrimary.opacity(0.4)
            }
        }
    }
}

struct FocusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosFocusWidget", provider: FocusProvider()) { entry in
            FocusWidgetView(entry: entry)
        }
        .configurationDisplayName("Focus")
        .description("Control your focus session — start, pause, extend, or stop from the Home Screen.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemMedium) {
    FocusWidget()
} timeline: {
    FocusEntry(date: .now, snapshot: FocusWidgetSnapshot(
        isActive: true, blockTitle: "Deep work", phaseIsBreak: false, isPaused: false,
        awaitingAdvance: false, phaseNumber: 1, totalPhases: 4,
        phaseEndsAt: .now.addingTimeInterval(20 * 60),
        currentPhaseTotalSeconds: 25 * 60, currentPhaseIndex: 0, phasePlanEncoded: "F25,B5,F25,B5"))
    FocusEntry(date: .now, snapshot: .idle)
}
