package com.ChronosFlow.VBCR.core.ai.genai

import com.ChronosFlow.VBCR.core.ai.ProposedSuggestionBlock
import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.DailyReviewSummary
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsight
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightSeverity
import com.ChronosFlow.VBCR.core.domain.model.ReviewInsightType
import com.ChronosFlow.VBCR.core.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalPlanningHeuristicsTest {
    @Test
    fun `generateIdealDayPlan with default preferences`() {
        val result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName = "com.ChronosFlow.VBCR",
            userPreferences = "",
            date = LocalDate.of(2026, 5, 30),
            currentTimeZone = "UTC"
        )

        assertEquals(5, result.proposedBlocks.size)
        assertEquals("Morning planning", result.proposedBlocks[0].title)
        assertEquals(8 * 60, result.proposedBlocks[0].startMinuteOfDay)
    }

    @Test
    fun `generateIdealDayPlan with recovery preference`() {
        val result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName = "com.ChronosFlow.VBCR",
            userPreferences = "I am feeling very tired today, need recovery",
            date = LocalDate.of(2026, 5, 30),
            currentTimeZone = "UTC"
        )

        assertEquals("Slow start and planning", result.proposedBlocks[0].title)
        assertEquals(9 * 60, result.proposedBlocks[0].startMinuteOfDay)
        assertEquals(45, result.proposedBlocks[0].durationMinutes)
    }

    @Test
    fun `generateIdealDayPlan with study and exercise preferences`() {
        val result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName = "com.ChronosFlow.VBCR",
            userPreferences = "exam study and workout",
            date = LocalDate.of(2026, 5, 30),
            currentTimeZone = "UTC"
        )

        assertEquals("Deep study block", result.proposedBlocks[1].title)
        assertEquals("STUDY", result.proposedBlocks[1].category)
        assertEquals("Workout window", result.proposedBlocks[3].title)
        assertEquals("WORKOUT", result.proposedBlocks[3].category)
    }

    @Test
    fun `generateIdealDayPlan schedules a due-today task ahead of a higher-priority task due later`() {
        val date = LocalDate.of(2026, 5, 30)
        val zone = ZoneId.of("UTC")
        fun task(title: String, priority: Int, due: Instant?) = Task(
            id = title,
            title = title,
            description = null,
            isCompleted = false,
            priority = priority,
            dueDate = due,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val dueToday = task("Submit report", priority = 1, due = date.atTime(12, 0).atZone(zone).toInstant())
        val highPriorityLater = task("Plan offsite", priority = 9, due = date.plusDays(20).atStartOfDay(zone).toInstant())

        val result = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName = "com.ChronosFlow.VBCR",
            userPreferences = "",
            date = date,
            currentTimeZone = "UTC",
            pendingTasks = listOf(highPriorityLater, dueToday)
        )

        // The deep-work slot (block index 1) takes the urgent due-today task over the higher-priority one.
        assertEquals("Submit report", result.proposedBlocks[1].title)
    }

    @Test
    fun `generateReviewBackedDayPlan with missed tasks and low energy`() {
        val review = DailyReviewSummary(
            date = LocalDate.of(2026, 5, 29),
            plannedMinutes = 480,
            actualMinutes = 300,
            missedMinutes = 180,
            driftMinutes = 30,
            completedBlockCount = 3,
            missedBlockCount = 2,
            insights = listOf(
                ReviewInsight(
                    id = "i1",
                    type = ReviewInsightType.MISSED_BLOCK,
                    title = "Missed work",
                    detail = "You missed your deep work block.",
                    severity = ReviewInsightSeverity.WARNING
                )
            )
        )
        
        val baseline = LocalPlanningHeuristics.generateIdealDayPlan(
            "com.ChronosFlow.VBCR", "", LocalDate.of(2026, 5, 30), "UTC"
        )

        val result = LocalPlanningHeuristics.generateReviewBackedDayPlan(
            packageName = "com.ChronosFlow.VBCR",
            userPreferences = "None",
            review = review,
            existingBlocks = emptyList(),
            currentTimeZone = "UTC",
            baseline = baseline
        )

        assertNotNull(result)
        assertTrue(result.proposedBlocks.size >= baseline.proposedBlocks.size)
        assertTrue(result.explanation.contains("ChronosFlow used structured daily review data"))
    }

    @Test
    fun `repairDayPlan covers all conflict types`() {
        val currentPlan = "Gym at 7am, Work at 9am"
        
        val overlapResult = LocalPlanningHeuristics.repairDayPlan(currentPlan, "overlap collision")
        assertTrue("Should contain OVERLAP", overlapResult.uppercase().contains("OVERLAP"))
        
        val overloadResult = LocalPlanningHeuristics.repairDayPlan(currentPlan, "overbook full")
        assertTrue("Should contain OVERLOAD", overloadResult.uppercase().contains("OVERLOAD"))
        
        val missedResult = LocalPlanningHeuristics.repairDayPlan(currentPlan, "missed late drift")
        assertTrue("Should contain MISSED_WORK", missedResult.uppercase().contains("MISSED_WORK"))
        
        val deepWorkResult = LocalPlanningHeuristics.repairDayPlan(currentPlan, "deep work fragment")
        assertTrue("Should contain DEEP_WORK", deepWorkResult.uppercase().contains("DEEP_WORK"))
        
        val generalResult = LocalPlanningHeuristics.repairDayPlan(currentPlan, "unknown")
        assertTrue("Should contain GENERAL", generalResult.uppercase().contains("GENERAL"))
    }

    @Test
    fun `repairDayPlan with empty plan`() {
        val result = LocalPlanningHeuristics.repairDayPlan("", "Overlap.")
        assertTrue(result.contains("On-device repair plan"))
    }

    @Test
    fun `blocksToDomainTimeBlocks converts correctly`() {
        val suggestion = ProposedSuggestionBlock(
            id = "1",
            title = "Test",
            category = "WORK",
            startMinuteOfDay = 600,
            durationMinutes = 60,
            provenance = com.ChronosFlow.VBCR.core.domain.model.BlockProvenance.AI_SUGGESTED,
            flexibility = BlockFlexibility.MOVABLE,
            isLocked = false,
            isProtected = false,
            timezone = "UTC"
        )
        
        val result = LocalPlanningHeuristics.blocksToDomainTimeBlocks(listOf(suggestion), LocalDate.now())
        assertEquals(1, result.size)
        assertEquals("Test", result[0].title)
        assertEquals(600, result[0].startMinuteOfDay)
    }
}
