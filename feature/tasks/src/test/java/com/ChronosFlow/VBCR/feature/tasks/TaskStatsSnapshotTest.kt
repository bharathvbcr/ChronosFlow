package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskStatsSnapshotTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-06-13T12:00:00Z")

    @Test
    fun `counts totals done and open priority buckets`() {
        val tasks = listOf(
            task(id = "1", isCompleted = true, priority = 1),
            task(id = "2", isCompleted = false, priority = 0),
            task(id = "3", isCompleted = false, priority = 1),
            task(id = "4", isCompleted = false, priority = 2)
        )

        val stats = buildTaskStatsSnapshot(tasks, now, zone)

        assertEquals(4, stats.total)
        assertEquals(1, stats.done)
        assertEquals(3, stats.openCount)
        assertEquals(1, stats.openLow)
        assertEquals(1, stats.openMedium)
        assertEquals(1, stats.openHigh)
    }

    @Test
    fun `completed this week counts only completions updated within seven days`() {
        val tasks = listOf(
            task(id = "recent", isCompleted = true, updatedAt = now.minusSeconds(2L * 24 * 60 * 60)),
            task(id = "old", isCompleted = true, updatedAt = now.minusSeconds(10L * 24 * 60 * 60)),
            task(id = "open", isCompleted = false, updatedAt = now)
        )

        val stats = buildTaskStatsSnapshot(tasks, now, zone)

        assertEquals(1, stats.completedThisWeek)
    }

    @Test
    fun `due today and overdue are derived from open task due dates`() {
        val tasks = listOf(
            task(id = "today", isCompleted = false, dueDate = Instant.parse("2026-06-13T18:00:00Z")),
            task(id = "overdue", isCompleted = false, dueDate = Instant.parse("2026-06-10T09:00:00Z")),
            task(id = "future", isCompleted = false, dueDate = Instant.parse("2026-06-20T09:00:00Z")),
            // A completed overdue task should not be counted.
            task(id = "done", isCompleted = true, dueDate = Instant.parse("2026-06-01T09:00:00Z"))
        )

        val stats = buildTaskStatsSnapshot(tasks, now, zone)

        assertEquals(1, stats.dueToday)
        assertEquals(1, stats.overdue)
    }

    @Test
    fun `completion rate is zero for an empty list`() {
        val stats = buildTaskStatsSnapshot(emptyList(), now, zone)

        assertEquals(0, stats.total)
        assertEquals(0f, stats.completionRate, 0f)
    }

    @Test
    fun `headline celebrates when all work is done`() {
        val stats = buildTaskStatsSnapshot(
            listOf(task(id = "1", isCompleted = true)),
            now,
            zone
        )

        assertEquals("All clear — every task is done!", taskStatsHeadline(stats))
    }

    @Test
    fun `completion by day buckets done tasks by updatedAt oldest first`() {
        val tasks = listOf(
            task(id = "a", isCompleted = true, updatedAt = Instant.parse("2026-06-13T08:00:00Z")),
            task(id = "b", isCompleted = true, updatedAt = Instant.parse("2026-06-13T20:00:00Z")),
            task(id = "c", isCompleted = true, updatedAt = Instant.parse("2026-06-11T10:00:00Z")),
            task(id = "open", isCompleted = false, updatedAt = Instant.parse("2026-06-13T10:00:00Z")),
            task(id = "old", isCompleted = true, updatedAt = Instant.parse("2026-06-01T10:00:00Z"))
        )

        val trend = taskCompletionByDay(tasks, today = LocalDate.parse("2026-06-13"), zoneId = zone, days = 7)

        // Window is Jun 7..Jun 13. Jun 11 has 1, Jun 13 has 2, the rest 0; Jun 1 is outside.
        assertEquals(listOf(0, 0, 0, 0, 1, 0, 2), trend)
    }

    @Test
    fun `completion by day with zero window is empty`() {
        assertEquals(emptyList<Int>(), taskCompletionByDay(emptyList(), now.atZone(zone).toLocalDate(), zone, days = 0))
    }

    private fun task(
        id: String,
        isCompleted: Boolean = false,
        priority: Int = 0,
        dueDate: Instant? = null,
        updatedAt: Instant = Instant.parse("2026-06-13T09:00:00Z")
    ): Task = Task(
        id = id,
        title = "Task $id",
        description = null,
        isCompleted = isCompleted,
        priority = priority,
        dueDate = dueDate,
        createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        updatedAt = updatedAt
    )
}
