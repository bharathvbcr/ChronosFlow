package com.chronosflow.core.domain.model

import java.time.LocalDate

data class Goal(
    val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val targetValue: Int,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val progressValue: Int,
    val isCompleted: Boolean
)

/** Counts derived from work linked to a goal (completed tasks, logged habit completions). */
data class GoalDerivedProgress(
    val completedTaskCount: Int = 0,
    val habitCompletionCount: Int = 0
)

data class GoalWithProgress(
    val goal: Goal,
    val derived: GoalDerivedProgress = GoalDerivedProgress()
) {
    val totalProgress: Int
        get() = deriveGoalProgress(goal, derived)

    val progressFraction: Float
        get() = if (goal.targetValue <= 0) {
            if (goal.isCompleted) 1f else 0f
        } else {
            totalProgress.toFloat() / goal.targetValue.toFloat()
        }.coerceIn(0f, 1f)
}

/** Manual progress plus the derived linked-work counts, capped at the target. */
fun deriveGoalProgress(goal: Goal, derived: GoalDerivedProgress): Int {
    val combined = goal.progressValue + derived.completedTaskCount + derived.habitCompletionCount
    return if (goal.targetValue <= 0) combined else combined.coerceAtMost(goal.targetValue)
}
