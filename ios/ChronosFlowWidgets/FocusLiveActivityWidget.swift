import WidgetKit
import SwiftUI
import ActivityKit

/// Lock Screen + Dynamic Island presentation for a running focus session. This is the iOS-native
/// "Live Update" — the equivalent of the Android focus foreground notification / Live Updates.
///
/// Mirrors the block-bounded phase model: shows phase N of M, a live countdown while running, and a
/// "Tap to continue" prompt while holding at a phase boundary (`awaitingAdvance`).
///
/// CANONICAL Live Activity widget: this widget-extension implementation is the one that ships. It
/// renders the multi-phase (Pomodoro) progress bar from `ContentState.phaseSegments` (built in the
/// app via `ChronosCore.focusBarSegments`) AND carries the interactive Pause/Stop/Extend/Continue
/// controls. The in-app `FocusLiveActivityView.swift` is the view-only companion (it has no buttons,
/// since a Live Activity's buttons must run in the extension process). `FocusActivityAttributes` and
/// `FocusCommandBridge` are defined in the main app target and shared via the App Group, matching the
/// Android FocusService Live Updates architecture.
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
                // Segmented (Pomodoro) bar — one capsule per work/break phase, sized + colored from
                // the render-ready segments the app built via `focusBarSegments`. Empty for a flat
                // session, which renders nothing here.
                segmentedBar(context.state.phaseSegments)
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
                        segmentedBar(context.state.phaseSegments)
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

    /// Thin multi-segment Pomodoro bar from the render-ready `phaseSegments`. One capsule per
    /// work/break phase, laid out by `fractionalWidth` and tinted by phase kind / live state — the
    /// iOS render of the Android segmented foreground-notification bar (logic in
    /// `ChronosCore.focusBarSegments`). Renders nothing for a flat (single-phase) session.
    @ViewBuilder private func segmentedBar(
        _ segments: [FocusActivityAttributes.ContentState.PhaseSegmentInfo]
    ) -> some View {
        if segments.count > 1 {
            GeometryReader { geo in
                HStack(spacing: 2) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        Capsule()
                            .fill(segmentColor(segment))
                            // Subtract the cumulative 2pt gaps so the widths still sum to the bar.
                            .frame(width: max(2, segment.fractionalWidth * availableBarWidth(geo.size.width, count: segments.count)))
                    }
                }
                .frame(maxHeight: .infinity, alignment: .center)
            }
            .frame(height: 5)
            .accessibilityHidden(true)
        }
    }

    private func availableBarWidth(_ total: CGFloat, count: Int) -> CGFloat {
        max(0, total - CGFloat(max(count - 1, 0)) * 2)
    }

    /// Maps a segment's kind / live state to the brand palette, mirroring Android
    /// `focusSegmentColorRes` precedence (break → current-state → steady work).
    private func segmentColor(_ segment: FocusActivityAttributes.ContentState.PhaseSegmentInfo) -> Color {
        if segment.isBreak { return ChronosColors.brandSecondary }          // teal break
        guard segment.isCurrent, let state = segment.currentState else {
            return ChronosColors.brandPrimary.opacity(0.55)                 // non-current work (steady)
        }
        switch state {
        case .running:    return ChronosColors.brandPrimary                 // current work, ticking
        case .endingSoon: return ChronosColors.brandAccent                  // current work, < 1 min left
        case .paused:     return ChronosColors.brandPrimary.opacity(0.4)    // current work, paused
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
