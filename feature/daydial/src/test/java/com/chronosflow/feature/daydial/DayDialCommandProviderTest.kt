package com.chronosflow.feature.daydial

import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialCommandProviderTest {
    @Test
    fun `day dial command provider includes review commands when review is enabled`() {
        val provider = dayDialCommandProvider(
            onOpenDayDial = { },
            onOpenPlan = { },
            onOpenFocusPlanner = { },
            onOpenInsights = { },
            onOpenPlanningTools = { },
            onOpenTemplates = { },
            onOpenReview = { },
            onOpenAiSettings = { },
            onOpenPrivacySync = { },
            onOpenNotificationSettings = { },
            onOpenAppearanceSettings = { },
            featureFlags = ChronosFeatureFlags.AllEnabled
        )

        val ids = provider.commands().map { it.id }
        assertTrue(ids.contains("daydial.insights"))
        assertTrue(ids.contains("daydial.review"))
        assertEquals(11, ids.size)
        assertEquals("Today", provider.commands().first { it.id == "daydial.open" }.shortcutLabel)
    }

    @Test
    fun `day dial command provider omits review commands when review is disabled`() {
        val provider = dayDialCommandProvider(
            onOpenDayDial = { },
            onOpenPlan = { },
            onOpenFocusPlanner = { },
            onOpenInsights = { },
            onOpenPlanningTools = { },
            onOpenTemplates = { },
            onOpenReview = { },
            onOpenAiSettings = { },
            onOpenPrivacySync = { },
            onOpenNotificationSettings = { },
            onOpenAppearanceSettings = { },
            featureFlags = ChronosFeatureFlags.AllEnabled.copy(reviewEnabled = false)
        )

        val ids = provider.commands().map { it.id }
        assertFalse(ids.contains("daydial.insights"))
        assertFalse(ids.contains("daydial.review"))
        assertEquals(9, ids.size)
    }
}

