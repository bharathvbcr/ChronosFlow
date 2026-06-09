package com.chronosflow.feature.daydial

import androidx.compose.ui.unit.dp
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SidebarPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DayDialTopBarTransitionTest {
    @Test
    fun `primary tab switches leave top bar center static`() {
        assertFalse(
            shouldAnimateTopBarCenterTransition(
                initialState = null to DayDialTab.PLAN,
                targetState = null to DayDialTab.TODAY
            )
        )
        assertFalse(
            shouldAnimateTopBarCenterTransition(
                initialState = null to DayDialTab.TODAY,
                targetState = null to DayDialTab.FOCUS
            )
        )
    }

    @Test
    fun `sidebar transitions keep top bar center static after normalization`() {
        val primaryState = topBarCenterState(activeSidebarPage = null, currentTab = DayDialTab.TODAY)
        val sidebarState = topBarCenterState(activeSidebarPage = SidebarPage.DAY_TOOLS, currentTab = DayDialTab.TODAY)

        assertEquals(primaryState, sidebarState)
        assertFalse(
            shouldAnimateTopBarCenterTransition(
                initialState = primaryState,
                targetState = sidebarState
            )
        )
    }

    @Test
    fun `top bar keeps no extra bottom gutter before day content`() {
        assertEquals(0.dp, dayDialTopBarBottomPadding)
    }

    @Test
    fun `primary pages share one top bar date state`() {
        val expectedState = null to DayDialTab.TODAY

        DayDialTab.entries.forEach { tab ->
            assertEquals(
                expectedState,
                topBarCenterState(activeSidebarPage = null, currentTab = tab)
            )
        }
    }

    @Test
    fun `sidebar pages share the primary top bar date state`() {
        val expectedState = null to DayDialTab.TODAY

        SidebarPage.entries.forEach { page ->
            DayDialTab.entries.forEach { tab ->
                assertEquals(
                    expectedState,
                    topBarCenterState(activeSidebarPage = page, currentTab = tab)
                )
            }
        }
    }
}
