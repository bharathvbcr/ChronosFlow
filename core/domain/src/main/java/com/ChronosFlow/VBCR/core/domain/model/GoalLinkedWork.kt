package com.ChronosFlow.VBCR.core.domain.model

import java.time.LocalDate

/**
 * The concrete tasks and habits linked to a goal, summarized for the goal detail
 * view. These are lightweight projections — only the fields the list renders —
 * so surfacing them never assembles full task/habit aggregates.
 */
data class GoalLinkedTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val priority: Int,
    val targetDate: LocalDate?
)

data class GoalLinkedHabit(
    val id: String,
    val title: String,
    val cadence: String,
    val streakCount: Int,
    val isActive: Boolean
)

data class GoalLinkedWork(
    val tasks: List<GoalLinkedTask> = emptyList(),
    val habits: List<GoalLinkedHabit> = emptyList()
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && habits.isEmpty()
}
