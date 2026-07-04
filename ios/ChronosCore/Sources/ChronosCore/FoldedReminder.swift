import Foundation

/// The iOS mirror of Android `FoldedReminder.kt` — pure ranking and per-kind due rules for the
/// schedule Live Activity fold. Keeps the coordinator thin and unit-testable without SwiftData.
public enum FoldedReminderKind: Int, Hashable, Sendable {
    case medication = 0
    case task = 1
    case habit = 2
}

public struct FoldedReminder: Hashable, Sendable {
    public var kind: FoldedReminderKind
    public var entityID: String
    public var title: String
    public var detail: String
    public var dueMinute: Int
    public var isOverdue: Bool

    public init(
        kind: FoldedReminderKind,
        entityID: String,
        title: String,
        detail: String,
        dueMinute: Int,
        isOverdue: Bool
    ) {
        self.kind = kind
        self.entityID = entityID
        self.title = title
        self.detail = detail
        self.dueMinute = dueMinute
        self.isOverdue = isOverdue
    }
}

/// Matches Android `OVERDUE_THRESHOLD_MINUTES` and iOS reminder snooze cadence.
public let foldedReminderOverdueThresholdMinutes = 15

/// Matches Android `MAX_FOLDED_REMINDERS`.
public let maxFoldedReminders = 3

public func rankFoldedReminders(
    _ candidates: [FoldedReminder],
    limit: Int = maxFoldedReminders
) -> [FoldedReminder] {
    candidates
        .sorted {
            let ka = foldedReminderKindWeight($0.kind), kb = foldedReminderKindWeight($1.kind)
            if ka != kb { return ka < kb }
            return $0.dueMinute < $1.dueMinute
        }
        .prefix(Swift.max(0, limit))
        .map { $0 }
}

private func foldedReminderKindWeight(_ kind: FoldedReminderKind) -> Int {
    kind.rawValue
}

// MARK: - Medication

public struct MedicationFoldInput: Sendable {
    public var id: String
    public var name: String
    public var dosage: String
    public var unit: String
    public var isActive: Bool
    public var reminderMinutes: [Int]
    public var pausedUntil: Date?
    public var takenScheduledMinutes: Set<Int>

    public init(
        id: String,
        name: String,
        dosage: String,
        unit: String,
        isActive: Bool,
        reminderMinutes: [Int],
        pausedUntil: Date?,
        takenScheduledMinutes: Set<Int>
    ) {
        self.id = id
        self.name = name
        self.dosage = dosage
        self.unit = unit
        self.isActive = isActive
        self.reminderMinutes = reminderMinutes
        self.pausedUntil = pausedUntil
        self.takenScheduledMinutes = takenScheduledMinutes
    }
}

public func buildMedicationFoldedReminders(
    plans: [MedicationFoldInput],
    today: Date,
    nowMinute: Int,
    calendar: Calendar = .current
) -> [FoldedReminder] {
    let startToday = calendar.startOfDay(for: today)
    return plans.flatMap { plan -> [FoldedReminder] in
        guard plan.isActive else { return [] }
        if let paused = plan.pausedUntil, startToday < calendar.startOfDay(for: paused) { return [] }
        guard let due = plan.reminderMinutes.filter({ $0 <= nowMinute }).max() else { return [] }
        guard !plan.takenScheduledMinutes.contains(due) else { return [] }
        let dose = "\(plan.dosage) \(plan.unit)".trimmingCharacters(in: .whitespaces)
        let detail = dose.isEmpty ? "Due \(clockTime(due))" : "Due \(clockTime(due)) · \(dose)"
        return [
            FoldedReminder(
                kind: .medication,
                entityID: plan.id,
                title: plan.name,
                detail: detail,
                dueMinute: due,
                isOverdue: nowMinute - due >= foldedReminderOverdueThresholdMinutes)
        ]
    }
}

// MARK: - Tasks

public struct TaskFoldInput: Sendable {
    public var id: String
    public var title: String
    public var isCompleted: Bool
    public var dueDate: Date?
    public var priority: Int

    public init(
        id: String,
        title: String,
        isCompleted: Bool,
        dueDate: Date?,
        priority: Int
    ) {
        self.id = id
        self.title = title
        self.isCompleted = isCompleted
        self.dueDate = dueDate
        self.priority = priority
    }
}

public func buildTaskFoldedReminders(
    tasks: [TaskFoldInput],
    today: Date,
    nowMinute: Int,
    calendar: Calendar = .current
) -> [FoldedReminder] {
    tasks.compactMap { task in
        guard !task.isCompleted, let due = task.dueDate, calendar.isDate(due, inSameDayAs: today) else {
            return nil
        }
        let dueMin = calendar.component(.hour, from: due) * 60 + calendar.component(.minute, from: due)
        guard dueMin <= nowMinute else { return nil }
        let detail = task.priority >= 3
            ? "Due \(clockTime(dueMin)) · High priority"
            : "Due \(clockTime(dueMin))"
        return FoldedReminder(
            kind: .task,
            entityID: task.id,
            title: task.title,
            detail: detail,
            dueMinute: dueMin,
            isOverdue: nowMinute - dueMin >= foldedReminderOverdueThresholdMinutes)
    }
}

// MARK: - Habits

public struct HabitFoldInput: Sendable {
    public var id: String
    public var title: String
    public var isActive: Bool
    public var isDueToday: Bool
    public var isCompletedToday: Bool
    public var isSkippedToday: Bool
    public var isPausedToday: Bool
    public var windowOpenMinute: Int
    public var streakCount: Int

    public init(
        id: String,
        title: String,
        isActive: Bool,
        isDueToday: Bool,
        isCompletedToday: Bool,
        isSkippedToday: Bool,
        isPausedToday: Bool,
        windowOpenMinute: Int,
        streakCount: Int
    ) {
        self.id = id
        self.title = title
        self.isActive = isActive
        self.isDueToday = isDueToday
        self.isCompletedToday = isCompletedToday
        self.isSkippedToday = isSkippedToday
        self.isPausedToday = isPausedToday
        self.windowOpenMinute = windowOpenMinute
        self.streakCount = streakCount
    }
}

public func buildHabitFoldedReminders(
    habits: [HabitFoldInput],
    nowMinute: Int
) -> [FoldedReminder] {
    habits.compactMap { habit in
        guard habit.isActive, habit.isDueToday,
              !habit.isCompletedToday, !habit.isSkippedToday, !habit.isPausedToday
        else { return nil }
        let open = habit.windowOpenMinute
        guard open <= nowMinute else { return nil }
        let detail = habit.streakCount > 0
            ? "Window open · \(habit.streakCount)-day streak"
            : "Window open"
        return FoldedReminder(
            kind: .habit,
            entityID: habit.id,
            title: habit.title,
            detail: detail,
            dueMinute: open,
            isOverdue: nowMinute - open >= foldedReminderOverdueThresholdMinutes)
    }
}
