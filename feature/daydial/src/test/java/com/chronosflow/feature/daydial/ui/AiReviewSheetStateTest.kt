package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class AiReviewSheetStateTest {
    @Test
    fun `ai review action labels name target suggestion and count`() {
        val suggestion = TimeBlockUiModel(
            id = "focus",
            title = "Focus writing",
            startMinuteOfDay = 9 * 60,
            durationMinutes = 90,
            color = Color(0xFF6750A4)
        )

        assertEquals("Dismiss 1 AI suggestion", aiReviewDismissAllActionLabel(1))
        assertEquals("Dismiss 3 AI suggestions", aiReviewDismissAllActionLabel(3))
        assertEquals("Apply 1 AI suggestion to today's plan", aiReviewApplyAllActionLabel(1))
        assertEquals("Apply 3 AI suggestions to today's plan", aiReviewApplyAllActionLabel(3))
        assertEquals(
            "Reject Focus writing suggestion from 9:00 AM for 90 minutes",
            aiReviewRejectActionLabel(suggestion)
        )
        assertEquals(
            "Modify Focus writing suggestion from 9:00 AM for 90 minutes",
            aiReviewModifyActionLabel(suggestion)
        )
        assertEquals(
            "Accept Focus writing suggestion from 9:00 AM for 90 minutes",
            aiReviewAcceptActionLabel(suggestion)
        )
    }
}
