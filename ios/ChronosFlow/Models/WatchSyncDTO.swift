import Foundation

// MARK: - Phone ↔ Watch sync DTOs
//
// The cross-device "Data Layer" for the watchOS companion — the iOS-native analogue of the
// Android Wear OS Data Layer (MessageClient/DataClient). Apple Watch and iPhone do NOT share an
// App-Group container (App Groups are device-local), so the watch cannot open the phone's
// SwiftData store. Instead the phone serialises a small snapshot of *today* and ships it over
// WatchConnectivity (WCSession); the watch ships back quick-action commands.
//
// This file lives under `ChronosFlow/Models/` so it is compiled into BOTH the phone app target and
// the `ChronosWatch` target (see project.yml `sources`). It is Foundation-only + `Codable` so it
// can cross the wire and persist into the watch's local UserDefaults for cold launch.

/// A self-contained snapshot of the phone's "today", small enough to send via
/// `WCSession.updateApplicationContext` (latest-state delivery) on every change.
struct WatchSnapshot: Codable, Equatable, Sendable {
    /// Start-of-day the snapshot describes (the watch shows a banner if this is stale / not today).
    var date: Date
    var blocks: [WatchBlock]
    var tasks: [WatchTask]
    var habits: [WatchHabit]
    /// Present only while a focus session is running on the phone.
    var focus: WatchFocusState?

    init(date: Date = Calendar.current.startOfDay(for: .now),
         blocks: [WatchBlock] = [],
         tasks: [WatchTask] = [],
         habits: [WatchHabit] = [],
         focus: WatchFocusState? = nil) {
        self.date = date
        self.blocks = blocks
        self.tasks = tasks
        self.habits = habits
        self.focus = focus
    }
}

/// One planned span of the day. `startMinute`/`durationMinutes` mirror the phone's minute-of-day
/// model so the watch can compute "now / next" without the full `TimeBlock` @Model.
struct WatchBlock: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var title: String
    var startMinute: Int
    var durationMinutes: Int
    var category: String
    var isCompleted: Bool

    /// End minute-of-day, wrapping past midnight (mirrors `TimeBlock.plannedEndMinuteOfDay`).
    var endMinute: Int { (startMinute + durationMinutes) % 1440 }
}

struct WatchTask: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var title: String
    /// 0 = none, 1 = low, 2 = medium, 3 = high (mirrors `TaskItem.priority`).
    var priority: Int
    var isCompleted: Bool
}

struct WatchHabit: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var title: String
    var doneToday: Bool
}

/// Live focus state mirrored to the watch so it can render a countdown (`Text(timerInterval:)`)
/// and the correct controls. Mirrors `FocusActivityAttributes.ContentState`.
struct WatchFocusState: Codable, Equatable, Sendable {
    var blockTitle: String
    /// When the current phase ends; the watch renders a live countdown from this.
    var phaseEndsAt: Date
    var isPaused: Bool
    /// True while holding at a phase boundary, waiting for the user to tap to continue.
    var awaitingAdvance: Bool
    /// 1-based index of the current phase.
    var phaseNumber: Int
    /// Total phases in the planned (block-bounded) sequence.
    var totalPhases: Int
}

/// Quick actions the watch sends back to the phone (watch → phone). The phone applies each to the
/// shared SwiftData store (task/habit/dose) or forwards focus controls to the live FocusTimerModel
/// via `FocusCommandBridge`. Mirrors the Android quick-action messages.
enum WatchCommand: Codable, Equatable, Sendable {
    case completeTask(id: String)
    case toggleHabit(id: String)
    case markDose(id: String)
    case startFocus(blockID: String)
    case togglePauseFocus
    case stopFocus
}

// MARK: - WatchConnectivity payload keys

/// Shared dictionary keys for the `[String: Any]` payloads WCSession transports. Kept here so the
/// phone and watch encode/decode against the same constants.
enum WatchSyncPayload {
    /// Key under which a JSON-encoded `WatchSnapshot` travels (phone → watch).
    static let snapshot = "snapshot"
    /// Key under which a JSON-encoded `WatchCommand` travels (watch → phone).
    static let command = "command"

    /// On-watch App Group shared between the watch app and its WidgetKit extension (same device —
    /// this IS a valid shared container, unlike the phone↔watch link). Lets the complication read
    /// the latest snapshot the watch app cached.
    static let watchAppGroup = "group.com.chronosflow.shared"
    /// Key the watch app caches the latest `WatchSnapshot` under (cold launch + widget timeline).
    static let cacheKey = "watch.cachedSnapshot"
}
