package com.chronosflow.feature.daydial.model

import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarPageFeatureFlagsTest {
    @Test
    fun `root sidebar pages include habits meds and review by default while review can be disabled`() {
        assertEquals(
            listOf(
                SidebarPage.DAY_TOOLS,
                SidebarPage.TASKS,
                SidebarPage.HABITS,
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
                ChronosFeatureFlags(reviewEnabled = false)
            )
        )
    }
}
