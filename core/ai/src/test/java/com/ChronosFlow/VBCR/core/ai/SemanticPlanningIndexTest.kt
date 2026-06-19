package com.ChronosFlow.VBCR.core.ai

import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.Habit
import com.ChronosFlow.VBCR.core.domain.model.MedicationPlan
import com.ChronosFlow.VBCR.core.domain.model.Task
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SemanticPlanningIndexTest {
    private val index = SemanticPlanningIndex()

    @Test
    fun `finds review entries by lexical query`() {
        index.rebuild(
            reviews = listOf(
                DailyReviewSummary(
                    date = LocalDate.of(2026, 5, 20),
                    plannedMinutes = 480,
                    actualMinutes = 300,
                    missedMinutes = 60,
                    driftMinutes = 30,
                    completedBlockCount = 4,
                    missedBlockCount = 1,
                    insights = emptyList()
                )
            ),
            blocks = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            medications = emptyList(),
            focusSessions = emptyList()
        )
        val hits = index.query("drift review")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().provenance == "review")
    }

    @Test
    fun `finds task entries by lexical query`() {
        index.rebuild(
            reviews = emptyList(),
            blocks = emptyList(),
            tasks = listOf(
                Task(
                    id = "task-1",
                    title = "Prepare launch brief",
                    description = "Write the assistant-first rollout summary",
                    isCompleted = false,
                    priority = 2,
                    dueDate = null,
                    createdAt = Instant.parse("2026-05-20T09:00:00Z"),
                    updatedAt = Instant.parse("2026-05-20T09:00:00Z")
                )
            ),
            habits = emptyList(),
            medications = emptyList(),
            focusSessions = emptyList()
        )

        val hits = index.query("launch brief")

        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().provenance == "task")
    }

    @Test
    fun `redacts medication names in index text`() {
        index.rebuild(
            reviews = emptyList(),
            blocks = emptyList(),
            tasks = emptyList(),
            habits = emptyList(),
            medications = listOf(
                MedicationPlan(
                    id = "med-1",
                    name = "SecretMed",
                    dosage = "10",
                    unit = "mg",
                    notes = null,
                    startAt = null,
                    endAt = null,
                    reminderMinuteOfDay = 480,
                    takeWithFood = false,
                    missedCount = 1,
                    refillNeededAfterDoses = null,
                    isActive = true
                )
            ),
            focusSessions = emptyList(),
            redactMedicationNames = true
        )
        val hits = index.query("SecretMed")
        assertTrue(hits.isEmpty())
    }
}
