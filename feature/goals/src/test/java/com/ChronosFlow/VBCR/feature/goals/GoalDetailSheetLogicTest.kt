package com.ChronosFlow.VBCR.feature.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalDetailSheetLogicTest {

    @Test
    fun `cadence label is title-cased from a raw token`() {
        assertEquals("Daily", cadenceLabel("DAILY"))
        assertEquals("Weekly", cadenceLabel("weekly"))
    }

    @Test
    fun `blank cadence falls back to a generic label`() {
        assertEquals("Habit", cadenceLabel("   "))
    }
}
