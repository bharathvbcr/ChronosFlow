package com.ChronosFlow.VBCR.feature.daydial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAwareGoalsTest {

    @Test
    fun `appends the protect-focus hint when distraction runs above usual`() {
        val result = focusAwareGoals(listOf("balanced day"), distractionAboveUsual = true)

        assertEquals(2, result.size)
        assertEquals("balanced day", result.first())
        assertTrue(result.last().contains("protect focus", ignoreCase = true))
        assertTrue(result.last().contains("deep-work", ignoreCase = true))
    }

    @Test
    fun `leaves goals unchanged when distraction is normal`() {
        val goals = listOf("balanced day")

        assertEquals(goals, focusAwareGoals(goals, distractionAboveUsual = false))
    }

    @Test
    fun `adds the hint even when no goals were provided`() {
        val result = focusAwareGoals(emptyList(), distractionAboveUsual = true)

        assertEquals(listOf(FOCUS_PROTECT_GOAL_HINT), result)
    }
}
