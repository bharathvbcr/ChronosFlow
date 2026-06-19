package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.ai.genai.AssistGenAiSource
import com.ChronosFlow.VBCR.core.ai.genai.AssistTextGeneration
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCoordinator
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEvent
import com.ChronosFlow.VBCR.core.domain.model.MedicationDoseEventType
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationAdherenceAssistPlannerTest {
    private val today: LocalDate = LocalDate.of(2026, 6, 11)

    @Test
    fun `suggestAdjustments parses AI reminder minutes`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = "med-1|600|Anchor the reminder to breakfast",
            source = AssistGenAiSource.GEMINI_NANO
        )
        val planner = MedicationAdherenceAssistPlanner(coordinator)

        val results = planner.suggestAdjustments(listOf(missedTodayPlan()), today, currentMinute = 10 * 60)

        assertEquals(1, results.size)
        assertEquals(RoutineAssistSource.GEMINI_NANO, results.first().source)
        assertEquals(600, results.first().suggestedReminderMinute)
    }

    @Test
    fun `missed dose falls back to local catch-up suggestion`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAdherenceAssistPlanner(coordinator)

        val results = planner.suggestAdjustments(listOf(missedTodayPlan()), today, currentMinute = 10 * 60)

        assertEquals(RoutineAssistSource.LOCAL, results.first().source)
        assertTrue(results.first().reason.contains("passed without a logged dose"))
        assertEquals(10 * 60 + 15, results.first().suggestedReminderMinute)
    }

    @Test
    fun `late take pattern suggests the typical taken minute`() = runTest {
        val coordinator = mockk<GenAiAssistCoordinator>()
        coEvery { coordinator.generateAssistText(any()) } returns AssistTextGeneration(
            text = null,
            source = AssistGenAiSource.LOCAL
        )
        val planner = MedicationAdherenceAssistPlanner(coordinator)

        val results = planner.suggestAdjustments(listOf(lateTakePlan()), today, currentMinute = 9 * 60)

        assertEquals(1, results.size)
        assertEquals(570, results.first().suggestedReminderMinute)
        assertTrue(results.first().reason.contains("Usually taken around"))
    }

    @Test
    fun `acknowledged on-time plans produce no suggestions`() = runTest {
        // Strict mock with no stub: a coordinator call would fail the test,
        // proving the planner short-circuits before generating.
        val planner = MedicationAdherenceAssistPlanner(mockk())
        val plan = plan(
            events = listOf(takenEvent(today, minuteOfDay = 8 * 60 + 5, scheduled = 8 * 60))
        )

        val results = planner.suggestAdjustments(listOf(plan), today, currentMinute = 20 * 60)

        assertTrue(results.isEmpty())
    }

    private fun missedTodayPlan(): MedicationPlan = plan(events = emptyList())

    private fun lateTakePlan(): MedicationPlan = plan(
        events = listOf(
            takenEvent(today, minuteOfDay = 8 * 60 + 5, scheduled = 8 * 60),
            takenEvent(today.minusDays(1), minuteOfDay = 565, scheduled = 8 * 60),
            takenEvent(today.minusDays(2), minuteOfDay = 570, scheduled = 8 * 60),
            takenEvent(today.minusDays(3), minuteOfDay = 580, scheduled = 8 * 60)
        )
    )

    private fun plan(
        id: String = "med-1",
        reminderMinute: Int = 8 * 60,
        events: List<MedicationDoseEvent> = emptyList()
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = "Vitamin D",
        dosage = "1",
        unit = "tablet",
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = reminderMinute,
        takeWithFood = false,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = true,
        recentDoseEvents = events
    )

    private fun takenEvent(
        date: LocalDate,
        minuteOfDay: Int,
        scheduled: Int?
    ): MedicationDoseEvent = MedicationDoseEvent(
        id = "evt-$date-$minuteOfDay",
        medicationPlanId = "med-1",
        type = MedicationDoseEventType.TAKEN,
        eventDate = date,
        recordedAt = date.atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(ZoneId.systemDefault())
            .toInstant(),
        scheduledMinuteOfDay = scheduled,
        reason = null,
        doseAmount = null
    )
}
