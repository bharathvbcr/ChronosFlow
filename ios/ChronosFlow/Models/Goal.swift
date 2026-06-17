import Foundation
import SwiftData

/// A measurable objective that completed tasks and habit completions can roll up into.
/// Ported from `Goal.kt` plus its `deriveGoalProgress` helper.
@Model
final class Goal {
    @Attribute(.unique) var id: String
    var title: String
    var detail: String?
    var category: String
    var targetValue: Int
    var startDate: Date
    var targetDate: Date?
    /// Manually-entered progress; derived task/habit counts are added on top at read time.
    var progressValue: Int
    var isCompleted: Bool

    init(
        id: String = UUID().uuidString,
        title: String,
        detail: String? = nil,
        category: String = "GENERAL",
        targetValue: Int = 1,
        startDate: Date = .now,
        targetDate: Date? = nil,
        progressValue: Int = 0,
        isCompleted: Bool = false
    ) {
        self.id = id
        self.title = title
        self.detail = detail
        self.category = category
        self.targetValue = targetValue
        self.startDate = startDate
        self.targetDate = targetDate
        self.progressValue = progressValue
        self.isCompleted = isCompleted
    }

    /// Manual progress + derived linked-work counts, capped at the target.
    /// Mirrors `deriveGoalProgress(goal, derived)`.
    func totalProgress(completedTasks: Int = 0, habitCompletions: Int = 0) -> Int {
        let combined = progressValue + completedTasks + habitCompletions
        return targetValue <= 0 ? combined : min(combined, targetValue)
    }

    func progressFraction(completedTasks: Int = 0, habitCompletions: Int = 0) -> Double {
        guard targetValue > 0 else { return isCompleted ? 1 : 0 }
        let value = Double(totalProgress(completedTasks: completedTasks, habitCompletions: habitCompletions))
        return min(max(value / Double(targetValue), 0), 1)
    }
}
