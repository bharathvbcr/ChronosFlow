package com.ChronosFlow.VBCR.core.ai

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationQuickActionTest {
    @Test
    fun `inferQuickAction routes break and buffer text to add break`() {
        val planner = RecommendationPlanInterpreter(mockk(relaxed = true))

        assertEquals(RecommendationQuickAction.ADD_BREAK, planner.inferQuickAction("Add recovery break"))
        assertEquals(RecommendationQuickAction.ADD_BREAK, planner.inferQuickAction("15m buffers around meetings"))
    }
}
