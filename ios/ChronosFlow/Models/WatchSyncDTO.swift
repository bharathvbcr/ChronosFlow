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
    /// Medication doses scheduled for today. Empty when none — or when titles are redacted (the
    /// privacy gate drops the array but keeps `medsDueCount` so the watch still shows urgency).
    /// Mirrors Android's `WearDaySummary.meds` + `KEY_MED_ENTRIES`.
    var medications: [WatchMed]
    /// Count of doses still due today (untaken). Always sent — even when `medications` is redacted —
    /// so the today page / complication can surface "N due" without leaking titles. Mirrors
    /// Android's `WearDaySummary.medsDueCount` / `MEDS_DUE_COUNT`.
    var medsDueCount: Int
    /// One-line AI day digest pre-generated on the phone (cached; never triggers watch inference).
    /// `nil` when redacted or not yet generated. Mirrors Android's `WearDaySummary.digest`/`KEY_DIGEST`.
    var digest: String?
    /// Present only while a focus session is running on the phone.
    var focus: WatchFocusState?
    /// Ranked folded reminder chips from the phone live surface; empty when folding is off.
    var foldedReminders: [WatchFoldedReminder]
    /// Wall-clock epoch millis when the phone built this snapshot (0 = never synced on this device).
    /// Lets the watch flag a schedule that may be out of date when the phone has been out of reach —
    /// the "now"/"until"/dial claims are time-relative and silently rot otherwise. Feeds
    /// `WearFormat.syncAgeLabel`. Mirrors Android's `WearDaySummary.receivedAtMillis`.
    var receivedAtMillis: Int64

    init(date: Date = Calendar.current.startOfDay(for: .now),
         blocks: [WatchBlock] = [],
         tasks: [WatchTask] = [],
         habits: [WatchHabit] = [],
         medications: [WatchMed] = [],
         medsDueCount: Int = 0,
         digest: String? = nil,
         focus: WatchFocusState? = nil,
         foldedReminders: [WatchFoldedReminder] = [],
         receivedAtMillis: Int64 = 0) {
        self.date = date
        self.blocks = blocks
        self.tasks = tasks
        self.habits = habits
        self.medications = medications
        self.medsDueCount = medsDueCount
        self.digest = digest
        self.focus = focus
        self.foldedReminders = foldedReminders
        self.receivedAtMillis = receivedAtMillis
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

    /// Whether this block is a break/rest span — used to split "next event" vs "next break" on the
    /// watch (mirrors Android's break-aware NowScreen). Matches the BREAK category case-insensitively.
    var isBreak: Bool { category.uppercased() == "BREAK" }
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

/// A single medication dose mirrored from the phone. Mirrors Android's `WearMed`
/// (id, name, doseLabel, reminderMinute, taken). The watch shows these on a dedicated Meds page
/// with per-dose "Taken" controls; `reminderMinute` (minute-of-day) drives overdue/earliest sorting.
struct WatchMed: Codable, Equatable, Identifiable, Sendable {
    var id: String
    var name: String
    /// Human dose summary, e.g. "1 tablet · 10mg" (mirrors Android `WearMed.doseLabel`).
    var doseLabel: String
    var reminderMinute: Int
    var taken: Bool

    /// Whether this dose is past its reminder time and still untaken, relative to `nowMinute`.
    /// Mirrors Android's `!med.taken && med.reminderMinute < nowMinute`.
    func isOverdue(nowMinute: Int) -> Bool { !taken && reminderMinute < nowMinute }
}

/// Orders doses for an at-a-glance list: untaken first (earliest reminder first, so anything already
/// overdue floats to the top), taken doses last. 1:1 port of Android's `sortMedsForGlance`.
func sortMedsForGlance(_ meds: [WatchMed]) -> [WatchMed] {
    meds.sorted { lhs, rhs in
        if lhs.taken != rhs.taken { return !lhs.taken }      // untaken first
        return lhs.reminderMinute < rhs.reminderMinute        // then earliest reminder
    }
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

/// One ranked folded reminder chip mirrored from the phone live surface.
struct WatchFoldedReminder: Codable, Equatable, Identifiable, Sendable {
    var kind: WatchFoldedReminderKind
    var entityId: String
    var title: String
    var detail: String
    var isOverdue: Bool

    var id: String { "\(kind.rawValue)-\(entityId)" }
}

enum WatchFoldedReminderKind: String, Codable, Sendable {
    case medication, task, habit
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
    /// Watch opened — ask the phone to push a fresh snapshot immediately (mirrors Android TYPE_SYNC).
    case syncRequest
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
