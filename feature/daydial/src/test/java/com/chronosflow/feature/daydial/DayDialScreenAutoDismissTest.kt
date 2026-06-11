package com.chronosflow.feature.daydial

import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.SidebarPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialScreenAutoDismissTest {
    @Test
    fun `same primary tab request does not close active sidebar page`() {
        val uiState = DayDialScreenUiState(DayDialTab.PLAN).apply {
            activeSidebarPage = SidebarPage.AI_SETTINGS
        }

        val lastApplied = applyRequestedPrimaryTabIfChanged(
            lastAppliedPrimaryTab = DayDialTab.PLAN,
            requestedPrimaryTab = DayDialTab.PLAN,
            uiState = uiState
        )

        assertEquals(DayDialTab.PLAN, lastApplied)
        assertEquals(DayDialTab.PLAN, uiState.currentTab)
        assertEquals(SidebarPage.AI_SETTINGS, uiState.activeSidebarPage)
    }

    @Test
    fun `new primary tab request closes active sidebar page`() {
        val uiState = DayDialScreenUiState(DayDialTab.TODAY).apply {
            activeSidebarPage = SidebarPage.DAY_TOOLS
        }

        val lastApplied = applyRequestedPrimaryTabIfChanged(
            lastAppliedPrimaryTab = DayDialTab.TODAY,
            requestedPrimaryTab = DayDialTab.PLAN,
            uiState = uiState
        )

        assertEquals(DayDialTab.PLAN, lastApplied)
        assertEquals(DayDialTab.PLAN, uiState.currentTab)
        assertNull(uiState.activeSidebarPage)
    }

    @Test
    fun `requested primary tab renders immediately before state sync`() {
        assertEquals(
            DayDialTab.PLAN,
            dayDialRenderedCurrentTab(
                requestedPrimaryTab = DayDialTab.PLAN,
                currentTab = DayDialTab.TODAY
            )
        )
    }

    @Test
    fun `new requested primary tab hides stale sidebar during current render`() {
        assertNull(
            dayDialRenderedActiveSidebarPage(
                requestedPrimaryTab = DayDialTab.PLAN,
                currentTab = DayDialTab.TODAY,
                activeSidebarPage = SidebarPage.DAY_TOOLS
            )
        )
        assertEquals(
            SidebarPage.DAY_TOOLS,
            dayDialRenderedActiveSidebarPage(
                requestedPrimaryTab = DayDialTab.TODAY,
                currentTab = DayDialTab.TODAY,
                activeSidebarPage = SidebarPage.DAY_TOOLS
            )
        )
    }

    @Test
    fun `launch targets map every root sidebar page`() {
        mapOf(
            "day-tools" to SidebarPage.DAY_TOOLS,
            "tasks" to SidebarPage.TASKS,
            "habits" to SidebarPage.HABITS,
            "medication" to SidebarPage.MEDICATION
        ).forEach { (target, page) ->
            assertEquals(page, sidebarPageForLaunchTarget(target))
        }
        // "review" routes to the unified review sheet instead of a sidebar page.
        assertNull(sidebarPageForLaunchTarget("review"))
    }

    @Test
    fun `sidebar launch targets use lightweight first draw`() {
        listOf(
            "day-tools",
            "tasks",
            "habits",
            "medication",
            "templates",
            "ai-settings",
            "privacy-sync",
            "notifications",
            "appearance"
        ).forEach { target ->
            assertTrue(launchTargetUsesLightweightSidebar(target))
        }
    }

    @Test
    fun `primary launch targets keep the data-backed day screen`() {
        listOf(
            null,
            "today",
            "today-reset",
            "add-block",
            "plan",
            "focus-planner",
            "insights"
        ).forEach { target ->
            assertFalse(launchTargetUsesLightweightSidebar(target))
        }
    }

    @Test
    fun `in-place insights tab handles system back by returning to today`() {
        assertEquals(
            DayDialTab.TODAY,
            dayDialBackFallbackTab(
                currentTab = DayDialTab.INSIGHTS,
                launchTarget = null,
                activeSheet = null
            )
        )
    }

    @Test
    fun `routed insights and active sheets leave system back to their owners`() {
        assertNull(
            dayDialBackFallbackTab(
                currentTab = DayDialTab.INSIGHTS,
                launchTarget = "insights",
                activeSheet = null
            )
        )
        assertNull(
            dayDialBackFallbackTab(
                currentTab = DayDialTab.INSIGHTS,
                launchTarget = null,
                activeSheet = SheetTarget.EndOfDayReview
            )
        )
        assertNull(
            dayDialBackFallbackTab(
                currentTab = DayDialTab.PLAN,
                launchTarget = null,
                activeSheet = null
            )
        )
    }

    @Test
    fun `marks generation start while ai sheet request is pending`() {
        val update = reduceAiPlanAutoDismiss(
            state = AiPlanAutoDismissState(pendingDismiss = true),
            activeSheet = SheetTarget.AiPlan,
            isGenerating = true,
            hasGeneratedPlanResult = false
        )

        assertEquals(
            AiPlanAutoDismissState(
                pendingDismiss = true,
                sawGenerationStart = true
            ),
            update.nextState
        )
        assertFalse(update.shouldDismiss)
    }

    @Test
    fun `dismisses ai sheet once generation finishes after starting`() {
        val update = reduceAiPlanAutoDismiss(
            state = AiPlanAutoDismissState(
                pendingDismiss = true,
                sawGenerationStart = true
            ),
            activeSheet = SheetTarget.AiPlan,
            isGenerating = false,
            hasGeneratedPlanResult = true
        )

        assertEquals(AiPlanAutoDismissState(), update.nextState)
        assertTrue(update.shouldDismiss)
    }

    @Test
    fun `clears pending dismissal when ai sheet is no longer active`() {
        val update = reduceAiPlanAutoDismiss(
            state = AiPlanAutoDismissState(
                pendingDismiss = true,
                sawGenerationStart = true
            ),
            activeSheet = null,
            isGenerating = false,
            hasGeneratedPlanResult = false
        )

        assertEquals(AiPlanAutoDismissState(), update.nextState)
        assertFalse(update.shouldDismiss)
    }

    @Test
    fun `does not dismiss when generation finishes without a fresh result`() {
        val update = reduceAiPlanAutoDismiss(
            state = AiPlanAutoDismissState(
                pendingDismiss = true,
                sawGenerationStart = true
            ),
            activeSheet = SheetTarget.AiPlan,
            isGenerating = false,
            hasGeneratedPlanResult = false
        )

        assertEquals(
            AiPlanAutoDismissState(
                pendingDismiss = true,
                sawGenerationStart = true
            ),
            update.nextState
        )
        assertFalse(update.shouldDismiss)
    }
}
