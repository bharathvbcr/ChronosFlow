import ActivityKit
import WidgetKit
import SwiftUI
import ChronosCore

struct FocusLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            // Lock screen / banner
            VStack(spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.state.phase.title)
                            .font(.headline.bold()).foregroundStyle(.primary)
                        if let end = context.state.blockEndsAt {
                            Text("until \(end, style: .time)")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(timerInterval: Date()...context.state.phaseEndsAt,
                             countsDown: true)
                            .font(.system(size: 28, weight: .bold, design: .rounded).monospacedDigit())
                            .multilineTextAlignment(.trailing)
                        Text("Phase \(context.state.phaseNumber)/\(context.state.totalPhases)")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                // Segmented Pomodoro progress bar — one segment per work/break phase, sized by its
                // fractional width and colored by phase kind / live state (Android's segmented
                // foreground-notification bar). Empty for a flat session, which shows nothing here.
                FocusSegmentedBar(segments: context.state.phaseSegments)
            }
            .padding()
            .activityBackgroundTint(Color.black.opacity(0.7))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.blockTitle, systemImage: "timer")
                        .font(.caption).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: Date()...context.state.phaseEndsAt,
                         countsDown: true)
                        .font(.caption.monospacedDigit()).multilineTextAlignment(.trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 6) {
                        HStack {
                            Text(context.state.phase.title).font(.caption2)
                            Spacer()
                            Text("\(context.state.phaseNumber)/\(context.state.totalPhases) phases")
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                        FocusSegmentedBar(segments: context.state.phaseSegments)
                    }
                }
            } compactLeading: {
                Image(systemName: "timer").foregroundStyle(.orange)
            } compactTrailing: {
                Text(timerInterval: Date()...context.state.phaseEndsAt,
                     countsDown: true)
                    .font(.caption.monospacedDigit())
                    .frame(width: 40)
            } minimal: {
                Image(systemName: "timer")
            }
        }
    }
}

/// Thin multi-segment progress bar for a split (Pomodoro) focus session — one capsule per work/break
/// phase, laid out in proportion to each phase's `fractionalWidth` and tinted by phase kind / live
/// state. The iOS render of the Android segmented foreground-notification bar (built in
/// `ChronosCore.focusBarSegments`). Renders nothing for a flat (single-phase) session.
private struct FocusSegmentedBar: View {
    let segments: [FocusActivityAttributes.ContentState.PhaseSegmentInfo]

    var body: some View {
        if segments.count > 1 {
            GeometryReader { geo in
                HStack(spacing: 2) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        Capsule()
                            .fill(color(for: segment))
                            // Subtract the cumulative inter-segment spacing so widths still sum to the
                            // available width (n segments → n-1 gaps of 2pt).
                            .frame(width: max(2, segment.fractionalWidth * availableWidth(geo.size.width)))
                    }
                }
                .frame(maxHeight: .infinity, alignment: .center)
            }
            .frame(height: 5)
            .accessibilityHidden(true)
        }
    }

    private func availableWidth(_ total: CGFloat) -> CGFloat {
        max(0, total - CGFloat(max(segments.count - 1, 0)) * 2)
    }

    /// Maps a segment's portable color kind (break / current-with-state / steady work) to the
    /// brand palette, mirroring Android `focusSegmentColorRes` precedence.
    private func color(for segment: FocusActivityAttributes.ContentState.PhaseSegmentInfo) -> Color {
        if segment.isBreak { return ChronosColors.brandSecondary }       // teal break
        guard segment.isCurrent, let state = segment.currentState else {
            return ChronosColors.brandPrimary.opacity(0.55)              // non-current work (steady)
        }
        switch state {
        case .running:    return ChronosColors.brandPrimary             // current work, ticking
        case .endingSoon: return ChronosColors.brandAccent              // current work, < 1 min left
        case .paused:     return ChronosColors.brandPrimary.opacity(0.4) // current work, paused
        }
    }
}
