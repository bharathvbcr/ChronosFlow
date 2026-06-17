import Foundation

// Goal progress roll-up — port of `deriveGoalProgress` / GoalWithProgress in Goal.kt.
// Manual progress plus derived linked-work counts (completed tasks + habit completions), capped
// at the target.

public struct GoalDerivedProgress: Sendable, Equatable {
    public var completedTaskCount: Int
    public var habitCompletionCount: Int
    public init(completedTaskCount: Int = 0, habitCompletionCount: Int = 0) {
        self.completedTaskCount = completedTaskCount
        self.habitCompletionCount = habitCompletionCount
    }
}

/// Manual progress + derived linked-work counts, capped at the target (when target > 0).
public func deriveGoalProgress(progressValue: Int, targetValue: Int, derived: GoalDerivedProgress) -> Int {
    let combined = progressValue + derived.completedTaskCount + derived.habitCompletionCount
    return targetValue <= 0 ? combined : min(combined, targetValue)
}

/// Fraction in 0...1 for a progress ring. Mirrors `GoalWithProgress.progressFraction`.
public func goalProgressFraction(
    progressValue: Int, targetValue: Int, isCompleted: Bool, derived: GoalDerivedProgress = .init()
) -> Double {
    guard targetValue > 0 else { return isCompleted ? 1 : 0 }
    let value = Double(deriveGoalProgress(progressValue: progressValue, targetValue: targetValue, derived: derived))
    return min(max(value / Double(targetValue), 0), 1)
}
