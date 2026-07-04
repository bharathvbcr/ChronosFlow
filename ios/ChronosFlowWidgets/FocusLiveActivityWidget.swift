import WidgetKit
import SwiftUI
import ActivityKit

/// Lock Screen + Dynamic Island presentation for a running focus session. This is the iOS-native
/// "Live Update" — the equivalent of the Android focus foreground notification / Live Updates.
///
/// CANONICAL Live Activity widget: ships in the widget extension with interactive controls. Shares its
/// visual language (accent band, header eyebrow, capsule progress rail, dark card) with the schedule
/// Live Activity via LiveActivityChrome so the two surfaces read as one system.
struct FocusLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            lockScreen(context)
                .padding(ChronosSpacing.standard)
                .activityBackgroundTint(LiveActivityChrome.cardBackground)
        } dynamicIsland: { context in
            let accent = barColor(for: context.state)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.state.phase.title, systemImage: leadingIcon(context.state))
                        .font(.caption)
                        .foregroundStyle(accent)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    trailing(context.state)
                        .font(.system(.body, design: .rounded).monospacedDigit())
                        .foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: ChronosSpacing.small) {
                        Text(context.attributes.blockTitle)
                            .font(.headline)
                            .lineLimit(1)
                        Text(subtitle(context.state))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        progressSurface(context.state)
                        controls(context.state)
                    }
                }
            } compactLeading: {
                Image(systemName: leadingIcon(context.state))
                    .foregroundStyle(context.state.isPaused ? .secondary : accent)
            } compactTrailing: {
                compactTrailing(context.state)
            } minimal: {
                Image(systemName: leadingIcon(context.state))
                    .foregroundStyle(accent)
            }
        }
    }

    // MARK: - Lock screen

    @ViewBuilder
    private func lockScreen(_ context: ActivityViewContext<FocusActivityAttributes>) -> some View {
        let accent = barColor(for: context.state)
        VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
            HStack(alignment: .top, spacing: ChronosSpacing.compact) {
                LAAccentBand(color: accent, height: 48)
                VStack(alignment: .leading, spacing: 3) {
                    LAHeaderLabel(text: "Focus", color: accent)
                    Text(context.attributes.blockTitle)
                        .font(.system(.headline, design: .rounded))
                        .lineLimit(1)
                    Text(subtitle(context.state))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                    if let window = blockWindow(context.state) {
                        Text(window)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
                Spacer(minLength: ChronosSpacing.small)
                trailing(context.state)
                    .font(.system(size: 28, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(accent)
                    .multilineTextAlignment(.trailing)
            }
            progressSurface(context.state)
            controls(context.state)
        }
    }

    // MARK: - Progress

    @ViewBuilder
    private func progressSurface(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.phaseSegments.count > 1 {
            segmentedBar(state.phaseSegments)
        } else {
            LAProgressBar(progress: state.completionPercent, color: barColor(for: state), height: 5)
        }
    }

    private func barColor(for state: FocusActivityAttributes.ContentState) -> Color {
        if state.isPaused { return ChronosColors.brandPrimary.opacity(0.4) }
        if state.awaitingAdvance { return ChronosColors.brandAccent }
        let remaining = max(0, state.phaseEndsAt.timeIntervalSinceNow)
        if remaining > 0 && remaining <= 60 { return ChronosColors.brandAccent }
        return ChronosColors.brandPrimary
    }

    private func blockWindow(_ state: FocusActivityAttributes.ContentState) -> String? {
        guard let start = state.blockStartsAt, let end = state.blockEndsAt else { return nil }
        let fmt = Date.FormatStyle(date: .omitted, time: .shortened)
        return "\(start.formatted(fmt)) – \(end.formatted(fmt))"
    }

    // MARK: - Controls

    @ViewBuilder
    private func controls(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.awaitingAdvance {
            Button(intent: FocusAdvanceIntent()) {
                Label("Continue", systemImage: "play.fill")
                    .font(.caption.weight(.semibold))
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .tint(ChronosColors.brandPrimary)
        } else {
            HStack(spacing: 10) {
                Button(intent: FocusPauseIntent()) {
                    Label(state.isPaused ? "Resume" : "Pause",
                          systemImage: state.isPaused ? "play.fill" : "pause.fill")
                        .labelStyle(.iconOnly)
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .tint(ChronosColors.brandPrimary)

                Button(intent: FocusExtendIntent(minutes: 5)) {
                    Label("+5m", systemImage: "goforward.plus")
                        .font(.caption2)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)

                Button(intent: FocusStopIntent()) {
                    Label("Stop", systemImage: "stop.fill")
                        .labelStyle(.iconOnly)
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
                .tint(.red)
            }
        }
    }

    // MARK: - Segmented bar

    @ViewBuilder
    private func segmentedBar(
        _ segments: [FocusActivityAttributes.ContentState.PhaseSegmentInfo]
    ) -> some View {
        GeometryReader { geo in
            HStack(spacing: 2) {
                ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                    Capsule()
                        .fill(segmentColor(segment))
                        .frame(width: max(2, segment.fractionalWidth * availableBarWidth(geo.size.width, count: segments.count)))
                }
            }
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .frame(height: 5)
        .accessibilityHidden(true)
    }

    private func availableBarWidth(_ total: CGFloat, count: Int) -> CGFloat {
        max(0, total - CGFloat(max(count - 1, 0)) * 2)
    }

    private func segmentColor(_ segment: FocusActivityAttributes.ContentState.PhaseSegmentInfo) -> Color {
        if segment.isBreak { return ChronosColors.brandSecondary }
        guard segment.isCurrent, let state = segment.currentState else {
            return ChronosColors.brandPrimary.opacity(0.55)
        }
        switch state {
        case .running: return ChronosColors.brandPrimary
        case .endingSoon: return ChronosColors.brandAccent
        case .paused: return ChronosColors.brandPrimary.opacity(0.4)
        }
    }

    // MARK: - Labels

    private func leadingIcon(_ state: FocusActivityAttributes.ContentState) -> String {
        if state.awaitingAdvance { return "hand.tap.fill" }
        return state.isPaused ? "pause.circle.fill" : "timer"
    }

    private func subtitle(_ state: FocusActivityAttributes.ContentState) -> String {
        if state.awaitingAdvance { return "\(state.phase.title) · Tap to continue" }
        if state.isPaused { return "Paused · tap play to resume" }
        if state.totalPhases > 1 {
            return "\(state.phase.title) · Phase \(state.phaseNumber) of \(state.totalPhases)"
        }
        return state.phase.title
    }

    @ViewBuilder
    private func trailing(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.awaitingAdvance {
            Label("Continue", systemImage: "forward.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
        } else if state.isPaused {
            Label("Paused", systemImage: "pause.fill")
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
        } else {
            Text(timerInterval: Date.now...state.phaseEndsAt, countsDown: true)
                .frame(width: 72)
        }
    }

    @ViewBuilder
    private func compactTrailing(_ state: FocusActivityAttributes.ContentState) -> some View {
        if state.awaitingAdvance {
            Image(systemName: "hand.tap.fill").font(.caption2)
        } else if state.isPaused {
            Image(systemName: "pause.fill")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(width: 40)
        } else {
            Text(timerInterval: Date.now...state.phaseEndsAt, countsDown: true)
                .font(.caption2.monospacedDigit())
                .frame(width: 40)
        }
    }
}
