package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage

class DayDialScreenChromePaddingTest {
    @Test
    fun `day content bottom padding uses shell overlay inset when route padding is empty`() {
        assertEquals(
            132.dp,
            dayDialContentBottomPadding(
                routeBottomPadding = 0.dp,
                shellBottomInset = 108.dp
            )
        )
    }

    @Test
    fun `day content bottom padding preserves larger route padding`() {
        assertEquals(
            168.dp,
            dayDialContentBottomPadding(
                routeBottomPadding = 144.dp,
                shellBottomInset = 108.dp
            )
        )
    }

    @Test
    fun `day content bottom padding stays empty when no bottom inset exists`() {
        assertEquals(
            0.dp,
            dayDialContentBottomPadding(
                routeBottomPadding = 0.dp,
                shellBottomInset = 0.dp
            )
        )
    }

    @Test
    fun `sidebar back target is today for today page`() {
        assertEquals(
            DayDialTab.TODAY,
            sidebarBackTargetTab(DayDialTab.TODAY)
        )
    }

    @Test
    fun `sidebar back target is plan when currently in plan page`() {
        assertEquals(
            DayDialTab.PLAN,
            sidebarBackTargetTab(DayDialTab.PLAN)
        )
    }

    @Test
    fun `sidebar back target is today for non-plan primary tabs`() {
        assertEquals(
            DayDialTab.TODAY,
            sidebarBackTargetTab(DayDialTab.FOCUS)
        )
        assertEquals(
            DayDialTab.TODAY,
            sidebarBackTargetTab(DayDialTab.INSIGHTS)
        )
    }

    @Test
    fun `active sidebar pages suppress shell bottom chrome`() {
        assertFalse(
            shouldSuppressDayDialShellChrome(
                activeSidebarPage = null
            )
        )
        assertTrue(
            shouldSuppressDayDialShellChrome(
                activeSidebarPage = SidebarPage.DATA_EXPORT
            )
        )
        assertTrue(
            shouldSuppressDayDialShellChrome(
                activeSidebarPage = SidebarPage.APPEARANCE
            )
        )
    }

    @Test
    fun `sidebar menu hovers above shell bottom bar`() {
        assertEquals(108.dp, sidebarMenuBottomPadding(shellBottomInset = 108.dp))
    }

    @Test
    fun `sidebar menu keeps standard clearance without shell bottom bar`() {
        assertEquals(16.dp, sidebarMenuBottomPadding(shellBottomInset = 0.dp))
    }

    @Test
    fun `sidebar menu height stays a compact fraction of the screen`() {
        assertEquals(720.dp, sidebarMenuMaxHeight(availableHeight = 1000.dp))
    }

    @Test
    fun `sidebar pages keep daydial backdrop for predictive back preview`() {
        assertTrue(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = true,
                highContrastEnabled = false,
                activeSidebarPage = null
            )
        )
        assertTrue(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = true,
                highContrastEnabled = false,
                activeSidebarPage = SidebarPage.DATA_EXPORT
            )
        )
        assertTrue(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = true,
                highContrastEnabled = false,
                activeSidebarPage = SidebarPage.DAY_TOOLS
            )
        )
    }

    @Test
    fun `reduce motion keeps static daydial backdrop available`() {
        assertTrue(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = true,
                highContrastEnabled = false,
                activeSidebarPage = SidebarPage.APPEARANCE
            )
        )
    }

    @Test
    fun `high contrast and disabled glass hide daydial backdrop`() {
        assertFalse(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = true,
                highContrastEnabled = true,
                activeSidebarPage = null
            )
        )
        assertFalse(
            shouldShowDayDialBackdrop(
                glassSurfacesEnabled = false,
                highContrastEnabled = false,
                activeSidebarPage = null
            )
        )
    }

    @Test
    fun `sidebar page layer renders above primary tab content`() {
        assertTrue(sidebarPageLayerZIndex(SidebarPage.APPEARANCE) > primaryTabLayerZIndex(isActive = true))
        assertEquals(0f, sidebarPageLayerZIndex(null), 0f)
    }

    @Test
    fun `active sidebar page layers above exiting sidebar page`() {
        val activeLayer = sidebarPageLayerZIndex(
            page = SidebarPage.APPEARANCE,
            activeSidebarPage = SidebarPage.APPEARANCE
        )
        val exitingLayer = sidebarPageLayerZIndex(
            page = SidebarPage.DAY_TOOLS,
            activeSidebarPage = SidebarPage.APPEARANCE
        )

        assertTrue(activeLayer > exitingLayer)
        assertTrue(exitingLayer > primaryTabLayerZIndex(isActive = true))
    }

    @Test
    fun `full feature sidebar pages open their screens instead of in-dial pages`() {
        listOf(
            SidebarPage.TASKS,
            SidebarPage.FOCUS_TIMER,
            SidebarPage.HABITS,
            SidebarPage.MEDICATION
        ).forEach { page ->
            assertTrue(sidebarPageOpensFullScreen(page))
            assertNull(sidebarPageForDrawerSelection(page))
        }
    }

    @Test
    fun `remaining sidebar pages stay in the day shell`() {
        SidebarPage.entries
            .filterNot { sidebarPageOpensFullScreen(it) }
            .forEach { page ->
                assertEquals(page, sidebarPageForDrawerSelection(page))
            }
    }
}
