import Foundation
import SwiftData
import ChronosCore

/// A to-do that can be prioritized, scheduled into real time, and turned into a focus session.
/// Ported from `Task.kt` (renamed `TaskItem` to avoid clashing with Swift Concurrency's `Task`).
@Model
final class TaskItem {
    @Attribute(.unique) var id: String
    var title: String
    var detail: String?
    var isCompleted: Bool
    /// 0 = none, 1 = low, 2 = medium, 3 = high (mirrors the Android integer priority).
    var priority: Int
    var dueDate: Date?
    var createdAt: Date
    var updatedAt: Date

    var preferredDurationMinutes: Int?
    var preferredStartMinuteOfDay: Int?
    /// Day the task is slotted onto, stored as start-of-day.
    var targetDate: Date?
    var goalID: String?

    /// Optional recurrence — when a recurring task is completed the app spawns the next occurrence.
    var recurrence: RecurrenceSpec?

    /// Checklist sub-items, stored inline as a value type (matches `TaskChecklistItem`).
    var checklist: [ChecklistItem]

    /// Backup/restore round-trip representation of task attachments. Mirrors Android
    /// `Task.attachments: List<TaskAttachment>` (default empty). Uses the ChronosCore
    /// `AttachmentDTO` (the cross-platform Codable mirror) so a full-data export written by
    /// either platform decodes here unchanged.
    ///
    /// Declared OPTIONAL with a `nil` default so adding it is a zero-migration, CloudKit-safe
    /// change: a `TaskItem` saved before the field existed decodes it as `nil`. Use the
    /// `attachmentList` accessor for the Android-equivalent "empty list" semantics.
    var attachments: [AttachmentDTO]?

    init(
        id: String = UUID().uuidString,
        title: String,
        detail: String? = nil,
        isCompleted: Bool = false,
        priority: Int = 0,
        dueDate: Date? = nil,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        preferredDurationMinutes: Int? = nil,
        preferredStartMinuteOfDay: Int? = nil,
        targetDate: Date? = nil,
        goalID: String? = nil,
        recurrence: RecurrenceSpec? = nil,
        checklist: [ChecklistItem] = [],
        attachments: [AttachmentDTO]? = nil
    ) {
        self.id = id
        self.title = title
        self.detail = detail
        self.isCompleted = isCompleted
        self.priority = priority
        self.dueDate = dueDate
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.preferredDurationMinutes = preferredDurationMinutes
        self.preferredStartMinuteOfDay = preferredStartMinuteOfDay
        self.targetDate = targetDate.map { Calendar.current.startOfDay(for: $0) }
        self.goalID = goalID
        self.recurrence = recurrence
        self.checklist = checklist
        self.attachments = attachments
    }

    /// Android-equivalent non-optional view of `attachments` (treats `nil` as the empty list, the
    /// same default `Task.attachments` carries). Use this for read/round-trip logic.
    var attachmentList: [AttachmentDTO] {
        get { attachments ?? [] }
        set { attachments = newValue.isEmpty ? nil : newValue }
    }

    var priorityLabel: String {
        switch priority {
        case 3: "High"
        case 2: "Medium"
        case 1: "Low"
        default: "None"
        }
    }

    var checklistProgress: Double {
        guard !checklist.isEmpty else { return isCompleted ? 1 : 0 }
        let done = checklist.filter(\.isDone).count
        return Double(done) / Double(checklist.count)
    }
}

/// Inline checklist sub-item. Mirrors `TaskChecklistItem.kt`.
struct ChecklistItem: Codable, Hashable, Identifiable, Sendable {
    var id: String = UUID().uuidString
    var text: String
    var isDone: Bool = false
    var order: Int = 0
}

/// A simple recurrence spec stored inline on a task. The app advances a date by this when a
/// recurring task is completed. Mirrors the ChronosCore `RecurrenceRule` shape (verified off-device).
///
/// `weekdays` (1=Sun … 7=Sat, `Calendar.component(.weekday)`) lets a weekly rule target a specific
/// set of days (e.g. Mon/Wed/Fri). It is stored inline as JSON on the `@Model` (this struct is the
/// SwiftData-persisted Codable), so adding the field is a zero-migration change: a task saved before
/// the field existed simply decodes `weekdays` as the empty default. An empty set on a weekly rule
/// means "same weekday as the anchor", preserving the old every-N-weeks behaviour.
struct RecurrenceSpec: Codable, Hashable, Sendable {
    enum Frequency: String, Codable, CaseIterable, Sendable { case daily, weekly, monthly }
    var frequency: Frequency
    var interval: Int = 1
    /// For `.weekly`: the target weekdays (1=Sun … 7=Sat). Empty = anchor weekday only.
    var weekdays: Set<Int> = []

    private enum CodingKeys: String, CodingKey { case frequency, interval, weekdays }

    init(frequency: Frequency, interval: Int = 1, weekdays: Set<Int> = []) {
        self.frequency = frequency
        self.interval = interval
        self.weekdays = weekdays
    }

    /// Decode tolerant of pre-`weekdays` records (the key is simply absent → empty set).
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        frequency = try c.decode(Frequency.self, forKey: .frequency)
        interval = try c.decodeIfPresent(Int.self, forKey: .interval) ?? 1
        weekdays = try c.decodeIfPresent(Set<Int>.self, forKey: .weekdays) ?? []
    }

    var label: String {
        if frequency == .weekly, !weekdays.isEmpty {
            return RecurrenceSpec.weekdayLabel(for: weekdays)
        }
        let unit = switch frequency { case .daily: "day"; case .weekly: "week"; case .monthly: "month" }
        return interval == 1 ? "Every \(unit)" : "Every \(interval) \(unit)s"
    }

    /// Bridge to the ChronosCore rule so date math is shared (not reimplemented).
    var coreRule: RecurrenceRule {
        let freq: RecurrenceFrequency = switch frequency {
            case .daily: .daily; case .weekly: .weekly; case .monthly: .monthly
        }
        return RecurrenceRule(frequency: freq, interval: max(interval, 1),
                              weekdays: frequency == .weekly ? weekdays : [])
    }

    /// The next occurrence date after `date`. For a weekly weekday-set rule this advances to the
    /// next matching weekday via `ChronosCore.expandRecurrence`; otherwise it steps by interval.
    func nextDate(after date: Date, calendar: Calendar = .current) -> Date? {
        if frequency == .weekly, !weekdays.isEmpty {
            // Look ahead a bounded window and take the first expanded occurrence strictly after the
            // anchor day. The window covers the largest interval the editor allows (30 weeks).
            let from = calendar.date(byAdding: .day, value: 1, to: date) ?? date
            let horizon = calendar.date(byAdding: .day, value: max(interval, 1) * 7 + 7, to: from) ?? from
            return expandRecurrence(coreRule, from: from, to: horizon, calendar: calendar).first
        }
        let comp: Calendar.Component = switch frequency {
            case .daily: .day; case .weekly: .weekOfYear; case .monthly: .month
        }
        return calendar.date(byAdding: comp, value: max(interval, 1), to: date)
    }

    /// Human label for a weekday set, e.g. "Weekdays", "Weekends", "Mon, Wed, Fri".
    static func weekdayLabel(for days: Set<Int>) -> String {
        if days == [2, 3, 4, 5, 6] { return "Weekdays" }
        if days == [7, 1] { return "Weekends" }
        let names = ["", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        return days.sorted { orderIndex($0) < orderIndex($1) }
            .map { names[$0] }.joined(separator: ", ")
    }

    /// Mon-first ordering for display (Mon…Sun), keeping the weekday chips natural.
    private static func orderIndex(_ weekday: Int) -> Int { weekday == 1 ? 7 : weekday - 1 }
}
