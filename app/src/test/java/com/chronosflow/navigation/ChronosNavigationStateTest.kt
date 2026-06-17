package com.chronosflow.navigation

import com.chronosflow.core.notifications.SECTION_DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral regression tests for the hybrid Navigation 3 state holder. These lock in the
 * back-stack + Day-tab behavior that was hand-verified on device after the Nav2 → Nav3 migration,
 * notably the single-source-of-truth fix (back from a sub-section restores the prior Day tab,
 * and Day tabs never leak onto the cross-section back stack).
 */
class ChronosNavigationStateTest {

    @Test
    fun `initial state starts on day with a single day entry`() {
        val state = createChronosNavigationStateForTest()

        assertEquals(SECTION_DAY, state.topLevelSection)
        assertEquals(listOf<Any>(ChronosRoute.Day()), state.backStack.toList())
        assertNull(state.requestedDayTarget)
        assertFalse(state.canGoBack)
    }

    @Test
    fun `start day target seeds the requested day tab`() {
        val state = createChronosNavigationStateForTest(startDayTarget = ChronosRoute.Day.TARGET_PLAN)

        assertEquals(ChronosRoute.Day.TARGET_PLAN, state.requestedDayTarget)
    }

    @Test
    fun `navigating to a sub-section layers it above day`() {
        val state = createChronosNavigationStateForTest()

        state.navigate(ChronosRoute.Tasks())

        assertEquals(ChronosRoute.Tasks.section, state.topLevelSection)
        assertEquals(
            listOf<Any>(ChronosRoute.Day(), ChronosRoute.Tasks()),
            state.backStack.toList()
        )
        assertEquals(ChronosRoute.Tasks(), state.currentRoute)
        assertTrue(state.canGoBack)
    }

    @Test
    fun `sub-section context arguments are carried on the stack head`() {
        val state = createChronosNavigationStateForTest()

        state.navigate(ChronosRoute.Tasks(taskId = "task-9", target = "context"))

        assertEquals(
            ChronosRoute.Tasks(taskId = "task-9", target = "context"),
            state.backStack.last()
        )
    }

    @Test
    fun `day targets switch in place without touching the back stack`() {
        val state = createChronosNavigationStateForTest()

        state.navigate(ChronosRoute.Day(ChronosRoute.Day.TARGET_FOCUS_PLANNER))

        assertEquals(SECTION_DAY, state.topLevelSection)
        assertEquals(ChronosRoute.Day.TARGET_FOCUS_PLANNER, state.requestedDayTarget)
        // Day stays a single stable entry — the tab is UI state, not a back-stack entry.
        assertEquals(listOf<Any>(ChronosRoute.Day()), state.backStack.toList())
    }

    @Test
    fun `day target capture is surfaced for focus drafts`() {
        val state = createChronosNavigationStateForTest()

        state.navigate(
            ChronosRoute.Day(target = ChronosRoute.Day.TARGET_FOCUS_PLANNER, capture = "deep work draft")
        )

        assertEquals("deep work draft", state.requestedDayCapture)
    }

    @Test
    fun `selecting a day target bumps the generation each time`() {
        val state = createChronosNavigationStateForTest()
        val start = state.dayTargetGeneration

        state.selectDayTarget(ChronosRoute.Day.TARGET_TODAY)
        state.selectDayTarget(ChronosRoute.Day.TARGET_TODAY)

        // Even re-selecting the same tab bumps the generation (drives the Today double-tap reset).
        assertEquals(start + 2, state.dayTargetGeneration)
    }

    @Test
    fun `re-navigating to the same sub-section add target bumps the section generation`() {
        val state = createChronosNavigationStateForTest()
        val start = state.sectionTargetGeneration

        // Tapping "New task" twice in a row produces an equal Tasks(target = add) NavKey, so
        // NavDisplay sees no change. The bumped generation is what lets the screen re-open the
        // add sheet on the second tap (the "add FAB does nothing the second time" regression).
        state.navigate(ChronosRoute.Tasks(target = ChronosRoute.TARGET_ADD))
        state.navigate(ChronosRoute.Tasks(target = ChronosRoute.TARGET_ADD))

        assertEquals(start + 2, state.sectionTargetGeneration)
    }

    @Test
    fun `day navigation leaves the section generation untouched`() {
        val state = createChronosNavigationStateForTest()
        val start = state.sectionTargetGeneration

        // Day tabs ride on dayTargetGeneration, not the cross-section stack, so they must not
        // disturb the sub-section generation.
        state.navigate(ChronosRoute.Day(ChronosRoute.Day.TARGET_PLAN))
        state.selectDayTarget(ChronosRoute.Day.TARGET_TODAY)

        assertEquals(start, state.sectionTargetGeneration)
    }

    @Test
    fun `back from a sub-section returns to day and restores the prior day tab`() {
        val state = createChronosNavigationStateForTest()

        // On Focus, then jump to Tasks, then back — Focus must be restored (the desync regression).
        state.selectDayTarget(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
        state.navigate(ChronosRoute.Tasks())
        assertEquals(ChronosRoute.Tasks.section, state.topLevelSection)

        state.goBack()

        assertEquals(SECTION_DAY, state.topLevelSection)
        assertEquals(ChronosRoute.Day.TARGET_FOCUS_PLANNER, state.requestedDayTarget)
        assertEquals(listOf<Any>(ChronosRoute.Day()), state.backStack.toList())
        assertFalse(state.canGoBack)
    }

    @Test
    fun `switching between sub-sections does not stack them`() {
        val state = createChronosNavigationStateForTest()

        state.navigate(ChronosRoute.Tasks())
        state.navigate(ChronosRoute.Medication())

        assertEquals(ChronosRoute.Medication.section, state.topLevelSection)
        // Day stays the only anchor; the previous sub-section is replaced, not stacked.
        assertEquals(
            listOf<Any>(ChronosRoute.Day(), ChronosRoute.Medication()),
            state.backStack.toList()
        )
    }

    @Test
    fun `back at the day root is a no-op`() {
        val state = createChronosNavigationStateForTest()

        state.goBack()

        assertEquals(SECTION_DAY, state.topLevelSection)
        assertEquals(listOf<Any>(ChronosRoute.Day()), state.backStack.toList())
    }
}
