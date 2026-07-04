import ActivityKit
import Foundation

/// Live Activity payload for the always-on "current block" surface — the iOS analogue of Android's
/// `CurrentBlockNotificationCoordinator` live update. Shows the active block's countdown and progress,
/// or an up-next countdown during a gap. Yields to the focus Live Activity while a session runs.
struct BlockActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        enum Mode: String, Codable, Hashable {
            case active
            case upNext
            /// No block is running, but a reminder is pending — the chip is the primary content.
            /// This lets the single live surface stand in for what used to be separate med/task/habit
            /// banners (the "aggressive fold" — reminders live in the Live Activity, not the tray).
            case reminder
        }

        var mode: Mode
        var title: String
        var subtitle: String
        /// Header under the app name, e.g. "Now · 3 to go" or "Up next".
        var headerLabel: String
        /// Active block id for Start-focus deep link; empty in up-next / reminder modes.
        var blockID: String = ""
        /// 0…1 progress through the active block or up-next gap.
        var progress: Double
        var endsAt: Date
        var category: String
        var remainingBlockCount: Int
        /// The actionable reminders folded into this surface (doses due, tasks due, habit windows
        /// open), ranked most-urgent first and capped by the coordinator. Rendered as inline action
        /// chips alongside `.active`/`.upNext`, or — in `.reminder` mode — the first is the card
        /// headline and the rest stack beneath it. Empty when nothing is pending. Defaulted so the
        /// active/up-next state builders can omit it and set it afterward.
        var reminders: [ReminderChip] = []

        /// The highest-priority folded reminder (medication → task → habit, then most-overdue), or
        /// nil when none are pending. Drives the card accent and the `.reminder`-mode headline.
        var primaryReminder: ReminderChip? { reminders.first }
    }

    /// One folded reminder surfaced inside the schedule Live Activity, with the entity id the chip's
    /// action button needs (Take / Complete / Mark done reuse the existing widget intents).
    struct ReminderChip: Codable, Hashable {
        enum Kind: String, Codable, Hashable {
            case medication, task, habit

            /// SF Symbol for the chip glyph.
            var symbol: String {
                switch self {
                case .medication: "pills.fill"
                case .task: "checklist"
                case .habit: "flame.fill"
                }
            }

            /// The block-category key whose accent color represents this reminder kind (reuses the
            /// app-wide `ChronosColors.category` mapping so the chip matches the rest of the app).
            var categoryKey: String {
                switch self {
                case .medication: "MEDICATION"
                case .task: "FOCUS"
                case .habit: "HABIT"
                }
            }

            /// Label on the chip's action button.
            var actionTitle: String {
                switch self {
                case .medication: "Take"
                case .task: "Done"
                case .habit: "Log"
                }
            }
        }

        var kind: Kind
        /// planID / taskID / habitID — the parameter the action intent resolves against.
        var entityID: String
        var title: String
        /// Secondary line, e.g. "8:00 AM · 500 mg", "Due now", or "Window open · 5-day streak".
        var detail: String
        /// True when the reminder's time has passed — drives the urgent (coral) accent + interruption.
        var isOverdue: Bool
    }

    var dayLabel: String
}
