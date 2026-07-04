import Foundation

/// Guards for whether separate med/task/habit notification schedules should be omitted when fold
/// mode is active (mirrors iOS `ChronosNotifications.skipsSeparateRemindersWhenFolded`).
public enum ReminderFoldScheduling {
    public static func skipsSeparateRemindersWhenFolded(
        remindersFoldedIntoLiveActivity: Bool,
        currentBlockLiveActivityEnabled: Bool
    ) -> Bool {
        remindersFoldedIntoLiveActivity && currentBlockLiveActivityEnabled
    }
}
