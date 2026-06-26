import Foundation
import SwiftData
import ChronosCore

/// A measurable objective that completed tasks and habit completions can roll up into.
/// Ported from `Goal.kt` plus its `deriveGoalProgress` helper.
///
/// `id` is stable (assigned once at creation) so linked work — `TimeBlock`/`TaskItem`/`Habit`
/// rows carrying a matching `goalID` — can be filtered against it. `category` is a non-optional
/// defaulted String (CloudKit-safe) that `GoalDetailView` reads for its category chip + tint.
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

    /// Manual progress + derived linked-work counts, capped at the target. Delegates to the
    /// shared ChronosCore `deriveGoalProgress` so the rollup math lives in one place.
    func totalProgress(completedTasks: Int = 0, habitCompletions: Int = 0) -> Int {
        deriveGoalProgress(
            progressValue: progressValue,
            targetValue: targetValue,
            derived: GoalDerivedProgress(
                completedTaskCount: completedTasks,
                habitCompletionCount: habitCompletions
            )
        )
    }

    /// Fraction in 0...1 for a progress ring. Delegates to ChronosCore `goalProgressFraction`.
    func progressFraction(completedTasks: Int = 0, habitCompletions: Int = 0) -> Double {
        goalProgressFraction(
            progressValue: progressValue,
            targetValue: targetValue,
            isCompleted: isCompleted,
            derived: GoalDerivedProgress(
                completedTaskCount: completedTasks,
                habitCompletionCount: habitCompletions
            )
        )
    }
}
