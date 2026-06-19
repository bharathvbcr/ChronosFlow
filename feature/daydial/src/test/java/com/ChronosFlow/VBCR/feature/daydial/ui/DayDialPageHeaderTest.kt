package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialPageHeaderTest {
    @Test
    fun primaryPageSubtitlesCoverEveryDayDialTab() {
        DayDialTab.entries.forEach { tab ->
            assertTrue(dayDialPrimaryPageSubtitle(tab).isNotBlank())
        }
        assertEquals(
            "Build blocks, templates, and AI suggestions",
            dayDialPrimaryPageSubtitle(DayDialTab.PLAN)
        )
    }

    @Test
    fun sidebarPageSubtitlesCoverEverySidebarPage() {
        SidebarPage.entries.forEach { page ->
            assertTrue(dayDialSidebarPageSubtitle(page).isNotBlank())
        }
        assertEquals(
            "Planner privacy, model status, and planning style",
            dayDialSidebarPageSubtitle(SidebarPage.AI_SETTINGS)
        )
    }
}
