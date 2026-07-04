import ActivityKit
import AppIntents
import SwiftData
import WidgetKit
import Foundation

// Interactive AppIntents invoked from widget buttons and Live Activity controls — the iOS-native
// equivalent of the Android Glance widget actions (TaskComplete / HabitMark / DoseAck / FocusStart)
// and the focus notification action buttons.
//
// These are compiled into BOTH the app target and the widget extension (see project.yml note) so
// the same intent type backs the in-app shortcut surface and the widget/Live-Activity buttons.
// Each mutates the shared App-Group SwiftData store via `ChronosStore.shared` and then reloads
// widget timelines so the UI reflects the change immediately.

// MARK: - Data-mutating widget intents

/// Complete a task straight from the Tasks widget. Mirrors Android `TaskComplete`.
struct CompleteTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "Complete Task"
    static let isDiscoverable = false   // button-only; not surfaced as a standalone shortcut

    @Parameter(title: "Task ID") var taskID: String

    init() {}
    init(taskID: String) { self.taskID = taskID }

    @MainActor
    func perform() async throws -> some IntentResult {
        let context = ChronosStore.shared.mainContext
        let id = taskID
        if let task = try? context.fetch(
            FetchDescriptor<TaskItem>(predicate: #Predicate { $0.id == id })).first {
            task.isCompleted = true
            task.updatedAt = .now
            try? context.save()
        }
        WidgetCenter.shared.reloadAllTimelines()
        BlockLiveActivityChipRefresher.removeEntity(kind: .task, entityID: taskID)
        LiveActivityRefreshBridge.post()
        return .result()
    }
}

/// Toggle a habit's completion for today from the Habits widget. Mirrors Android `HabitMark`.
struct ToggleHabitIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle Habit"
    static let isDiscoverable = false

    @Parameter(title: "Habit ID") var habitID: String

    init() {}
    init(habitID: String) { self.habitID = habitID }

    @MainActor
    func perform() async throws -> some IntentResult {
        let context = ChronosStore.shared.mainContext
        let id = habitID
        if let habit = try? context.fetch(
            FetchDescriptor<Habit>(predicate: #Predicate { $0.id == id })).first {
            habit.toggleCompletion(on: .now)
            try? context.save()
        }
        WidgetCenter.shared.reloadAllTimelines()
        BlockLiveActivityChipRefresher.removeEntity(kind: .habit, entityID: habitID)
        LiveActivityRefreshBridge.post()
        return .result()
    }
}

/// Acknowledge a medication dose from the Medication widget. Mirrors Android `DoseAck`.
struct MarkDoseTakenIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark Dose Taken"
    static let isDiscoverable = false

    @Parameter(title: "Plan ID") var planID: String

    init() {}
    init(planID: String) { self.planID = planID }

    @MainActor
    func perform() async throws -> some IntentResult {
        let context = ChronosStore.shared.mainContext
        let id = planID
        if let plan = try? context.fetch(
            FetchDescriptor<MedicationPlan>(predicate: #Predicate { $0.id == id })).first,
           !plan.isTaken(on: .now) {
            plan.acknowledgeDose()
            try? context.save()
        }
        WidgetCenter.shared.reloadAllTimelines()
        BlockLiveActivityChipRefresher.removeEntity(kind: .medication, entityID: planID)
        LiveActivityRefreshBridge.post()
        return .result()
    }
}

/// Start a focus session for a block from the Today widget. Mirrors Android `FocusStart`.
/// The in-app FocusTimerModel can't run from the widget process, so this records the request as a
/// pending command (see `FocusCommandBridge`) and opens the app, which picks it up and starts the timer.
struct StartFocusIntent: AppIntent {
    static let title: LocalizedStringResource = "Start Focus"
    static let isDiscoverable = false
    static let openAppWhenRun = true

    @Parameter(title: "Block ID") var blockID: String

    init() {}
    init(blockID: String) { self.blockID = blockID }

    @MainActor
    func perform() async throws -> some IntentResult {
        FocusCommandBridge.post(.start(blockID: blockID))
        return .result()
    }
}

// MARK: - Live Activity control intents (Pause/Resume, Stop, Extend)
//
// A Live Activity button runs in the widget extension process and cannot touch the in-process
// FocusTimerModel directly. Each control writes a pending command to App-Group UserDefaults and
// posts a Darwin notification; the running app observes it (see FocusCommandBridge usage note) and
// applies it to the live FocusTimerModel. These are the iOS analogue of the Android focus
// notification action buttons.

struct FocusPauseIntent: AppIntent {
    static let title: LocalizedStringResource = "Pause Focus"
    static let isDiscoverable = false

    @MainActor
    func perform() async throws -> some IntentResult {
        FocusCommandBridge.post(.togglePause)
        return .result()
    }
}

struct FocusStopIntent: AppIntent {
    static let title: LocalizedStringResource = "Stop Focus"
    static let isDiscoverable = false

    @MainActor
    func perform() async throws -> some IntentResult {
        FocusCommandBridge.post(.stop)
        return .result()
    }
}

struct FocusExtendIntent: AppIntent {
    static let title: LocalizedStringResource = "Extend Focus"
    static let isDiscoverable = false

    @Parameter(title: "Minutes", default: 5) var minutes: Int

    init() {}
    init(minutes: Int) { self.minutes = minutes }

    @MainActor
    func perform() async throws -> some IntentResult {
        FocusCommandBridge.post(.extend(minutes: max(minutes, 1)))
        return .result()
    }
}

/// Advance past a phase boundary (the "tap to continue" hold) from the Live Activity.
struct FocusAdvanceIntent: AppIntent {
    static let title: LocalizedStringResource = "Continue Focus"
    static let isDiscoverable = false

    @MainActor
    func perform() async throws -> some IntentResult {
        FocusCommandBridge.post(.advance)
        return .result()
    }
}

// MARK: - Live Activity refresh bridge (widget/Live-Activity chip → app)
//
// Chip actions run in the widget extension and cannot call `BlockLiveActivityCoordinator` directly.
// Update the live activity in-process when possible, then post Darwin so the app also refreshes.
enum BlockLiveActivityChipRefresher {
    @MainActor
    static func removeEntity(kind: BlockActivityAttributes.ReminderChip.Kind, entityID: String) {
        for activity in Activity<BlockActivityAttributes>.activities {
            var state = activity.content.state
            let before = state.reminders.count
            state.reminders.removeAll { $0.kind == kind && $0.entityID == entityID }
            guard state.reminders.count != before else { continue }

            if state.mode == .reminder {
                if let primary = state.reminders.first {
                    state.title = primary.title
                    state.subtitle = primary.detail
                    state.category = primary.kind.categoryKey
                } else {
                    Task { await activity.end(nil, dismissalPolicy: .immediate) }
                    continue
                }
            }

            let content = ActivityContent(state: state, staleDate: activity.content.staleDate)
            Task { await activity.update(content) }
        }
    }
}

enum LiveActivityRefreshBridge {
    static let darwinName = "com.chronosflow.blocklive.refresh" as CFString

    static func post() {
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(darwinName), nil, nil, true)
    }
}

// MARK: - Focus command bridge (widget/Live-Activity → app)
//
// Cross-process hand-off for focus controls. The widget extension writes a command here; the app
// drains it via `ChronosFocusCommandRouter` (Darwin observer + launch/foreground drain).
enum FocusCommandBridge {
    enum Command: Codable, Equatable {
        case start(blockID: String)
        case togglePause
        case stop
        case extend(minutes: Int)
        case advance
    }

    static let darwinName = "com.chronosflow.focus.command" as CFString
    private static let storageKey = "focus.pendingCommands"
    private static var defaults: UserDefaults {
        UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
    }

    /// Append a command for the app to apply. Commands queue (a user may tap several controls before
    /// the app foregrounds) and are drained in order.
    static func post(_ command: Command) {
        var queue = pending()
        queue.append(command)
        if let data = try? JSONEncoder().encode(queue) {
            defaults.set(data, forKey: storageKey)
        }
        // Nudge the app if it is already running.
        CFNotificationCenterPostNotification(
            CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(darwinName), nil, nil, true)
    }

    /// Read the queued commands without clearing them.
    static func pending() -> [Command] {
        guard let data = defaults.data(forKey: storageKey),
              let queue = try? JSONDecoder().decode([Command].self, from: data) else { return [] }
        return queue
    }

    /// Apply and clear every queued command in order. Call this from the app.
    static func drain(_ apply: (Command) -> Void) {
        let queue = pending()
        guard !queue.isEmpty else { return }
        defaults.removeObject(forKey: storageKey)
        queue.forEach(apply)
    }
}
