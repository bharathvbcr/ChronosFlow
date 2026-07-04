import ActivityKit
import WidgetKit
import SwiftUI

/// Lock Screen + Dynamic Island for the schedule Live Activity — the single "now / up-next / reminder"
/// surface. It shows the active or up-next block with a live countdown, and folds the most-imminent
/// medication / task / habit reminder in as an action chip (or, when no block is running, becomes a
/// reminder card in its own right). Shares its visual language with the focus LA via LiveActivityChrome.
struct BlockLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BlockActivityAttributes.self) { context in
            lockScreen(context)
                .padding(ChronosSpacing.standard)
                .activityBackgroundTint(LiveActivityChrome.cardBackground)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(lockScreenAccessibilityLabel(context.state))
        } dynamicIsland: { context in
            let state = context.state
            let accent = accentColor(state)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(state.headerLabel, systemImage: icon(for: state))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(accent)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if state.mode != .reminder {
                        countdown(state.endsAt)
                            .font(.subheadline.weight(.semibold).monospacedDigit())
                            .foregroundStyle(accent)
                            .multilineTextAlignment(.trailing)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(state.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text(state.subtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                        if state.mode != .reminder {
                            LAProgressBar(progress: state.progress, color: accent, height: 5)
                        }
                        if state.mode == .reminder, let reminder = state.primaryReminder {
                            ReminderActionButton(chip: reminder, compact: true)
                        }
                        FoldedRemindersView(
                            reminders: state.reminders,
                            dropFirst: state.mode == .reminder ? 1 : 0,
                            maxVisible: 1,
                            compact: true)
                    }
                    .padding(.top, 2)
                }
            } compactLeading: {
                Image(systemName: icon(for: state))
                    .foregroundStyle(accent)
            } compactTrailing: {
                if state.mode == .reminder, let reminder = state.primaryReminder {
                    Text(reminder.kind.actionTitle)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(accent)
                } else {
                    countdown(state.endsAt)
                        .font(.caption2.monospacedDigit())
                        .frame(width: 44)
                }
            } minimal: {
                Image(systemName: minimalIcon(state))
                    .foregroundStyle(accent)
                    .accessibilityLabel(minimalAccessibilityLabel(state))
            }
        }
    }

    private func lockScreenAccessibilityLabel(_ state: BlockActivityAttributes.ContentState) -> String {
        switch state.mode {
        case .reminder:
            if let reminder = state.primaryReminder {
                return "\(reminder.title). \(reminder.detail)"
            }
            return state.title
        case .active:
            return "\(state.headerLabel). \(state.title). \(state.subtitle)"
        case .upNext:
            return "Up next. \(state.title). \(state.subtitle)"
        }
    }

    private func minimalAccessibilityLabel(_ state: BlockActivityAttributes.ContentState) -> String {
        switch state.mode {
        case .reminder: state.primaryReminder?.title ?? "Reminder"
        case .active: "Current block"
        case .upNext: "Up next block"
        }
    }

    // MARK: - Lock screen

    @ViewBuilder
    private func lockScreen(_ context: ActivityViewContext<BlockActivityAttributes>) -> some View {
        let state = context.state
        let accent = accentColor(state)
        VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
            HStack(alignment: .top, spacing: ChronosSpacing.compact) {
                LAAccentBand(color: accent)

                VStack(alignment: .leading, spacing: 3) {
                    LAHeaderLabel(text: state.headerLabel, color: accent)
                    Text(state.title)
                        .font(.system(.headline, design: .rounded))
                        .lineLimit(1)
                    Text(state.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                Spacer(minLength: ChronosSpacing.small)

                if state.mode == .reminder {
                    Image(systemName: state.primaryReminder?.kind.symbol ?? "bell.fill")
                        .font(.title)
                        .foregroundStyle(accent)
                        .symbolRenderingMode(.hierarchical)
                } else {
                    countdown(state.endsAt)
                        .font(.system(size: 26, weight: .bold, design: .rounded).monospacedDigit())
                        .foregroundStyle(accent)
                        .multilineTextAlignment(.trailing)
                }
            }

            if state.mode != .reminder {
                LAProgressBar(progress: state.progress, color: accent)
            }

            if state.mode == .active, supportsFocus(state.category), !state.blockID.isEmpty {
                startFocusButton(blockID: state.blockID, accent: accent)
            }

            if state.mode == .reminder, let reminder = state.primaryReminder {
                ReminderActionButton(chip: reminder)
            }

            FoldedRemindersView(
                reminders: state.reminders,
                dropFirst: state.mode == .reminder ? 1 : 0,
                maxVisible: state.mode == .active && supportsFocus(state.category) ? 1 : 2)
        }
    }

    @ViewBuilder
    private func startFocusButton(blockID: String, accent: Color) -> some View {
        Button(intent: StartFocusIntent(blockID: blockID)) {
            Label("Start focus", systemImage: "timer")
                .font(.caption.weight(.semibold))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(accent)
        .controlSize(.small)
    }

    /// Mirrors Android `BlockCategories.supportsFocus` — rest blocks skip the Start focus affordance.
    private func supportsFocus(_ category: String) -> Bool {
        switch category.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "break", "sleep", "meal", "food": return false
        default: return true
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func countdown(_ endsAt: Date) -> some View {
        if endsAt > .now {
            Text(timerInterval: Date.now...endsAt, countsDown: true)
        } else {
            Text("0:00")
        }
    }

    /// The card accent: the reminder's tint in reminder mode (coral when overdue), else the block
    /// category color.
    private func accentColor(_ state: BlockActivityAttributes.ContentState) -> Color {
        if state.mode == .reminder, let reminder = state.primaryReminder {
            return reminder.isOverdue ? LiveActivityChrome.urgent : ChronosColors.category(reminder.kind.categoryKey)
        }
        return LiveActivityChrome.accent(state.category)
    }

    private func icon(for state: BlockActivityAttributes.ContentState) -> String {
        switch state.mode {
        case .active: "clock.fill"
        case .upNext: "arrow.right.circle.fill"
        case .reminder: state.primaryReminder?.kind.symbol ?? "bell.fill"
        }
    }

    /// The Dynamic Island minimal glyph reuses the same mode→symbol mapping as the expanded/lock-screen
    /// icon so the surface reads consistently as it collapses (clock / arrow / reminder symbol).
    private func minimalIcon(_ state: BlockActivityAttributes.ContentState) -> String {
        icon(for: state)
    }
}
