package com.chronosflow.feature.daydial.model

import com.chronosflow.core.domain.model.Habit
import com.chronosflow.core.domain.model.AppLaunchTarget
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationDoseEventType
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class DayQuickItemsStateTest {
    @Test
    fun `quick items include selected date tasks habits and medication doses`() {
        val date = LocalDate.of(2026, 5, 27)
        val state = buildDayQuickItemsState(
            selectedDate = date,
            tasks = listOf(
                task(id = "task-today", title = "Submit report", targetDate = date),
                task(id = "task-future", title = "Future task", targetDate = date.plusDays(1)),
                task(id = "task-recurring", title = "Recurring standup")
            ),
            taskSchedules = mapOf(
                "task-recurring" to taskSchedule(taskId = "task-recurring", nextOccurrenceDate = date)
            ),
            habits = listOf(
                habit(id = "habit-1", title = "Stretch", lastCompletedDate = null),
                habit(id = "habit-2", title = "Journal", lastCompletedDate = date)
            ),
            medications = listOf(
                medicationPlan(
                    id = "med-1",
                    name = "Vitamin D",
                    doseMinutes = listOf(8 * 60, 20 * 60),
                    recentDoseEvents = listOf(
                        medicationEvent("taken-1", "med-1", MedicationDoseEventType.TAKEN, date, 8 * 60)
                    )
                )
            )
        )

        assertEquals(listOf("Submit report", "Recurring standup"), state.tasks.map { it.title })
        assertEquals(listOf("Stretch", "Journal"), state.habits.map { it.title })
        assertEquals(listOf("Vitamin D", "Vitamin D"), state.medications.map { it.title })
        assertEquals(listOf(8 * 60, 20 * 60), state.medications.map { it.scheduledMinuteOfDay })
        assertFalse(state.tasks.first().isDone)
        assertTrue(state.habits.last().isDone)
        assertTrue(state.medications.first().isDone)
        assertEquals("Due 8:00 PM", state.medications.last().status)
    }

    @Test
    fun `quick item action labels name the concrete item`() {
        val item = DayQuickItemUiModel(
            id = "habit-1",
            kind = DayQuickItemKind.HABIT,
            title = "Stretch",
            detail = "7:00 AM - 7:15 AM",
            status = "Due",
            isDone = false
        )

        assertEquals("Complete Stretch", dayQuickPrimaryActionLabel(item))
        assertEquals("Mark Stretch missed", dayQuickSecondaryActionLabel(item.copy(kind = DayQuickItemKind.MEDICATION)))
    }

    @Test
    fun `quick items surface task and habit contextual actions`() {
        val date = LocalDate.of(2026, 5, 27)
        val state = buildDayQuickItemsState(
            selectedDate = date,
            tasks = listOf(
                task(
                    id = "task-link",
                    title = "Submit report",
                    targetDate = date,
                    actions = listOf(
                        TaskAction(
                            id = "action-link",
                            type = TaskActionType.WEBSITE,
                            label = "Launch doc",
                            value = "https://example.com/brief",
                            isPrimary = true
                        )
                    )
                )
            ),
            habits = listOf(
                habit(
                    id = "habit-journal",
                    title = "Journal",
                    lastCompletedDate = null,
                    launchTarget = AppLaunchTarget(label = "Journal", value = "package:com.example.journal")
                )
            ),
            medications = emptyList()
        )

        val taskAction = state.tasks.single().contextAction
        val habitAction = state.habits.single().contextAction

        assertEquals("Visit link", taskAction?.label)
        assertEquals("Visit link for Submit report: Launch doc", dayQuickContextActionLabel(state.tasks.single()))
        assertEquals("Open Journal", habitAction?.label)
        assertEquals("Open Journal for Journal", dayQuickContextActionLabel(state.habits.single()))
    }

    @Test
    fun `quick item group labels summarize status and view all destination`() {
        val due = DayQuickItemUiModel(
            id = "task-1",
            kind = DayQuickItemKind.TASK,
            title = "Submit report",
            detail = "Around 9:00 AM",
            status = "Due today",
            isDone = false
        )
        val done = due.copy(id = "task-2", title = "Pay bills", status = "Done", isDone = true)

        assertEquals("1 due - 1 done", dayQuickGroupSummaryLabel(listOf(due, done)))
        assertEquals("2 due", dayQuickGroupSummaryLabel(listOf(due, due.copy(id = "task-3"))))
        assertEquals("View all tasks", dayQuickGroupOpenActionLabel(DayQuickItemKind.TASK))
        assertEquals("View all habits", dayQuickGroupOpenActionLabel(DayQuickItemKind.HABIT))
        assertEquals("View all meds", dayQuickGroupOpenActionLabel(DayQuickItemKind.MEDICATION))
    }

    @Test
    fun `empty quick item action labels keep destination shortcuts available`() {
        assertEquals("Open Tasks", dayQuickEmptyActionLabel(DayQuickItemKind.TASK))
        assertEquals("Open Habits", dayQuickEmptyActionLabel(DayQuickItemKind.HABIT))
        assertEquals("Open Meds", dayQuickEmptyActionLabel(DayQuickItemKind.MEDICATION))
    }

    private fun task(
        id: String,
        title: String,
        targetDate: LocalDate? = null,
        completed: Boolean = false,
        actions: List<TaskAction> = emptyList()
    ): Task {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        return Task(
            id = id,
            title = title,
            description = null,
            isCompleted = completed,
            priority = 1,
            dueDate = null,
            createdAt = now,
            updatedAt = now,
            preferredDurationMinutes = 30,
            preferredStartMinuteOfDay = 9 * 60,
            targetDate = targetDate,
            actions = actions
        )
    }

    private fun taskSchedule(taskId: String, nextOccurrenceDate: LocalDate): TaskSchedule {
        val now = Instant.parse("2026-05-27T12:00:00Z")
        return TaskSchedule(
            id = "schedule-$taskId",
            taskId = taskId,
            recurrenceRule = TaskRecurrenceRule.Daily(intervalDays = 1, startsOn = nextOccurrenceDate),
            nextOccurrenceDate = nextOccurrenceDate,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun habit(
        id: String,
        title: String,
        lastCompletedDate: LocalDate?,
        launchTarget: AppLaunchTarget? = null
    ): Habit = Habit(
        id = id,
        title = title,
        cadence = "Daily",
        windowStartMinute = 7 * 60,
        windowEndMinute = 7 * 60 + 15,
        difficulty = 1,
        isBundled = true,
        streakCount = 2,
        lastCompletedDate = lastCompletedDate,
        isActive = true,
        launchTarget = launchTarget
    )

    private fun medicationPlan(
        id: String,
        name: String,
        doseMinutes: List<Int>,
        recentDoseEvents: List<MedicationDoseEvent>
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        endAt = null,
        reminderMinuteOfDay = doseMinutes.first(),
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = true,
        schedule = MedicationSchedule(
            id = "schedule-$id",
            medicationPlanId = id,
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.MULTIPLE_TIMES_DAILY,
                timesOfDayMinutes = doseMinutes
            )
        ),
        recentDoseEvents = recentDoseEvents
    )

    private fun medicationEvent(
        id: String,
        medicationPlanId: String,
        type: MedicationDoseEventType,
        date: LocalDate,
        scheduledMinute: Int
    ): MedicationDoseEvent = MedicationDoseEvent(
        id = id,
        medicationPlanId = medicationPlanId,
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-05-27T12:00:00Z"),
        scheduledMinuteOfDay = scheduledMinute,
        reason = null,
        doseAmount = "1"
    )
}
