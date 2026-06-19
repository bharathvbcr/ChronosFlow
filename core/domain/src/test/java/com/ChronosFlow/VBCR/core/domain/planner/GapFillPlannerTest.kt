package com.ChronosFlow.VBCR.core.domain.planner

import com.ChronosFlow.VBCR.core.domain.model.SleepReadiness
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.planner.PlannerTestFixtures.timeBlock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GapFillPlannerTest {
    private val planner = GapFillPlanner(FreeTimeCalculator())
    private val sleep = SleepSchedule(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
    private val noSleep = SleepSchedule(enabled = false, startMinute = 0, endMinute = 0)

    private fun task(
        id: String,
        title: String = id,
        priority: Int = 0,
        dueDate: Instant? = null,
        preferredDurationMinutes: Int? = null,
        isCompleted: Boolean = false,
        createdAt: Instant = PlannerTestFixtures.now
    ) = Task(
        id = id,
        title = title,
        description = null,
        isCompleted = isCompleted,
        priority = priority,
        dueDate = dueDate,
        createdAt = createdAt,
        updatedAt = createdAt,
        preferredDurationMinutes = preferredDurationMinutes
    )

    @Test
    fun `fills multiple gaps with pending tasks ordered by due date then priority`() {
        val blocks = listOf(
            timeBlock(id = "morning", startMinute = 7 * 60, durationMinutes = 120),
            timeBlock(id = "midday", startMinute = 11 * 60, durationMinutes = 60),
            timeBlock(id = "evening", startMinute = 14 * 60, durationMinutes = 8 * 60)
        )
        val tasks = listOf(
            task("low", priority = 0),
            task("due-soon", priority = 0, dueDate = Instant.parse("2025-01-01T18:00:00Z")),
            task("high", priority = 2)
        )

        val proposal = planner.propose(
            blocks = blocks,
            tasks = tasks,
            habitCandidates = emptyList(),
            sleepSchedule = sleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )

        assertEquals(2, proposal.gapCount)
        val firstGapBlocks = proposal.proposedBlocks.filter { it.startMinute < 11 * 60 }
        assertEquals("due-soon", firstGapBlocks.first().title)
        assertTrue(proposal.taskCount >= 2)
        proposal.proposedBlocks.forEach { block ->
            assertTrue(block.startMinute >= 7 * 60)
            assertTrue(block.startMinute + block.durationMinutes <= 14 * 60)
        }
    }

    @Test
    fun `task duration honors preferred duration and is sized down to fit the gap`() {
        val blocks = listOf(
            timeBlock(id = "before", startMinute = 0, durationMinutes = 10 * 60),
            timeBlock(id = "after", startMinute = 11 * 60, durationMinutes = 13 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("big", preferredDurationMinutes = 180)),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )

        val placed = proposal.proposedBlocks.first { it.taskId == "big" }
        assertEquals(60, placed.durationMinutes)
    }

    @Test
    fun `tasks already scheduled today are not proposed again`() {
        val blocks = listOf(
            timeBlock(id = "linked", startMinute = 9 * 60, durationMinutes = 60).copy(taskId = "done-task"),
            timeBlock(id = "wall", startMinute = 12 * 60, durationMinutes = 11 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("done-task")),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )

        assertTrue(proposal.proposedBlocks.none { it.taskId == "done-task" })
    }

    @Test
    fun `habit lands inside its window before tasks take the slot`() {
        val blocks = listOf(
            timeBlock(id = "before", startMinute = 8 * 60, durationMinutes = 60),
            timeBlock(id = "after", startMinute = 12 * 60, durationMinutes = 10 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("task")),
            habitCandidates = listOf(
                GapFillHabitCandidate(
                    habitId = "stretch",
                    title = "Stretch",
                    windowStartMinute = 9 * 60,
                    windowEndMinute = 10 * 60
                )
            ),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )

        val habitBlock = proposal.proposedBlocks.first { it.habitId == "stretch" }
        assertEquals(9 * 60, habitBlock.startMinute)
        assertEquals(60, habitBlock.durationMinutes)
        assertEquals("HABIT", habitBlock.category)
    }

    @Test
    fun `today only fills time after now`() {
        val blocks = listOf(
            timeBlock(id = "evening", startMinute = 18 * 60, durationMinutes = 4 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("late")),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = 15 * 60,
            addBreaksAutomatically = false
        )

        proposal.proposedBlocks.forEach { assertTrue(it.startMinute >= 15 * 60) }
    }

    @Test
    fun `break appears only after a focus-heavy stretch with no candidates left`() {
        val blocks = listOf(
            timeBlock(id = "deep-1", startMinute = 8 * 60, durationMinutes = 60),
            timeBlock(id = "deep-2", startMinute = 9 * 60, durationMinutes = 60),
            timeBlock(id = "wall", startMinute = 13 * 60, durationMinutes = 11 * 60)
        )

        val withBreaks = planner.propose(
            blocks = blocks,
            tasks = emptyList(),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = true
        )
        val recovery = withBreaks.proposedBlocks.filter { it.category == "RECOVERY" }
        assertEquals(1, recovery.size)
        assertEquals(10 * 60, recovery.single().startMinute)

        val withoutBreaks = planner.propose(
            blocks = blocks,
            tasks = emptyList(),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )
        assertTrue(withoutBreaks.proposedBlocks.isEmpty())
    }

    @Test
    fun `a depleted night inserts a recovery break after a shorter focus stretch`() {
        // A single 60-minute focus block: below the normal 90-minute stretch, above the depleted 60.
        val blocks = listOf(
            timeBlock(id = "deep-1", startMinute = 8 * 60, durationMinutes = 60),
            timeBlock(id = "wall", startMinute = 13 * 60, durationMinutes = 11 * 60)
        )

        val normal = planner.propose(
            blocks = blocks,
            tasks = emptyList(),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = true
        )
        assertTrue(normal.proposedBlocks.none { it.category == "RECOVERY" })

        val depleted = planner.propose(
            blocks = blocks,
            tasks = emptyList(),
            habitCandidates = emptyList(),
            sleepSchedule = noSleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = true,
            readiness = SleepReadiness.DEPLETED
        )
        val recovery = depleted.proposedBlocks.filter { it.category == "RECOVERY" }
        assertEquals(1, recovery.size)
        assertEquals(9 * 60, recovery.single().startMinute)
        assertEquals(25, recovery.single().durationMinutes)
    }

    @Test
    fun `sleep window is excluded but the waking remainder of the gap still fills`() {
        val blocks = listOf(
            timeBlock(id = "midday", startMinute = 9 * 60, durationMinutes = 13 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("morning-task")),
            habitCandidates = emptyList(),
            sleepSchedule = sleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = false
        )

        val placed = proposal.proposedBlocks.first()
        assertEquals(7 * 60, placed.startMinute)
    }

    @Test
    fun `no qualifying gaps yields an empty proposal`() {
        val blocks = listOf(
            timeBlock(id = "all-day", startMinute = 7 * 60, durationMinutes = 15 * 60)
        )
        val proposal = planner.propose(
            blocks = blocks,
            tasks = listOf(task("task")),
            habitCandidates = emptyList(),
            sleepSchedule = sleep,
            nowMinuteOfDay = null,
            addBreaksAutomatically = true
        )

        assertEquals(0, proposal.gapCount)
        assertTrue(proposal.proposedBlocks.isEmpty())
    }
}
