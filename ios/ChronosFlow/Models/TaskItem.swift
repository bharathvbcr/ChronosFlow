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

    /// Contact snapshot linked to this task (Android `Task.linkedContact: TaskContactSnapshot?`).
    /// Optional with a `nil` default — a zero-migration, lightweight-migration-safe addition.
    var linkedContact: ContactSnapshotDTO?

    /// External actions (call / email / link…) attached to this task. Mirrors Android
    /// `Task.actions: List<TaskAction>` (default empty). Optional + `nil` default for the same
    /// zero-migration reason as `attachments`; use `actionList` for empty-list semantics.
    var actions: [TaskActionDTO]?

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
        attachments: [AttachmentDTO]? = nil,
        linkedContact: ContactSnapshotDTO? = nil,
        actions: [TaskActionDTO]? = nil
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
        self.linkedContact = linkedContact
        self.actions = actions
    }

    /// Android-equivalent non-optional view of `attachments` (treats `nil` as the empty list, the
    /// same default `Task.attachments` carries). Use this for read/round-trip logic.
    var attachmentList: [AttachmentDTO] {
        get { attachments ?? [] }
        set { attachments = newValue.isEmpty ? nil : newValue }
    }

    /// Android-equivalent non-optional view of `actions` (nil = empty list).
    var actionList: [TaskActionDTO] {
        get { actions ?? [] }
        set { actions = newValue.isEmpty ? nil : newValue }
    }

    /// The action a row's external button fires: explicit primary first, then Android's type order
    /// (call → email → link → …). Mirrors `TaskContextCommandSet.primaryExternalCommand` for actions.
    var primaryAction: TaskActionDTO? {
        let sorted = actionList.sorted { $0.type.sortPriority < $1.type.sortPriority }
        return sorted.first(where: \.isPrimary) ?? sorted.first
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

    /// `true` when the task is urgent enough that Android arms an exact alarm (priority ≥ 2).
    var isUrgent: Bool { priority >= 2 }

    /// A compact schedule descriptor for the task-row badge. Mirrors Android `taskScheduleSummary`:
    /// recurrence label first, then target-day / duration / preferred-start / steps-left parts.
    /// Returns `nil` when there's nothing scheduled to show.
    func scheduleSummary(today: Date = .now, calendar: Calendar = .current) -> String? {
        if let recurrence {
            return recurrence.label
        }
        var parts: [String] = []
        if let target = targetDate {
            parts.append(TaskItem.targetDateSummary(target, today: today, calendar: calendar))
        }
        if let duration = preferredDurationMinutes {
            parts.append("\(TaskItem.durationSummary(duration)) block")
        }
        if let start = preferredStartMinuteOfDay {
            parts.append("Around \(formatDisplayMinute(start))")
        }
        let stepsLeft = checklist.filter { !$0.isDone }.count
        if !checklist.isEmpty, stepsLeft > 0 {
            parts.append("\(stepsLeft) steps left")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " • ")
    }

    private static func targetDateSummary(_ target: Date, today: Date, calendar: Calendar) -> String {
        let day = calendar.startOfDay(for: target)
        let base = calendar.startOfDay(for: today)
        if calendar.isDate(day, inSameDayAs: base) { return "Target today" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: base),
           calendar.isDate(day, inSameDayAs: tomorrow) { return "Target tomorrow" }
        return "Target \(day.formatted(.dateTime.month().day()))"
    }

    private static func durationSummary(_ minutes: Int) -> String {
        let h = minutes / 60, m = minutes % 60
        if h > 0 && m > 0 { return "\(h)h \(m)m" }
        if h > 0 { return "\(h)h" }
        return "\(minutes)m"
    }

    /// A descriptor for linked connections (contact / actions / attachments). Mirrors Android
    /// `taskConnectionSummary`: "Contact: X • Action: Y • File: Z (+ N more)".
    var connectionSummary: String? {
        var parts: [String] = []
        if let contact = linkedContact, !contact.displayName.isEmpty {
            parts.append("Contact: \(contact.displayName)")
        }
        let actions = actionList
        if !actions.isEmpty {
            let primaryLabel = (actions.first(where: \.isPrimary) ?? actions.first)?.label
            parts.append(TaskItem.namedConnectionPart(
                label: "Action", fallbackSingular: "action", fallbackPlural: "actions",
                count: actions.count, primaryName: primaryLabel))
        }
        let attachments = attachmentList
        if !attachments.isEmpty {
            let featuredName = (attachments.first(where: \.isFeaturedImage) ?? attachments.first)?.displayName
            parts.append(TaskItem.namedConnectionPart(
                label: "File", fallbackSingular: "attachment", fallbackPlural: "attachments",
                count: attachments.count, primaryName: featuredName))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " • ")
    }

    /// Mirrors Android `taskNamedConnectionPart`.
    private static func namedConnectionPart(
        label: String, fallbackSingular: String, fallbackPlural: String,
        count: Int, primaryName: String?
    ) -> String {
        let name = primaryName?.trimmingCharacters(in: .whitespaces)
        if let name, !name.isEmpty {
            return count > 1 ? "\(label): \(name) + \(count - 1) more" : "\(label): \(name)"
        }
        return count == 1 ? "1 \(fallbackSingular)" : "\(count) \(fallbackPlural)"
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
    /// `monthlyOrdinal` mirrors Android's `TaskRecurringCadence.MONTHLY_ORDINAL` ("2nd Monday").
    enum Frequency: String, Codable, CaseIterable, Sendable { case daily, weekly, monthly, monthlyOrdinal }
    var frequency: Frequency
    var interval: Int = 1
    /// For `.weekly`: the target weekdays (1=Sun … 7=Sat). Empty = anchor weekday only.
    var weekdays: Set<Int> = []

    /// For `.monthlyOrdinal`: which occurrence of the weekday in the month (1…4, or 5 = last).
    /// Mirrors Android `TaskRecurringConfig.ordinal`.
    var ordinal: Int = 1
    /// For `.monthlyOrdinal`: the target weekday (1=Sun … 7=Sat). Mirrors `ordinalWeekday`.
    var ordinalWeekday: Int = 2

    /// When the recurrence series begins (start-of-day). `nil` = "from the task's anchor date".
    /// Mirrors Android `TaskRecurringConfig.startsOn`.
    var startsOn: Date?
    /// Optional last day the recurrence is active (start-of-day). Mirrors `endsOn`.
    var endsOn: Date?

    /// A per-occurrence reminder for a recurring task. Mirrors Android `TaskReminderDraft`
    /// (`TaskReminderTrigger.AT_TIME` / `BEFORE_OCCURRENCE`): either fire at a fixed clock time on
    /// each occurrence day, or a lead offset before the task's preferred start.
    struct Reminder: Codable, Hashable, Identifiable, Sendable {
        enum Trigger: String, Codable, Sendable { case atTime, beforeOccurrence }
        var id: String = UUID().uuidString
        var trigger: Trigger = .atTime
        /// For `.atTime`: minutes after midnight. `nil` falls back to the task's preferred start.
        var minuteOfDay: Int?
        /// For `.beforeOccurrence`: lead minutes before the preferred start (must be > 0).
        var offsetMinutesBefore: Int?

        init(id: String = UUID().uuidString, trigger: Trigger = .atTime,
             minuteOfDay: Int? = nil, offsetMinutesBefore: Int? = nil) {
            self.id = id
            self.trigger = trigger
            self.minuteOfDay = minuteOfDay
            self.offsetMinutesBefore = offsetMinutesBefore
        }

        /// Tolerant decode so a reminder written by another platform (or a later schema) never
        /// fails the whole task import: missing keys fall back to defaults.
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
            trigger = try c.decodeIfPresent(Trigger.self, forKey: .trigger) ?? .atTime
            minuteOfDay = try c.decodeIfPresent(Int.self, forKey: .minuteOfDay)
            offsetMinutesBefore = try c.decodeIfPresent(Int.self, forKey: .offsetMinutesBefore)
        }

        private enum CodingKeys: String, CodingKey {
            case id, trigger, minuteOfDay, offsetMinutesBefore
        }
    }

    /// Per-occurrence reminders (Android `TaskRecurringConfig.reminderDrafts`). Stored inside the
    /// same inline JSON blob, so adding the field is a zero-migration change (missing key → empty).
    var reminders: [Reminder] = []

    private enum CodingKeys: String, CodingKey {
        case frequency, interval, weekdays, ordinal, ordinalWeekday, startsOn, endsOn, reminders
    }

    init(
        frequency: Frequency,
        interval: Int = 1,
        weekdays: Set<Int> = [],
        ordinal: Int = 1,
        ordinalWeekday: Int = 2,
        startsOn: Date? = nil,
        endsOn: Date? = nil,
        reminders: [Reminder] = []
    ) {
        self.frequency = frequency
        self.interval = interval
        self.weekdays = weekdays
        self.ordinal = ordinal
        self.ordinalWeekday = ordinalWeekday
        self.startsOn = startsOn
        self.endsOn = endsOn
        self.reminders = reminders
    }

    /// Decode tolerant of pre-existing records: any key added after a task was first saved is simply
    /// absent and falls back to its default, keeping this a zero-migration change.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        frequency = try c.decode(Frequency.self, forKey: .frequency)
        interval = try c.decodeIfPresent(Int.self, forKey: .interval) ?? 1
        weekdays = try c.decodeIfPresent(Set<Int>.self, forKey: .weekdays) ?? []
        ordinal = try c.decodeIfPresent(Int.self, forKey: .ordinal) ?? 1
        ordinalWeekday = try c.decodeIfPresent(Int.self, forKey: .ordinalWeekday) ?? 2
        startsOn = try c.decodeIfPresent(Date.self, forKey: .startsOn)
        endsOn = try c.decodeIfPresent(Date.self, forKey: .endsOn)
        reminders = try c.decodeIfPresent([Reminder].self, forKey: .reminders) ?? []
    }

    var label: String {
        if frequency == .monthlyOrdinal {
            let count = interval == 1 ? "" : "\(interval) "
            return "Every \(count)\(RecurrenceSpec.ordinalLabel(ordinal)) \(RecurrenceSpec.weekdayName(ordinalWeekday))"
        }
        if frequency == .weekly, !weekdays.isEmpty {
            return RecurrenceSpec.weekdayLabel(for: weekdays)
        }
        let unit = switch frequency { case .daily: "day"; case .weekly: "week"; default: "month" }
        return interval == 1 ? "Every \(unit)" : "Every \(interval) \(unit)s"
    }

    /// "1st"…"4th", "Last" — mirrors Android `displayOrdinalLabel`.
    static func ordinalLabel(_ ordinal: Int) -> String {
        switch ordinal { case 1: "1st"; case 2: "2nd"; case 3: "3rd"; case 4: "4th"; default: "Last" }
    }

    /// Full weekday name for a `Calendar.component(.weekday)` value (1=Sun … 7=Sat).
    static func weekdayName(_ weekday: Int) -> String {
        let names = ["", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
        return names.indices.contains(weekday) ? names[weekday] : "Monday"
    }

    /// Bridge to the ChronosCore rule so date math is shared (not reimplemented). `monthlyOrdinal`
    /// has no core analogue, so it maps to `.monthly` here (the ordinal step is handled in `nextDate`).
    var coreRule: RecurrenceRule {
        let freq: RecurrenceFrequency = switch frequency {
            case .daily: .daily; case .weekly: .weekly; case .monthly, .monthlyOrdinal: .monthly
        }
        return RecurrenceRule(frequency: freq, interval: max(interval, 1),
                              weekdays: frequency == .weekly ? weekdays : [])
    }

    /// The next occurrence date after `date`. For a weekly weekday-set rule this advances to the
    /// next matching weekday via `ChronosCore.expandRecurrence`; the monthly-ordinal rule lands on the
    /// Nth weekday of the next interval month; otherwise it steps by interval.
    func nextDate(after date: Date, calendar: Calendar = .current) -> Date? {
        if frequency == .monthlyOrdinal {
            return nextOrdinalDate(after: date, calendar: calendar)
        }
        if frequency == .weekly, !weekdays.isEmpty {
            // Look ahead a bounded window and take the first expanded occurrence strictly after the
            // anchor day. The window covers the largest interval the editor allows (30 weeks).
            let from = calendar.date(byAdding: .day, value: 1, to: date) ?? date
            let horizon = calendar.date(byAdding: .day, value: max(interval, 1) * 7 + 7, to: from) ?? from
            return expandRecurrence(coreRule, from: from, to: horizon, calendar: calendar).first
        }
        let comp: Calendar.Component = switch frequency {
            case .daily: .day; case .weekly: .weekOfYear; default: .month
        }
        return calendar.date(byAdding: comp, value: max(interval, 1), to: date)
    }

    /// The Nth `ordinalWeekday` (ordinal 1…4, or 5 = last) of the month `interval` months after the
    /// anchor. Mirrors Android's monthly-ordinal expansion in `TaskRecurringSupport`.
    private func nextOrdinalDate(after date: Date, calendar: Calendar) -> Date? {
        let stepMonth = calendar.date(byAdding: .month, value: max(interval, 1), to: date) ?? date
        guard let monthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: stepMonth)),
              let range = calendar.range(of: .day, in: .month, for: monthStart) else { return nil }
        var matches: [Date] = []
        for day in range {
            guard let candidate = calendar.date(byAdding: .day, value: day - 1, to: monthStart) else { continue }
            if calendar.component(.weekday, from: candidate) == ordinalWeekday { matches.append(candidate) }
        }
        guard !matches.isEmpty else { return nil }
        if ordinal >= 5 { return matches.last }
        return matches.indices.contains(ordinal - 1) ? matches[ordinal - 1] : matches.last
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
