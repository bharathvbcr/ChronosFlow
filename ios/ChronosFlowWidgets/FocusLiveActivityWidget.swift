import WidgetKit
import SwiftUI
import ActivityKit

/// Lock Screen + Dynamic Island presentation for a running focus session. This is the iOS-native
/// "Live Update" — the equivalent of the Android focus foreground notification / Live Updates.
///
/// Mirrors the block-bounded phase model: shows phase N of M, a live countdown while running, and a
/// "Tap to continue" prompt while holding at a phase boundary (`awaitingAdvance`).
struct FocusLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            // Lock Screen / banner.
            VStack(spacing: 10) {
                HStack(spacing: 12) {
                    Image(systemName: leadingIcon(context.state))
                        .font(.title2).foregroundStyle(ChronosColors.brandPrimary)
                    VStack(alignment: .leading) {
                        Text(context.attributes.blockTitle).font(.headline)
                        Text(subtitle(context.state)).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    trailing(context.state)
                }
                controls(context.state)
            }
            .padding()
            .activityBackgroundTint(Color.black.opacity(0.4))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.state.phase.title, systemImage: "timer")
                        .font(.caption)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    trailing(context.state).font(.system(.body, design: .rounded).monospacedDigit())
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        Text(context.attributes.blockTitle).font(.headline)
                        Text(subtitle(context.state)).font(.caption2).foregroundStyle(.secondary)
                        controls(context.state)
                    }
                }
            } compactLeading: {
                Image(systemName: "timer").foregroundStyle(ChronosColors.brandPrimary)
            } compactTrailing: {
                if context.state.awaitingAdvance {
                    Image(systemName: "hand.tap.fill").font(.caption2)
                } else {
                    Text(timerInterval: Date.now...context.state.phaseEndsAt, countsDown: true)
                        .font(.caption2.monospacedDigit()).frame(width: 40)
                }
            } minimal: {
                Image(systemName: context.state.awaitingAdvance ? "hand.tap.fill" : "timer")
                    .foregroundStyle(ChronosColors.brandPrimary)
            }
        }
    }

    /// Lock Screen / Dynamic-Island action buttons — the iOS analogue of the Android focus
    /// notification action buttons. Each `Button(intent:)` runs in the widget process and hands the
    /// command to the app via `FocusCommandBridge` (App-Group queue + Darwin notification).
    @ViewBuilder private func controls(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.awaitingAdvance {
            // Holding at a phase boundary: a single "Continue" advances (or finishes) the session.
            Button(intent: FocusAdvanceIntent()) {
                Label("Continue", systemImage: "play.fill").font(.caption)
            }
            .buttonStyle(.borderedProminent)
            .tint(ChronosColors.brandPrimary)
        } else {
            HStack(spacing: 10) {
                Button(intent: FocusPauseIntent()) {
                    Label(state.isPaused ? "Resume" : "Pause",
                          systemImage: state.isPaused ? "play.fill" : "pause.fill")
                        .labelStyle(.iconOnly).font(.caption)
                }
                .buttonStyle(.bordered).tint(ChronosColors.brandPrimary)

                Button(intent: FocusExtendIntent(minutes: 5)) {
                    Label("+5m", systemImage: "goforward.plus").font(.caption2)
                }
                .buttonStyle(.bordered)

                Button(intent: FocusStopIntent()) {
                    Label("Stop", systemImage: "stop.fill").labelStyle(.iconOnly).font(.caption)
                }
                .buttonStyle(.bordered).tint(.red)
            }
        }
    }

    private func leadingIcon(_ state: FocusActivityAttributes.ContentState) -> String {
        if state.awaitingAdvance { return "hand.tap.fill" }
        return state.isPaused ? "pause.circle.fill" : "timer"
    }

    private func subtitle(_ state: FocusActivityAttributes.ContentState) -> String {
        let phasePart = "Phase \(state.phaseNumber) of \(state.totalPhases)"
        if state.awaitingAdvance { return "\(state.phase.title) · Tap to continue" }
        return "\(state.phase.title) · \(phasePart)"
    }

    @ViewBuilder private func trailing(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.awaitingAdvance {
            Text("Tap to continue").font(.caption).foregroundStyle(.secondary)
        } else {
            Text(timerInterval: Date.now...state.phaseEndsAt, countsDown: true)
                .font(.system(.title2, design: .rounded).monospacedDigit())
                .frame(width: 70)
        }
    }
}
