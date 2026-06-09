package com.chronosflow.feature.daydial

import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SidebarPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialMainContentTransitionTest {
    @Test
    fun `primary tab transition order is stable`() {
        assertEquals(0, getDayDialTargetIndex(DayDialTab.PLAN))
        assertEquals(1, getDayDialTargetIndex(DayDialTab.TODAY))
        assertEquals(2, getDayDialTargetIndex(DayDialTab.FOCUS))
        assertEquals(3, getDayDialTargetIndex(DayDialTab.INSIGHTS))
    }

    @Test
    fun `sidebar pages transition after primary tabs`() {
        assertEquals(4, getDayDialTargetIndex(SidebarPage.DAY_TOOLS))
        assertTrue(dayDialTransitionDirection(DayDialTab.FOCUS, SidebarPage.DAY_TOOLS) > 0)
        assertTrue(dayDialTransitionDirection(SidebarPage.DAY_TOOLS, DayDialTab.TODAY) < 0)
    }

    @Test
    fun `sidebar page to page transitions have directional order`() {
        assertTrue(dayDialTransitionDirection(SidebarPage.DAY_TOOLS, SidebarPage.APPEARANCE) > 0)
        assertTrue(dayDialTransitionDirection(SidebarPage.APPEARANCE, SidebarPage.DAY_TOOLS) < 0)
        assertEquals(0, dayDialTransitionDirection(SidebarPage.APPEARANCE, SidebarPage.APPEARANCE))
    }

    @Test
    fun `tab transition direction follows bottom navigation order`() {
        assertTrue(dayDialTransitionDirection(DayDialTab.PLAN, DayDialTab.TODAY) > 0)
        assertTrue(dayDialTransitionDirection(DayDialTab.FOCUS, DayDialTab.TODAY) < 0)
        assertEquals(0, dayDialTransitionDirection(DayDialTab.TODAY, DayDialTab.TODAY))
    }

    @Test
    fun `primary tab transition maps directly to navigation direction`() {
        assertEquals(
            ChronosTransitionDirection.Forward,
            dayDialPrimaryTabTransitionDirection(DayDialTab.PLAN, DayDialTab.TODAY)
        )
        assertEquals(
            ChronosTransitionDirection.Backward,
            dayDialPrimaryTabTransitionDirection(DayDialTab.FOCUS, DayDialTab.PLAN)
        )
        assertEquals(
            ChronosTransitionDirection.Neutral,
            dayDialPrimaryTabTransitionDirection(DayDialTab.TODAY, DayDialTab.TODAY)
        )
    }

    @Test
    fun `sidebar pages keep primary tab content behind predictive back`() {
        assertTrue(shouldRenderPrimaryTabContent(activeSidebarPage = null, sidebarBackProgress = 0f))
        assertTrue(shouldRenderPrimaryTabContent(activeSidebarPage = SidebarPage.DATA_EXPORT, sidebarBackProgress = 0.5f))
        assertTrue(shouldRenderPrimaryTabContent(activeSidebarPage = SidebarPage.APPEARANCE, sidebarBackProgress = -0.2f))
        assertFalse(shouldRenderPrimaryTabContent(activeSidebarPage = SidebarPage.DAY_TOOLS, sidebarBackProgress = 0f))
    }

    @Test
    fun `sidebar predictive back previews the tab it will return to`() {
        assertEquals(
            DayDialTab.TODAY,
            primaryTabContentTab(
                currentTab = DayDialTab.TODAY,
                activeSidebarPage = SidebarPage.DAY_TOOLS
            )
        )
        assertEquals(
            DayDialTab.PLAN,
            primaryTabContentTab(
                currentTab = DayDialTab.PLAN,
                activeSidebarPage = SidebarPage.AI_SETTINGS
            )
        )
        assertEquals(
            DayDialTab.TODAY,
            primaryTabContentTab(
                currentTab = DayDialTab.FOCUS,
                activeSidebarPage = SidebarPage.APPEARANCE
            )
        )
        assertEquals(
            DayDialTab.FOCUS,
            primaryTabContentTab(
                currentTab = DayDialTab.FOCUS,
                activeSidebarPage = null
            )
        )
    }

    @Test
    fun `sidebar layers compose only visible transition pages`() {
        assertEquals(
            emptyList<SidebarPage>(),
            sidebarPagesForAnimatedLayer(
                activeSidebarPage = null,
                currentSidebarPage = null,
                targetSidebarPage = null
            )
        )
        assertEquals(
            listOf(SidebarPage.TASKS),
            sidebarPagesForAnimatedLayer(
                activeSidebarPage = SidebarPage.TASKS,
                currentSidebarPage = null,
                targetSidebarPage = SidebarPage.TASKS
            )
        )
        assertEquals(
            listOf(SidebarPage.DAY_TOOLS, SidebarPage.APPEARANCE),
            sidebarPagesForAnimatedLayer(
                activeSidebarPage = SidebarPage.APPEARANCE,
                currentSidebarPage = SidebarPage.DAY_TOOLS,
                targetSidebarPage = SidebarPage.APPEARANCE
            )
        )
        assertEquals(
            listOf(SidebarPage.REVIEW),
            sidebarPagesForAnimatedLayer(
                activeSidebarPage = SidebarPage.REVIEW,
                currentSidebarPage = SidebarPage.REVIEW,
                targetSidebarPage = SidebarPage.REVIEW
            )
        )
    }

    @Test
    fun `primary tab route transitions are available for normal and reduced motion`() {
        val normalTransition = dayDialPrimaryTabTransition(
            initialState = DayDialTab.PLAN,
            targetState = DayDialTab.TODAY,
            reducedMotion = false
        )
        val reducedMotionTransition = dayDialPrimaryTabTransition(
            initialState = DayDialTab.PLAN,
            targetState = DayDialTab.TODAY,
            reducedMotion = true
        )

        assertNotNull(normalTransition.enter)
        assertNotNull(normalTransition.exit)
        assertNotNull(reducedMotionTransition.enter)
        assertNotNull(reducedMotionTransition.exit)
    }

    @Test
    fun `route enter fade starts immediately`() {
        assertEquals(0, dayDialRouteEnterFadeDelayMillis())
    }

    @Test
    fun `route fade keeps incoming page visible early`() {
        assertEquals(1.0, dayDialRouteEnterInitialAlpha(reducedMotion = false).toDouble(), 0.0001)
        assertEquals(0.0, dayDialRouteEnterInitialAlpha(reducedMotion = true).toDouble(), 0.0001)
        assertEquals(1, dayDialRouteEnterFadeDurationMillis(240))
        assertEquals(80, dayDialRouteExitFadeDurationMillis(240))
    }

    @Test
    fun `move in blur resolves immediately for reduced motion`() {
        assertEquals(8.0, dayDialMoveInBlurRadius(reducedMotion = false, entering = true).value.toDouble(), 0.0001)
        assertEquals(12.0, dayDialMoveInTravel(reducedMotion = false, entering = true).value.toDouble(), 0.0001)
        assertEquals(0.0, dayDialMoveInBlurRadius(reducedMotion = false, entering = false).value.toDouble(), 0.0001)
        assertEquals(0.0, dayDialMoveInTravel(reducedMotion = false, entering = false).value.toDouble(), 0.0001)
        assertEquals(0.0, dayDialMoveInLayerAlpha(reducedMotion = false, exiting = true).toDouble(), 0.0001)
        assertEquals(1.0, dayDialMoveInLayerAlpha(reducedMotion = false, exiting = false).toDouble(), 0.0001)
        assertEquals(0.0, dayDialMoveInBlurRadius(reducedMotion = true, entering = true).value.toDouble(), 0.0001)
        assertEquals(0.0, dayDialMoveInTravel(reducedMotion = true, entering = true).value.toDouble(), 0.0001)
        assertEquals(1.0, dayDialMoveInLayerAlpha(reducedMotion = true, exiting = true).toDouble(), 0.0001)
    }

    @Test
    fun `move in travel follows route direction`() {
        assertEquals(1, dayDialMoveInTravelMultiplier(ChronosTransitionDirection.Forward))
        assertEquals(-1, dayDialMoveInTravelMultiplier(ChronosTransitionDirection.Backward))
        assertEquals(0, dayDialMoveInTravelMultiplier(ChronosTransitionDirection.Neutral))
    }

    @Test
    fun `active primary tab layer stays above exiting pages`() {
        assertEquals(1f, primaryTabLayerZIndex(isActive = true), 0.0001f)
        assertEquals(0f, primaryTabLayerZIndex(isActive = false), 0.0001f)
    }
}
