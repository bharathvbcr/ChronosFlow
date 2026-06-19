package com.ChronosFlow.VBCR.feature.goals

import com.ChronosFlow.VBCR.core.domain.model.Goal
import com.ChronosFlow.VBCR.core.domain.model.GoalWithProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoalOverdueLogicTest {

    private val today = LocalDate.of(2026, 6, 13)

    private fun entry(
        id: String,
        targetDate: LocalDate?,
        isCompleted: Boolean = false
    ) = GoalWithProgress(
        Goal(
            id = id,
            title = "Goal $id",
            description = null,
            category = "Personal",
            targetValue = 10,
            startDate = LocalDate.of(2026, 1, 1),
            targetDate = targetDate,
            progressValue = 0,
            isCompleted = isCompleted
        )
    )

    @Test
    fun `overdue count includes only active goals past their target date`() {
        val goals = listOf(
            entry("past-active", today.minusDays(1)),                 // counts
            entry("due-today", today),                                // not before today
            entry("future", today.plusDays(5)),                      // not overdue
            entry("no-date", null),                                  // no target date
            entry("past-completed", today.minusDays(3), isCompleted = true) // completed, excluded
        )

        assertEquals(1, goalsOverdueCount(goals, today))
    }

    @Test
    fun `overdue message is singular for one and plural otherwise`() {
        assertEquals("1 active goal is past its target date.", goalsOverdueMessage(1))
        assertEquals("3 active goals are past their target date.", goalsOverdueMessage(3))
    }
}
