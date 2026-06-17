package com.chronosflow.navigation

import androidx.activity.BackEventCompat
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.core.notifications.SECTION_DAY
import com.chronosflow.core.notifications.SECTION_FOCUS
import com.chronosflow.core.notifications.SECTION_MEDICATION
import com.chronosflow.core.notifications.SECTION_REVIEW
import com.chronosflow.core.notifications.SECTION_TASKS
import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.ui.shell.ChronosCompactShellBottomClearance
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.feature.daydial.model.DayDialTab
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosRouteShellDestinationTest {
    @Test
    fun `today badge shows viewed day of month when dial is off today`() {
        val today = java.time.LocalDate.of(2026, 6, 10)

        assertEquals("14", todayBadgeValue(today.plusDays(4), today, missedCount = 3))
        assertEquals("9", todayBadgeValue(today.minusDays(1), today, missedCount = 0))
    }

    @Test
    fun `today badge falls back to missed count when viewing today or unreported`() {
        val today = java.time.LocalDate.of(2026, 6, 10)

        assertEquals("3", todayBadgeValue(today, today, missedCount = 3))
        assertEquals("3", todayBadgeValue(null, today, missedCount = 3))
        assertNull(todayBadgeValue(today, today, missedCount = 0))
    }

    @Test
    fun `compact shell overlays bottom chrome without shrinking destinations`() {
        val navigationBarInset = 24.dp

        assertEquals(
            navigationBarInset + ChronosCompactShellBottomClearance,
            compactShellOverlayBottomInset(navigationBarInset, showNavigationChrome = true)
        )
        assertEquals(0.dp, compactShellOverlayBottomInset(navigationBarInset, showNavigationChrome = false))
    }

    @Test
    fun `compact shell floating bar and quick menu stay anchored when keyboard is open`() {
        val navigationBarInset = 24.dp
        val imeInset = 160.dp
        val expandedBottomInset = compactShellInteractiveBottomInset(
            navigationBarInset = navigationBarInset,
            imeInset = imeInset,
            quickAddExpanded = true
        )

        assertEquals(
            navigationBarInset + 16.dp,
            compactShellFloatingBarBottomOffset(navigationBarInset, showNavigationChrome = true)
        )
        assertEquals(
            navigationBarInset + 96.dp,
            compactQuickAddMenuBottomOffset(navigationBarInset, showNavigationChrome = true)
        )
        assertEquals(
            navigationBarInset,
            expandedBottomInset
        )
        assertEquals(
            navigationBarInset + 16.dp,
            compactShellFloatingBarBottomOffset(expandedBottomInset, showNavigationChrome = true)
        )
        assertEquals(
            navigationBarInset + 96.dp,
            compactQuickAddMenuBottomOffset(expandedBottomInset, showNavigationChrome = true)
        )
        assertEquals(
            navigationBarInset,
            compactShellInteractiveBottomInset(
                navigationBarInset = navigationBarInset,
                imeInset = imeInset,
                quickAddExpanded = false
            )
        )
        assertEquals(0.dp, compactShellFloatingBarBottomOffset(navigationBarInset, showNavigationChrome = false))
        assertEquals(0.dp, compactQuickAddMenuBottomOffset(navigationBarInset, showNavigationChrome = false))
    }

    @Test
    fun `compact shell keeps supporting screens out of the bottom navigation set`() {
        assertEquals(
            listOf(
                ChronosRoute.SHELL_PLAN,
                ChronosRoute.SHELL_TODAY,
                ChronosRoute.SHELL_FOCUS
            ),
            ChronosRoute.compactShellDestinations().map { it.id }
        )
    }

    @Test
    fun `expanded shell exposes the full route set`() {
        assertEquals(
            listOf(
                ChronosRoute.SHELL_PLAN,
                ChronosRoute.SHELL_TODAY,
                ChronosRoute.SHELL_FOCUS,
                ChronosRoute.SHELL_TASKS,
                ChronosRoute.SHELL_HABITS,
                ChronosRoute.SHELL_GOALS,
                ChronosRoute.SHELL_MEDICATION,
                ChronosRoute.SHELL_REVIEW
            ),
            ChronosRoute.expandedShellDestinations().map { it.id }
        )
    }

    @Test
    fun `default shell destinations include habits goals meds and graduated review`() {
        assertEquals(
            listOf(
                ChronosRoute.SHELL_PLAN,
                ChronosRoute.SHELL_TODAY,
                ChronosRoute.SHELL_FOCUS,
                ChronosRoute.SHELL_TASKS,
                ChronosRoute.SHELL_HABITS,
                ChronosRoute.SHELL_GOALS,
                ChronosRoute.SHELL_MEDICATION,
                ChronosRoute.SHELL_REVIEW
            ),
            ChronosRoute.expandedShellDestinations(ChronosFeatureFlags()).map { it.id }
        )

        assertEquals(
            listOf(
                ChronosRoute.SHELL_PLAN,
                ChronosRoute.SHELL_TODAY,
                ChronosRoute.SHELL_FOCUS,
                ChronosRoute.SHELL_TASKS,
                ChronosRoute.SHELL_HABITS,
                ChronosRoute.SHELL_GOALS,
                ChronosRoute.SHELL_MEDICATION
            ),
            ChronosRoute.expandedShellDestinations(
                ChronosFeatureFlags(reviewEnabled = false)
            ).map { it.id }
        )
    }

    @Test
    fun `supporting shell destinations open their full feature screens directly`() {
        val destinations = ChronosRoute.expandedShellDestinations().associateBy { it.id }

        assertEquals(
            ChronosRoute.Tasks(),
            destinations.getValue(ChronosRoute.SHELL_TASKS).route
        )
        assertEquals(
            ChronosRoute.Habits(),
            destinations.getValue(ChronosRoute.SHELL_HABITS).route
        )
        assertEquals(
            ChronosRoute.Medication(),
            destinations.getValue(ChronosRoute.SHELL_MEDICATION).route
        )
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_INSIGHTS),
            destinations.getValue(ChronosRoute.SHELL_REVIEW).route
        )
    }

    @Test
    fun `day graph falls back to shell sidebar launch targets`() {
        assertEquals(
            ChronosRoute.Day.TARGET_TASKS,
            dayLaunchTargetForRoute(
                routeTarget = null,
                shellDayTarget = ChronosRoute.Day.TARGET_TASKS
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_MEDICATION,
            dayLaunchTargetForRoute(
                routeTarget = ChronosRoute.Day.TARGET_MEDICATION,
                shellDayTarget = null
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            dayLaunchTargetForRoute(
                routeTarget = ChronosRoute.Day.TARGET_PLAN,
                shellDayTarget = ChronosRoute.Day.TARGET_TASKS
            )
        )
        assertNull(
            dayLaunchTargetForRoute(
                routeTarget = null,
                shellDayTarget = ChronosRoute.Day.TARGET_TODAY
            )
        )
    }

    @Test
    fun `medication and graduated review notifications are enabled by default`() {
        assertEquals(
            ChronosRoute.Medication(),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_MEDICATION),
                ChronosFeatureFlags()
            )
        )
        assertEquals(
            ChronosRoute.Day(),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_MEDICATION),
                ChronosFeatureFlags(medicationEnabled = false)
            )
        )
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_REVIEW),
                ChronosFeatureFlags()
            )
        )
        assertEquals(
            ChronosRoute.Day(),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_REVIEW),
                ChronosFeatureFlags(reviewEnabled = false)
            )
        )
    }

    @Test
    fun `quick add includes habits goals and meds by default`() {
        assertEquals(
            listOf(
                "New block",
                "New task",
                "New meds",
                "New habit",
                "New goal",
                "Journal entry",
                "Log sleep",
                "Type a task, med, habit, or goal"
            ),
            quickAddActionsFor(ChronosFeatureFlags()).map { it.label }
        )

        assertEquals(
            listOf("New block", "New task", "Type a task, med, habit, or goal"),
            quickAddActionsFor(
                ChronosFeatureFlags(
                    habitsEnabled = false,
                    medicationEnabled = false,
                    goalsEnabled = false,
                    reviewEnabled = false,
                    journalEnabled = false,
                    sleepEnabled = false
                )
            ).map { it.label }
        )
    }

    @Test
    fun `quick add actions open creation surfaces`() {
        val actionRoutes = quickAddActionsFor(ChronosFeatureFlags()).associate { it.label to it.route }

        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK),
            actionRoutes["New block"]
        )
        assertEquals(
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD),
            actionRoutes["New task"]
        )
        assertEquals(
            ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD),
            actionRoutes["New meds"]
        )
        assertEquals(
            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD),
            actionRoutes["New habit"]
        )
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_JOURNAL),
            actionRoutes["Journal entry"]
        )
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_SLEEP),
            actionRoutes["Log sleep"]
        )
    }

    @Test
    fun `shell navigation ignores taps on selected destination`() {
        assertFalse(shouldNavigateShellDestination(selectedId = ChronosRoute.SHELL_TODAY, destinationId = ChronosRoute.SHELL_TODAY))
        assertTrue(shouldNavigateShellDestination(selectedId = ChronosRoute.SHELL_TODAY, destinationId = ChronosRoute.SHELL_PLAN))
    }

    @Test
    fun `quick add closes when shell destination changes`() {
        val baseTodayKey = quickAddDestinationChangeKey(
            destinationId = ChronosRoute.SHELL_TODAY,
            dayTarget = null
        )
        val todayKey = quickAddDestinationChangeKey(
            destinationId = ChronosRoute.SHELL_TODAY,
            dayTarget = ChronosRoute.Day.TARGET_TODAY
        )
        val planKey = quickAddDestinationChangeKey(
            destinationId = ChronosRoute.SHELL_PLAN,
            dayTarget = ChronosRoute.Day.TARGET_PLAN
        )
        val focusKey = quickAddDestinationChangeKey(
            destinationId = ChronosRoute.SHELL_FOCUS,
            dayTarget = ChronosRoute.Day.TARGET_FOCUS_PLANNER
        )

        assertTrue(
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = baseTodayKey,
                destinationKey = todayKey,
                quickAddExpanded = true
            )
        )
        assertTrue(
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = todayKey,
                destinationKey = planKey,
                quickAddExpanded = true
            )
        )
        assertTrue(
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = planKey,
                destinationKey = focusKey,
                quickAddExpanded = true
            )
        )
        assertFalse(
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = todayKey,
                destinationKey = todayKey,
                quickAddExpanded = true
            )
        )
        assertFalse(
            shouldCloseQuickAddForDestinationChange(
                previousDestinationKey = todayKey,
                destinationKey = planKey,
                quickAddExpanded = false
            )
        )
    }

    @Test
    fun `day shell targets map to DayDial tabs`() {
        assertEquals(DayDialTab.TODAY, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_TODAY))
        // The Today double-tap target resets the date but still renders the Today tab.
        assertEquals(DayDialTab.TODAY, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_TODAY_RESET))
        assertEquals(DayDialTab.PLAN, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_PLAN))
        assertEquals(DayDialTab.FOCUS, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_FOCUS_PLANNER))
        assertEquals(null, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_INSIGHTS))

        assertEquals(ChronosRoute.Day.TARGET_TODAY, dayTargetForDayDialTab(DayDialTab.TODAY))
        assertEquals(ChronosRoute.Day.TARGET_PLAN, dayTargetForDayDialTab(DayDialTab.PLAN))
        assertEquals(ChronosRoute.Day.TARGET_FOCUS_PLANNER, dayTargetForDayDialTab(DayDialTab.FOCUS))
    }

    @Test
    fun `compact shell double click detection does not delay first tap`() {
        assertFalse(
            shouldTreatCompactNavigationClickAsDouble(
                previousClickUptimeMillis = 0L,
                clickUptimeMillis = 1_000L
            )
        )
        assertTrue(
            shouldTreatCompactNavigationClickAsDouble(
                previousClickUptimeMillis = 1_000L,
                clickUptimeMillis = 1_240L
            )
        )
        assertFalse(
            shouldTreatCompactNavigationClickAsDouble(
                previousClickUptimeMillis = 1_000L,
                clickUptimeMillis = 1_400L
            )
        )
    }

    @Test
    fun `compact shell ignores deferred clickable after immediate touch down`() {
        assertTrue(
            shouldIgnoreCompactNavigationDeferredClick(
                lastImmediateClickUptimeMillis = 1_000L,
                clickUptimeMillis = 1_120L
            )
        )
        assertFalse(
            shouldIgnoreCompactNavigationDeferredClick(
                lastImmediateClickUptimeMillis = 0L,
                clickUptimeMillis = 1_120L
            )
        )
        assertFalse(
            shouldIgnoreCompactNavigationDeferredClick(
                lastImmediateClickUptimeMillis = 1_000L,
                clickUptimeMillis = 1_500L
            )
        )
    }

    @Test
    fun `compact shell motion respects reduced motion`() {
        assertEquals(1.02, compactNavigationItemTargetScale(selected = true, reducedMotion = false).toDouble(), 0.0001)
        assertEquals(1.0, compactNavigationItemTargetScale(selected = false, reducedMotion = false).toDouble(), 0.0001)
        assertEquals(1.0, compactNavigationItemTargetScale(selected = true, reducedMotion = true).toDouble(), 0.0001)

        assertEquals(45.0, quickAddIconRotationDegrees(expanded = true, reducedMotion = false).toDouble(), 0.0001)
        assertEquals(0.0, quickAddIconRotationDegrees(expanded = false, reducedMotion = false).toDouble(), 0.0001)
        assertEquals(0.0, quickAddIconRotationDegrees(expanded = true, reducedMotion = true).toDouble(), 0.0001)
    }

    @Test
    fun `shell haptics suppress movement ticks when reduced motion is enabled`() {
        assertTrue(shouldPerformShellHaptic(ChronosShellHapticCue.NavigationTick, reducedMotion = false))
        assertFalse(shouldPerformShellHaptic(ChronosShellHapticCue.NavigationTick, reducedMotion = true))

        assertTrue(shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddToggle, reducedMotion = false))
        assertTrue(shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddToggle, reducedMotion = true))
        assertTrue(shouldPerformShellHaptic(ChronosShellHapticCue.QuickAddAction, reducedMotion = true))
    }

    @Test
    fun `quick add predictive back progress follows the swipe edge`() {
        assertEquals(
            0.4,
            signedQuickAddBackProgress(progress = 0.4f, swipeEdge = BackEventCompat.EDGE_LEFT).toDouble(),
            0.0001
        )
        assertEquals(
            -0.4,
            signedQuickAddBackProgress(progress = 0.4f, swipeEdge = BackEventCompat.EDGE_RIGHT).toDouble(),
            0.0001
        )
        assertEquals(
            1.0,
            signedQuickAddBackProgress(progress = 1.4f, swipeEdge = BackEventCompat.EDGE_LEFT).toDouble(),
            0.0001
        )
    }

    @Test
    fun `quick capture input state reflects empty pending ready and rejected text`() {
        assertEquals(
            QuickCaptureInputState(
                canSubmit = false,
                isError = false,
                helperText = null,
                submitContentDescription = "Quick add typed command"
            ),
            quickCaptureInputState(capture = "", previewLabel = null, rejectedCapture = null)
        )
        assertEquals(
            QuickCaptureInputState(
                canSubmit = false,
                isError = false,
                helperText = "Keep typing for a task, med, or habit.",
                submitContentDescription = "Quick add typed command"
            ),
            quickCaptureInputState(capture = "task", previewLabel = null, rejectedCapture = null)
        )
        assertEquals(
            QuickCaptureInputState(
                canSubmit = true,
                isError = false,
                helperText = "Ready to add med.",
                submitContentDescription = "Quick add med"
            ),
            quickCaptureInputState(capture = "vitamin d morning", previewLabel = "med", rejectedCapture = null)
        )
        assertEquals(
            QuickCaptureInputState(
                canSubmit = false,
                isError = true,
                helperText = "Add more detail for a task, med, or habit.",
                submitContentDescription = "Quick add typed command"
            ),
            quickCaptureInputState(capture = "task", previewLabel = null, rejectedCapture = "task")
        )
    }

    @Test
    fun `disabled feature placeholder copy points to developer settings`() {
        assertEquals("Review is disabled", disabledFeatureTitle("Review"))
        assertEquals(
            "Enable it from Developer settings to show this module.",
            disabledFeatureMessage()
        )
    }

    @Test
    fun `day targets and focus routes resolve to the expected shell destination`() {
        assertEquals(
            ChronosRoute.SHELL_TODAY,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_TODAY).id
        )
        // The Today double-tap reset target keeps the Today bottom-bar item highlighted.
        assertEquals(
            ChronosRoute.SHELL_TODAY,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_TODAY_RESET).id
        )
        assertEquals(
            ChronosRoute.SHELL_PLAN,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_PLAN).id
        )
        assertEquals(
            ChronosRoute.SHELL_FOCUS,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_FOCUS_PLANNER).id
        )
        assertEquals(
            ChronosRoute.SHELL_REVIEW,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_INSIGHTS).id
        )
        assertEquals(
            ChronosRoute.SHELL_TODAY,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_TODAY).id
        )
        assertEquals(
            ChronosRoute.SHELL_TASKS,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_TASKS).id
        )
        assertEquals(
            ChronosRoute.SHELL_HABITS,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_HABITS).id
        )
        assertEquals(
            ChronosRoute.SHELL_MEDICATION,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_MEDICATION).id
        )
        assertEquals(
            ChronosRoute.SHELL_FOCUS,
            ChronosRoute.shellDestinationFor(SECTION_FOCUS, null).id
        )
        assertEquals(
            ChronosRoute.SHELL_MEDICATION,
            ChronosRoute.shellDestinationFor(
                SECTION_MEDICATION,
                null,
                ChronosFeatureFlags.AllEnabled
            ).id
        )
    }

    @Test
    fun `quick add routes target add sheets`() {
        assertEquals(
            ChronosRoute.Day(target = ChronosRoute.Day.TARGET_ADD_BLOCK),
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
        )
        assertEquals(
            ChronosRoute.Tasks(target = ChronosRoute.TARGET_ADD),
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
        )
        assertEquals(
            ChronosRoute.Habits(target = ChronosRoute.TARGET_ADD),
            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
        )
        assertEquals(
            ChronosRoute.Medication(target = ChronosRoute.TARGET_ADD),
            ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD)
        )
    }

    @Test
    fun `capture routes carry add text for dynamic create flows`() {
        assertEquals(
            ChronosRoute.Tasks(target = ChronosRoute.TARGET_ADD, capture = "call mom tomorrow"),
            ChronosRoute.Tasks.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "call mom tomorrow"
            )
        )
        assertEquals(
            ChronosRoute.Habits(target = ChronosRoute.TARGET_ADD, capture = "gym 3x week evening"),
            ChronosRoute.Habits.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "gym 3x week evening"
            )
        )
        assertEquals(
            ChronosRoute.Medication(target = ChronosRoute.TARGET_ADD, capture = "vitamin d 1000 iu morning"),
            ChronosRoute.Medication.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "vitamin d 1000 iu morning"
            )
        )
    }

    @Test
    fun `capture create routes carry typed command text for type-safe navigation`() {
        val capture = "call mom & vitamin d"

        assertEquals(
            ChronosRoute.Tasks(target = ChronosRoute.TARGET_ADD, capture = capture),
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            ChronosRoute.Habits(target = ChronosRoute.TARGET_ADD, capture = capture),
            ChronosRoute.Habits.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            ChronosRoute.Medication(target = ChronosRoute.TARGET_ADD, capture = capture),
            ChronosRoute.Medication.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            ChronosRoute.Day(target = ChronosRoute.Day.TARGET_FOCUS_PLANNER, capture = capture),
            ChronosRoute.Day.createRoute(target = ChronosRoute.Day.TARGET_FOCUS_PLANNER, capture = capture)
        )
    }

    @Test
    fun `top level sections open their full feature screens directly`() {
        assertEquals(ChronosRoute.Day(), ChronosRoute.topLevelRouteFor(SECTION_DAY))
        assertEquals(ChronosRoute.Day(ChronosRoute.Day.TARGET_FOCUS_PLANNER), ChronosRoute.topLevelRouteFor(SECTION_FOCUS))
        assertEquals(ChronosRoute.Tasks(), ChronosRoute.topLevelRouteFor(SECTION_TASKS))
        assertEquals(ChronosRoute.Habits(), ChronosRoute.topLevelRouteFor(SECTION_HABITS))
        assertEquals(ChronosRoute.Medication(), ChronosRoute.topLevelRouteFor(SECTION_MEDICATION))
        assertEquals(
            ChronosRoute.Day(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.topLevelRouteFor(SECTION_REVIEW)
        )
    }
}
