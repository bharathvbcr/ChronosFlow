package com.chronosflow.feature.tasks

import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskAssistantSummaryTest {
    @Test
    fun `urgent unscheduled tasks produce triage guidance`() {
        val summary = buildTaskAssistantSummary(
            listOf(
                Task(
                    id = "task-1",
                    title = "Prepare launch brief",
                    description = null,
                    isCompleted = false,
                    priority = 2,
                    dueDate = null,
                    createdAt = Instant.parse("2026-05-25T09:00:00Z"),
                    updatedAt = Instant.parse("2026-05-25T09:00:00Z"),
                    preferredDurationMinutes = null,
                    preferredStartMinuteOfDay = null,
                    targetDate = null
                ),
                Task(
                    id = "task-2",
                    title = "Reply to reviewer",
                    description = null,
                    isCompleted = false,
                    priority = 1,
                    dueDate = null,
                    createdAt = Instant.parse("2026-05-25T10:00:00Z"),
                    updatedAt = Instant.parse("2026-05-25T10:00:00Z"),
                    preferredDurationMinutes = 45,
                    preferredStartMinuteOfDay = 9 * 60,
                    targetDate = LocalDate.of(2026, 5, 25)
                )
            )
        )

        assertTrue(summary.headline.contains("urgent", ignoreCase = true))
        assertTrue(summary.nextStep.contains("schedule", ignoreCase = true))
    }

    @Test
    fun `scheduled urgent tasks are treated as protected time`() {
        val urgentTask = task(
            id = "task-1",
            title = "Prepare launch brief",
            priority = 2
        )

        val summary = buildTaskAssistantSummary(
            tasks = listOf(urgentTask),
            taskSchedulesByTaskId = mapOf(urgentTask.id to schedule(urgentTask.id))
        )

        assertEquals(
            "Urgent tasks are captured and ready for DayDial placement.",
            summary.headline
        )
        assertEquals(
            "Review the protected urgent work before pulling in lower-priority tasks.",
            summary.nextStep
        )
    }

    private fun task(
        id: String,
        title: String,
        priority: Int = 1
    ): Task {
        return Task(
            id = id,
            title = title,
            description = null,
            isCompleted = false,
            priority = priority,
            dueDate = null,
            createdAt = Instant.parse("2026-05-25T09:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T09:00:00Z"),
            preferredDurationMinutes = null,
            preferredStartMinuteOfDay = null,
            targetDate = null
        )
    }

    private fun schedule(taskId: String): TaskSchedule {
        val now = Instant.parse("2026-05-25T09:00:00Z")
        return TaskSchedule(
            id = "schedule-1",
            taskId = taskId,
            recurrenceRule = TaskRecurrenceRule.Daily(
                startsOn = LocalDate.parse("2026-05-25")
            ),
            nextOccurrenceDate = LocalDate.parse("2026-05-25"),
            createdAt = now,
            updatedAt = now
        )
    }
}
