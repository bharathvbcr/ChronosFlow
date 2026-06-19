package com.ChronosFlow.VBCR.feature.daydial.model

import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarPageFeatureFlagsTest {
    @Test
    fun `root sidebar pages include habits goals meds and graduated review by default`() {
        assertEquals(
            listOf(
                SidebarPage.DAY_TOOLS,
                SidebarPage.TASKS,
                SidebarPage.HABITS,
                SidebarPage.GOALS,
                SidebarPage.MEDICATION,
                SidebarPage.REVIEW
            ),
            SidebarPage.rootPages(ChronosFeatureFlags())
        )

        assertEquals(
            listOf(
                SidebarPage.DAY_TOOLS,
                SidebarPage.TASKS,
                SidebarPage.HABITS,
                SidebarPage.MEDICATION
            ),
            SidebarPage.rootPages(
                ChronosFeatureFlags(reviewEnabled = false, goalsEnabled = false)
            )
        )
    }
}
