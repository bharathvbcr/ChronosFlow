package com.ChronosFlow.VBCR.core.domain.model

/**
 * Snapshot of the user's day for glanceable surfaces (home-screen widgets and the paired
 * Wear OS tiles). One use case builds this once and every surface renders its own slice,
 * so the phone widgets and the watch always agree on what "today" looks like.
 */
data class ChronosDayOverview(
    /** Current + upcoming blocks for today, sorted by start time. Past blocks are dropped. */
    val blocks: List<DayOverviewBlock> = emptyList(),
    /** Open (incomplete) tasks sorted most-urgent first. */
    val openTasks: List<DayOverviewTask> = emptyList(),
    /** Active habits, not-yet-done-today first. */
    val habits: List<DayOverviewHabit> = emptyList(),
    /** Active medication plans sorted by reminder time, dose-pending first. */
    val medications: List<DayOverviewMedication> = emptyList(),
    /** State of the focus session, if any. */
    val focus: DayOverviewFocus = DayOverviewFocus()
) {
    val currentBlock: DayOverviewBlock? get() = blocks.firstOrNull { it.isCurrent }
    val nextBlock: DayOverviewBlock? get() = blocks.firstOrNull { !it.isCurrent }
    val habitsDoneToday: Int get() = habits.count { it.isDoneToday }
}

enum class WidgetFocusState { IDLE, RUNNING, PAUSED }

data class DayOverviewFocus(
    val state: WidgetFocusState = WidgetFocusState.IDLE,
    val timeLeftSeconds: Int = 0
)

data class DayOverviewBlock(
    val id: String,
    val title: String,
    val startMinuteOfDay: Int,
    /** Planned end as start + duration; may exceed 1439 for blocks crossing midnight. */
    val endMinuteOfDay: Int,
    val isCurrent: Boolean,
    /** Raw block category (e.g. "WORK", "BREAK"); drives break/event split + focus eligibility. */
    val category: String = ""
)

data class DayOverviewTask(
    val id: String,
    val title: String,
    /** 0 = normal, 1 = high, 2+ = urgent (matches the tasks feature's scale). */
    val priority: Int
)

data class DayOverviewHabit(
    val id: String,
    val title: String,
    val streakCount: Int,
    val isDoneToday: Boolean
)

data class DayOverviewMedication(
    val id: String,
    /** Raw plan name; surfaces must apply privacy redaction before display. */
    val name: String,
    /** Display dose, e.g. "1 tablet". */
    val doseLabel: String,
    val reminderMinuteOfDay: Int,
    val isTakenToday: Boolean
)
