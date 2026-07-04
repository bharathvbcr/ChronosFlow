import ChronosCore
import Foundation
import SwiftData

/// Shared fold resolution for the schedule Live Activity and notification suppression.
/// Keeps [BlockLiveActivityCoordinator] and [ChronosNotifications] in sync without duplicating
/// SwiftData fetches.
enum FoldedReminderLiveResolver {
    struct EntityKey: Hashable {
        var kind: FoldedReminderKind
        var id: String
    }

    @MainActor
    static func resolveRanked(
        nowMinute: Int,
        settings: ChronosSettings,
        context: ModelContext
    ) -> [FoldedReminder] {
        guard settings.remindersFoldedIntoLiveActivity else { return [] }
        let today = Date.now
        let cal = Calendar.current
        let startToday = cal.startOfDay(for: today)
        var candidates: [FoldedReminder] = []
        if settings.medicationRemindersEnabled {
            let plans = ((try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? [])
                .filter { $0.isActive && !$0.isPaused() }
                .map { plan in
                    MedicationFoldInput(
                        id: plan.id, name: plan.name, dosage: plan.dosage, unit: plan.unit,
                        isActive: true, reminderMinutes: plan.reminderMinutes,
                        pausedUntil: plan.pausedUntil,
                        takenScheduledMinutes: Set(
                            plan.reminderMinutes.filter { plan.isDoseTaken(on: today, scheduledMinute: $0) }))
                }
            candidates += buildMedicationFoldedReminders(plans: plans, today: today, nowMinute: nowMinute)
        }
        if settings.taskRemindersEnabled {
            let tasks = ((try? context.fetch(FetchDescriptor<TaskItem>())) ?? [])
                .map {
                    TaskFoldInput(
                        id: $0.id, title: $0.title, isCompleted: $0.isCompleted,
                        dueDate: $0.dueDate, priority: $0.priority)
                }
            candidates += buildTaskFoldedReminders(tasks: tasks, today: today, nowMinute: nowMinute)
        }
        if settings.habitRemindersEnabled {
            let habits = ((try? context.fetch(FetchDescriptor<Habit>())) ?? [])
                .map { habit in
                    HabitFoldInput(
                        id: habit.id, title: habit.title, isActive: habit.isActive,
                        isDueToday: habit.isDue(), isCompletedToday: habit.isCompleted(on: today),
                        isSkippedToday: habit.isSkipped(on: today),
                        isPausedToday: habit.pausedUntil.map { startToday < cal.startOfDay(for: $0) } ?? false,
                        windowOpenMinute: habit.deferUntilMinuteOfDay ?? habit.windowStartMinute,
                        streakCount: habit.streakCount)
                }
            candidates += buildHabitFoldedReminders(habits: habits, nowMinute: nowMinute)
        }
        return rankFoldedReminders(candidates)
    }

    @MainActor
    static func resolveEntityKeys(
        nowMinute: Int,
        settings: ChronosSettings,
        context: ModelContext
    ) -> Set<EntityKey> {
        Set(resolveRanked(nowMinute: nowMinute, settings: settings, context: context)
            .map { EntityKey(kind: $0.kind, id: $0.entityID) })
    }

    @MainActor
    static func resolveReminderChips(
        nowMinute: Int,
        settings: ChronosSettings,
        context: ModelContext
    ) -> [BlockActivityAttributes.ReminderChip] {
        resolveRanked(nowMinute: nowMinute, settings: settings, context: context)
            .map(reminderChip(from:))
    }

    /// Earliest upcoming minute when a folded reminder becomes due — used to enqueue a BG refresh
    /// near fire time when the app is killed.
    @MainActor
    static func nextDueFireDate(
        nowMinute: Int,
        settings: ChronosSettings,
        context: ModelContext
    ) -> Date? {
        let ranked = resolveRanked(nowMinute: nowMinute, settings: settings, context: context)
        guard !ranked.isEmpty else { return nil }
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        if let upcoming = ranked.map(\.dueMinute).filter({ $0 > nowMinute }).min(),
           let date = cal.date(byAdding: .minute, value: upcoming, to: today) {
            return date
        }
        // Everything ranked is already due — refresh soon so LA catches up without waiting for stale.
        return Date.now.addingTimeInterval(60)
    }

    static func reminderChip(from folded: FoldedReminder) -> BlockActivityAttributes.ReminderChip {
        BlockActivityAttributes.ReminderChip(
            kind: {
                switch folded.kind {
                case .medication: .medication
                case .task: .task
                case .habit: .habit
                }
            }(),
            entityID: folded.entityID,
            title: folded.title,
            detail: folded.detail,
            isOverdue: folded.isOverdue)
    }
}
