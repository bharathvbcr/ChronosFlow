package com.ChronosFlow.VBCR.core.notifications

import com.ChronosFlow.VBCR.core.domain.model.MedicationAnalytics
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.MedicationSchedule
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrence
import com.ChronosFlow.VBCR.core.domain.model.PlannerRecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FoldedReminderTest {
    @Test
    fun `medication fold uses latest passed reminder minute from multi-dose schedule`() {
        val plan = medicationPlan(
            id = "med-1",
            schedule = MedicationSchedule(
                id = "sched-1",
                medicationPlanId = "med-1",
                recurrence = PlannerRecurrence(
                    type = PlannerRecurrenceType.MULTIPLE_TIMES_DAILY,
                    timesOfDayMinutes = listOf(8 * 60, 14 * 60, 20 * 60)
                )
            )
        )
        val folded = buildMedicationFoldedReminders(
            plans = listOf(plan),
            today = LocalDate.of(2026, 7, 3),
            nowMinute = 15 * 60
        )
        assertEquals(1, folded.size)
        assertEquals(14 * 60, folded.first().dueMinute)
        assertEquals("med-1", folded.first().entityId)
    }

    @Test
    fun `paused medication plan is excluded from fold`() {
        val plan = medicationPlan(
            id = "med-paused",
            schedule = MedicationSchedule(
                id = "sched-p",
                medicationPlanId = "med-paused",
                pausedUntil = LocalDate.of(2026, 7, 10)
            )
        )
        val folded = buildMedicationFoldedReminders(
            plans = listOf(plan),
            today = LocalDate.of(2026, 7, 3),
            nowMinute = 10 * 60
        )
        assertTrue(folded.isEmpty())
    }

    @Test
    fun `taken dose at scheduled minute is excluded but next dose still folds`() {
        val today = LocalDate.of(2026, 7, 3)
        val plan = medicationPlan(
            id = "med-multi",
            schedule = MedicationSchedule(
                id = "sched-m",
                medicationPlanId = "med-multi",
                recurrence = PlannerRecurrence(
                    type = PlannerRecurrenceType.MULTIPLE_TIMES_DAILY,
                    timesOfDayMinutes = listOf(8 * 60, 14 * 60)
                )
            ),
            events = listOf(
                doseEvent(
                    planId = "med-multi",
                    date = today,
                    minute = 8 * 60,
                    type = MedicationDoseEventType.TAKEN
                )
            )
        )
        val folded = buildMedicationFoldedReminders(
            plans = listOf(plan),
            today = today,
            nowMinute = 15 * 60
        )
        assertEquals(1, folded.size)
        assertEquals(14 * 60, folded.first().dueMinute)
    }

    @Test
    fun `ranking prefers medication over task then habit`() {
        val candidates = listOf(
            FoldedReminder(FoldedReminderKind.HABIT, "h1", "Habit", "Window open", 9 * 60, false),
            FoldedReminder(FoldedReminderKind.TASK, "t1", "Task", "Due 9:00 AM", 9 * 60, false),
            FoldedReminder(FoldedReminderKind.MEDICATION, "m1", "Med", "Due 9:00 AM", 9 * 60, false)
        )
        val ranked = rankFoldedReminders(candidates)
        assertEquals(FoldedReminderKind.MEDICATION, ranked[0].kind)
        assertEquals(FoldedReminderKind.TASK, ranked[1].kind)
        assertEquals(FoldedReminderKind.HABIT, ranked[2].kind)
    }

    @Test
    fun `isDoseTaken matches scheduled minute only`() {
        val today = LocalDate.of(2026, 7, 3)
        val plan = medicationPlan(
            id = "med-dose",
            events = listOf(
                doseEvent(planId = "med-dose", date = today, minute = 8 * 60, type = MedicationDoseEventType.TAKEN)
            )
        )
        assertTrue(plan.isDoseTaken(today, 8 * 60))
        assertFalse(plan.isDoseTaken(today, 14 * 60))
    }

    private val foldDay = LocalDate.of(2026, 7, 3)

    private fun duePlan(id: String) = medicationPlan(
        id = id,
        schedule = MedicationSchedule(
            id = "sched-$id",
            medicationPlanId = id,
            recurrence = PlannerRecurrence(
                type = PlannerRecurrenceType.MULTIPLE_TIMES_DAILY,
                timesOfDayMinutes = listOf(8 * 60)
            )
        )
    )

    @Test
    fun `a dose inside its snooze window is hidden from the fold`() {
        // Snoozed at 8:10 → chip may return at 8:25; at 8:20 it must stay hidden.
        val folded = buildMedicationFoldedReminders(
            plans = listOf(duePlan("med-s")),
            today = foldDay,
            nowMinute = 8 * 60 + 20,
            snoozedBackMinuteByPlanId = mapOf("med-s" to 8 * 60 + 25)
        )
        assertTrue(folded.isEmpty())
    }

    @Test
    fun `a snoozed dose returns to the fold once the window elapses`() {
        val folded = buildMedicationFoldedReminders(
            plans = listOf(duePlan("med-s")),
            today = foldDay,
            nowMinute = 8 * 60 + 30,
            snoozedBackMinuteByPlanId = mapOf("med-s" to 8 * 60 + 25)
        )
        assertEquals(1, folded.size)
        assertEquals(8 * 60, folded.first().dueMinute)
    }

    @Test
    fun `snooze suppression is scoped to the snoozed plan only`() {
        val folded = buildMedicationFoldedReminders(
            plans = listOf(duePlan("med-a"), duePlan("med-b")),
            today = foldDay,
            nowMinute = 8 * 60 + 10,
            snoozedBackMinuteByPlanId = mapOf("med-a" to 8 * 60 + 25)
        )
        assertEquals(listOf("med-b"), folded.map { it.entityId })
    }

    @Test
    fun `shouldSuppressFoldedReminderBanner matches folded medication entity`() {
        val folded = setOf(FoldedEntityKey(FoldedReminderKind.MEDICATION, "med-1"))
        assertTrue(
            shouldSuppressFoldedReminderBanner(
                folded = folded,
                medicationPlanId = "med-1",
                taskId = null,
                habitId = null
            )
        )
        assertFalse(
            shouldSuppressFoldedReminderBanner(
                folded = folded,
                medicationPlanId = "med-2",
                taskId = null,
                habitId = null
            )
        )
    }

    @Test
    fun `shouldSuppressFoldedReminderBanner ignores empty fold set`() {
        assertFalse(
            shouldSuppressFoldedReminderBanner(
                folded = emptySet(),
                medicationPlanId = "med-1",
                taskId = "task-1",
                habitId = "habit-1"
            )
        )
    }

    private fun medicationPlan(
        id: String,
        schedule: MedicationSchedule? = null,
        events: List<MedicationDoseEvent> = emptyList()
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = id,
        dosage = "500",
        unit = "mg",
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = 8 * 60,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = true,
        schedule = schedule,
        recentDoseEvents = events,
        analytics = MedicationAnalytics()
    )

    private fun doseEvent(
        planId: String,
        date: LocalDate,
        minute: Int,
        type: MedicationDoseEventType
    ): MedicationDoseEvent = MedicationDoseEvent(
        id = "$planId-$minute",
        medicationPlanId = planId,
        type = type,
        eventDate = date,
        recordedAt = Instant.parse("2026-07-03T12:00:00Z"),
        scheduledMinuteOfDay = minute,
        reason = null,
        doseAmount = null
    )
}
