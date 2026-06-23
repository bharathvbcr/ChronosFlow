package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.SleepReadiness
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import java.time.Instant
import javax.inject.Inject

/** A habit that is due today and not yet completed, with its preferred time window. */
data class GapFillHabitCandidate(
    val habitId: String,
    val title: String,
    val windowStartMinute: Int,
    val windowEndMinute: Int
)

data class GapFillBlock(
    val title: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val category: String,
    val taskId: String? = null,
    val habitId: String? = null
)

data class GapFillProposal(
    val gapCount: Int,
    val proposedBlocks: List<GapFillBlock>
) {
    val taskCount: Int get() = proposedBlocks.count { it.taskId != null }
    val habitCount: Int get() = proposedBlocks.count { it.habitId != null }
}

/**
 * Fills every qualifying free gap with the user's pending tasks (due-date first,
 * then priority) and due habits (placed inside their windows), sizing each to
 * fit the gap. Breaks come last: at most one short recovery block per gap, and
 * only when the gap follows a focus-heavy stretch.
 */
class GapFillPlanner @Inject constructor(
    private val freeTimeCalculator: FreeTimeCalculator
) {
    fun propose(
        blocks: List<TimeBlock>,
        tasks: List<Task>,
        habitCandidates: List<GapFillHabitCandidate>,
        sleepSchedule: SleepSchedule,
        nowMinuteOfDay: Int?,
        addBreaksAutomatically: Boolean,
        minGapMinutes: Int = MIN_GAP_MINUTES,
        readiness: SleepReadiness = SleepReadiness.UNKNOWN
    ): GapFillProposal {
        // After a depleted night, recovery breaks come after a shorter focus stretch and run longer.
        val depleted = readiness == SleepReadiness.DEPLETED
        val focusStretchMinutes = if (depleted) DEPLETED_FOCUS_STRETCH_MINUTES else FOCUS_STRETCH_MINUTES
        val breakDurationMinutes = if (depleted) DEPLETED_BREAK_DURATION_MINUTES else BREAK_DURATION_MINUTES
        val gaps = excludeSleepMinutes(freeTimeCalculator.calculate(blocks), sleepSchedule)
            .mapNotNull { gap -> clipGapToNow(gap, nowMinuteOfDay) }
            .filter { it.endMinute - it.startMinute >= minGapMinutes }

        val scheduledTaskIds = blocks.mapNotNull(TimeBlock::taskId).toSet()
        val scheduledHabitIds = blocks.mapNotNull(TimeBlock::habitId).toSet()
        val taskQueue = tasks
            .filter { !it.isCompleted && it.id !in scheduledTaskIds }
            .sortedWith(
                compareBy<Task> { it.dueDate ?: Instant.MAX }
                    .thenByDescending(Task::priority)
                    .thenBy(Task::createdAt)
            )
            .toMutableList()
        val habitQueue = habitCandidates
            .filter { it.habitId !in scheduledHabitIds }
            .sortedBy(GapFillHabitCandidate::windowStartMinute)
            .toMutableList()

        val proposed = mutableListOf<GapFillBlock>()
        gaps.forEach { gap ->
            var cursor = gap.startMinute
            var placedMinutes = 0
            while (gap.endMinute - cursor >= MIN_PLACEMENT_MINUTES) {
                val available = gap.endMinute - cursor
                val habit = habitQueue.firstOrNull { candidate ->
                    cursor < candidate.windowEndMinute && gap.endMinute > candidate.windowStartMinute
                }
                val block: GapFillBlock? = run {
                    if (habit != null) {
                        val b = habitBlockFor(habit, cursor, gap.endMinute)
                        if (b != null) {
                            habitQueue.remove(habit)
                            return@run b
                        }
                        // habit can't fit at this cursor — fall through to task placement
                    }
                    if (taskQueue.isNotEmpty()) {
                        val task = taskQueue.removeAt(0)
                        GapFillBlock(
                            title = task.title,
                            startMinute = cursor,
                            durationMinutes = taskDurationFor(task).coerceAtMost(available),
                            category = "TASK",
                            taskId = task.id
                        )
                    } else null
                }
                if (block == null) {
                    if (
                        addBreaksAutomatically &&
                        available >= BREAK_MIN_GAP_MINUTES &&
                        focusMinutesBefore(blocks, gap.startMinute) + placedMinutes >= focusStretchMinutes
                    ) {
                        proposed += GapFillBlock(
                            title = "Recovery break",
                            startMinute = cursor,
                            durationMinutes = breakDurationMinutes.coerceAtMost(available),
                            category = "RECOVERY"
                        )
                    }
                    break
                }
                proposed += block
                placedMinutes += block.durationMinutes
                cursor = block.startMinute + block.durationMinutes
            }
        }
        return GapFillProposal(gapCount = gaps.size, proposedBlocks = proposed)
    }

    private fun habitBlockFor(
        habit: GapFillHabitCandidate,
        cursor: Int,
        gapEnd: Int
    ): GapFillBlock? {
        val start = maxOf(cursor, habit.windowStartMinute)
        val duration = minOf(habit.windowEndMinute, gapEnd, start + MAX_HABIT_MINUTES) - start
        if (duration < MIN_PLACEMENT_MINUTES) return null
        return GapFillBlock(
            title = habit.title,
            startMinute = start,
            durationMinutes = duration,
            category = "HABIT",
            habitId = habit.habitId
        )
    }

    private companion object {
        const val MIN_GAP_MINUTES = 45
        const val MIN_PLACEMENT_MINUTES = 15
        const val MAX_HABIT_MINUTES = 60
        const val BREAK_MIN_GAP_MINUTES = 30
        const val BREAK_DURATION_MINUTES = 20
        const val FOCUS_STRETCH_MINUTES = 90
        const val DEPLETED_BREAK_DURATION_MINUTES = 25
        const val DEPLETED_FOCUS_STRETCH_MINUTES = 60
    }
}

/** Splits gaps around the sleep window so waking stretches next to sleep stay fillable. */
internal fun excludeSleepMinutes(
    gaps: List<FreeTimeSegment>,
    sleepSchedule: SleepSchedule
): List<FreeTimeSegment> {
    if (!sleepSchedule.isActive) return gaps
    val result = mutableListOf<FreeTimeSegment>()
    gaps.forEach { gap ->
        var start: Int? = null
        for (minute in gap.startMinute until gap.endMinute) {
            val sleeping = sleepSchedule.contains(minute)
            if (!sleeping && start == null) {
                start = minute
            }
            if ((sleeping || minute == gap.endMinute - 1) && start != null) {
                val end = if (sleeping) minute else minute + 1
                if (end > start) result += FreeTimeSegment(start, end)
                start = null
            }
        }
    }
    return result
}

internal fun clipGapToNow(gap: FreeTimeSegment, nowMinuteOfDay: Int?): FreeTimeSegment? {
    if (nowMinuteOfDay == null) return gap
    if (gap.endMinute <= nowMinuteOfDay) return null
    return FreeTimeSegment(maxOf(gap.startMinute, nowMinuteOfDay), gap.endMinute)
}

/** Same sizing as ScheduleTaskIntoDayUseCase so converted tasks land at familiar lengths. */
internal fun taskDurationFor(task: Task): Int =
    (task.preferredDurationMinutes ?: durationForTaskPriority(task.priority)).coerceIn(15, 180)

internal fun durationForTaskPriority(priority: Int): Int = when {
    priority >= 2 -> 60
    priority == 1 -> 45
    else -> 30
}

/** Minutes of focus-type work in the contiguous run of blocks ending exactly at [minute]. */
internal fun focusMinutesBefore(blocks: List<TimeBlock>, minute: Int): Int {
    var boundary = minute
    var focusMinutes = 0
    while (true) {
        val block = blocks.firstOrNull { it.startMinuteOfDay + it.durationMinutes == boundary } ?: break
        if (block.category.uppercase() !in FOCUS_CATEGORIES) break
        focusMinutes += block.durationMinutes
        boundary = block.startMinuteOfDay
    }
    return focusMinutes
}

private val FOCUS_CATEGORIES = setOf("WORK", "STUDY", "FOCUS", "TASK", "DEEP WORK")
