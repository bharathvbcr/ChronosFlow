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
            ChronosRoute.Tasks.route,
            destinations.getValue(ChronosRoute.SHELL_TASKS).route
        )
        assertEquals(
            ChronosRoute.Habits.route,
            destinations.getValue(ChronosRoute.SHELL_HABITS).route
        )
        assertEquals(
            ChronosRoute.Medication.route,
            destinations.getValue(ChronosRoute.SHELL_MEDICATION).route
        )
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS),
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
    fun `initial shell target only seeds sidebar launch pages`() {
        assertEquals(
            ChronosRoute.Day.TARGET_TASKS,
            initialLocalDayTarget(ChronosRoute.Day.TARGET_TASKS)
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TEMPLATES,
            initialLocalDayTarget(ChronosRoute.Day.TARGET_TEMPLATES)
        )
        assertNull(initialLocalDayTarget(ChronosRoute.Day.TARGET_TODAY))
        // Review resolves to the Insights tab, which is not a sidebar launch page.
        assertNull(initialLocalDayTarget(ChronosRoute.Day.TARGET_INSIGHTS))
    }

    @Test
    fun `external sidebar launch replaces current local day target`() {
        assertEquals(
            ChronosRoute.Day.TARGET_HABITS,
            localDayTargetAfterExternalLaunch(
                launchDayTarget = ChronosRoute.Day.TARGET_HABITS,
                localDayTarget = ChronosRoute.Day.TARGET_TASKS
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TASKS,
            localDayTargetAfterExternalLaunch(
                launchDayTarget = ChronosRoute.Day.TARGET_TODAY,
                localDayTarget = ChronosRoute.Day.TARGET_TASKS
            )
        )
    }

    @Test
    fun `medication and graduated review notifications are enabled by default`() {
        assertEquals(
            ChronosRoute.Medication.route,
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_MEDICATION),
                ChronosFeatureFlags()
            )
        )
        assertEquals(
            ChronosRoute.Day.createRoute(),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_MEDICATION),
                ChronosFeatureFlags(medicationEnabled = false)
            )
        )
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.routeForNotificationLaunch(
                NotificationLaunch(section = SECTION_REVIEW),
                ChronosFeatureFlags()
            )
        )
        assertEquals(
            ChronosRoute.Day.createRoute(),
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
    fun `day primary shell destinations can switch in place while already on day`() {
        val planDestination = ChronosRoute.shellDestinations.single { it.id == ChronosRoute.SHELL_PLAN }
        val todayDestination = ChronosRoute.shellDestinations.single { it.id == ChronosRoute.SHELL_TODAY }
        val focusDestination = ChronosRoute.shellDestinations.single { it.id == ChronosRoute.SHELL_FOCUS }
        val reviewDestination = ChronosRoute.shellDestinations.single { it.id == ChronosRoute.SHELL_REVIEW }

        assertTrue(shouldHandleDayPrimaryDestinationInPlace(ChronosRoute.Day.section, planDestination))
        assertTrue(shouldHandleDayPrimaryDestinationInPlace(ChronosRoute.Day.section, todayDestination))
        assertTrue(shouldHandleDayPrimaryDestinationInPlace(ChronosRoute.Day.section, focusDestination))
        assertFalse(shouldHandleDayPrimaryDestinationInPlace(ChronosRoute.Tasks.section, planDestination))
        assertFalse(shouldHandleDayPrimaryDestinationInPlace(ChronosRoute.Day.section, reviewDestination))
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
    fun `local day target overrides route target only while shell is on day`() {
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            effectiveShellDayTarget(
                currentSection = ChronosRoute.Day.section,
                routeDayTarget = ChronosRoute.Day.TARGET_TODAY,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TODAY,
            effectiveShellDayTarget(
                currentSection = ChronosRoute.Day.section,
                routeDayTarget = ChronosRoute.Day.TARGET_TODAY,
                localDayTarget = null
            )
        )
        assertEquals(
            null,
            effectiveShellDayTarget(
                currentSection = ChronosRoute.Tasks.section,
                routeDayTarget = null,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
    }

    @Test
    fun `pending primary day target renders immediately while shell is on day`() {
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            optimisticShellDayTarget(
                currentSection = ChronosRoute.Day.section,
                effectiveDayTarget = ChronosRoute.Day.TARGET_TODAY,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TODAY,
            optimisticShellDayTarget(
                currentSection = ChronosRoute.Tasks.section,
                effectiveDayTarget = ChronosRoute.Day.TARGET_TODAY,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TODAY,
            optimisticShellDayTarget(
                currentSection = ChronosRoute.Day.section,
                effectiveDayTarget = ChronosRoute.Day.TARGET_TODAY,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_INSIGHTS
            )
        )
    }

    @Test
    fun `pending primary day target is committed until synchronized target catches up`() {
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            localDayTargetAfterOptimisticPrimaryTarget(
                currentSection = ChronosRoute.Day.section,
                synchronizedLocalDayTarget = ChronosRoute.Day.TARGET_TODAY,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            localDayTargetAfterOptimisticPrimaryTarget(
                currentSection = ChronosRoute.Day.section,
                synchronizedLocalDayTarget = ChronosRoute.Day.TARGET_PLAN,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_TODAY,
            localDayTargetAfterOptimisticPrimaryTarget(
                currentSection = ChronosRoute.Tasks.section,
                synchronizedLocalDayTarget = ChronosRoute.Day.TARGET_TODAY,
                pendingPrimaryDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
    }

    @Test
    fun `changed route day target replaces stale local day target while shell is on day`() {
        assertEquals(
            ChronosRoute.Day.TARGET_FOCUS_PLANNER,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = ChronosRoute.Day.section,
                previousRouteDayTarget = ChronosRoute.Day.TARGET_TODAY,
                routeDayTarget = ChronosRoute.Day.TARGET_FOCUS_PLANNER,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_INSIGHTS,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = ChronosRoute.Day.section,
                previousRouteDayTarget = ChronosRoute.Day.TARGET_PLAN,
                routeDayTarget = ChronosRoute.Day.TARGET_INSIGHTS,
                localDayTarget = ChronosRoute.Day.TARGET_FOCUS_PLANNER
            )
        )
    }

    @Test
    fun `unchanged or missing route day target preserves in-place local day target`() {
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = ChronosRoute.Day.section,
                previousRouteDayTarget = ChronosRoute.Day.TARGET_TODAY,
                routeDayTarget = ChronosRoute.Day.TARGET_TODAY,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            ChronosRoute.Day.TARGET_PLAN,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = ChronosRoute.Day.section,
                previousRouteDayTarget = ChronosRoute.Day.TARGET_TODAY,
                routeDayTarget = null,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
    }

    @Test
    fun `base day route clears stale local target after returning from another section`() {
        assertEquals(
            null,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = ChronosRoute.Tasks.section,
                previousRouteDayTarget = null,
                routeDayTarget = null,
                localDayTarget = ChronosRoute.Day.TARGET_PLAN
            )
        )
        assertEquals(
            null,
            localDayTargetAfterRouteTargetChange(
                currentSection = ChronosRoute.Day.section,
                previousSection = null,
                previousRouteDayTarget = null,
                routeDayTarget = null,
                localDayTarget = ChronosRoute.Day.TARGET_FOCUS_PLANNER
            )
        )
    }

    @Test
    fun `day shell targets map to DayDial tabs`() {
        assertEquals(DayDialTab.TODAY, dayDialTabForShellTarget(ChronosRoute.Day.TARGET_TODAY))
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
    fun `page transitions use global shared axis motion by default`() {
        val pop = chronosRouteTransitionSelection(
            from = ChronosRoute.Tasks.route,
            to = ChronosRoute.Day.route,
            operation = ChronosNavigationOperation.Pop,
            reducedMotion = false
        )

        assertEquals(ChronosRouteTransitionKind.MaterialSharedAxis, pop.kind)
        assertEquals(ChronosTransitionDirection.Backward, pop.direction)
        assertEquals(ChronosMotionDefaults.DefaultDurationMillis, pop.motionConfig.durationMillis)
        assertEquals(
            ChronosMotionDefaults.SharedAxisSlideFraction.toDouble(),
            pop.motionConfig.slideFraction.toDouble(),
            0.0001
        )

        val reduced = chronosRouteTransitionSelection(
            from = ChronosRoute.Tasks.route,
            to = ChronosRoute.Day.route,
            operation = ChronosNavigationOperation.Pop,
            reducedMotion = true
        )

        assertEquals(ChronosMotionDefaults.ReducedDurationMillis, reduced.motionConfig.durationMillis)
        assertEquals(0.0, reduced.motionConfig.slideFraction.toDouble(), 0.0001)
        assertEquals(1.0, reduced.motionConfig.enterScale.toDouble(), 0.0001)
        assertEquals(1.0, reduced.motionConfig.exitScale.toDouble(), 0.0001)
    }

    @Test
    fun `route transition resolver suppresses day internal motion and fades shell peers`() {
        val dayInternal = chronosRouteTransitionSelection(
            from = ChronosRoute.Day.route,
            to = ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN),
            operation = ChronosNavigationOperation.Push,
            reducedMotion = false
        )
        assertEquals(ChronosRouteTransitionKind.None, dayInternal.kind)
        assertEquals(ChronosTransitionDirection.Neutral, dayInternal.direction)

        val shellPeer = chronosRouteTransitionSelection(
            from = ChronosRoute.Tasks.route,
            to = ChronosRoute.Medication.route,
            operation = ChronosNavigationOperation.Push,
            reducedMotion = false
        )
        assertEquals(ChronosRouteTransitionKind.ShellPeerFade, shellPeer.kind)
        assertEquals(ChronosTransitionDirection.Neutral, shellPeer.direction)
    }

    @Test
    fun `day targets and focus routes resolve to the expected shell destination`() {
        assertEquals(
            ChronosRoute.SHELL_TODAY,
            ChronosRoute.shellDestinationFor(SECTION_DAY, ChronosRoute.Day.TARGET_TODAY).id
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
            "${SECTION_DAY}?target=${ChronosRoute.Day.TARGET_ADD_BLOCK}",
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
        )
        assertEquals(
            "${SECTION_TASKS}?taskId=&target=${ChronosRoute.TARGET_ADD}",
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
        )
        assertEquals(
            "${SECTION_HABITS}?target=${ChronosRoute.TARGET_ADD}",
            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
        )
        assertEquals(
            "${SECTION_MEDICATION}?target=${ChronosRoute.TARGET_ADD}",
            ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD)
        )
    }

    @Test
    fun `capture routes encode add text for dynamic create flows`() {
        assertEquals(
            "${SECTION_TASKS}?taskId=&target=${ChronosRoute.TARGET_ADD}&capture=call%20mom%20tomorrow",
            ChronosRoute.Tasks.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "call mom tomorrow"
            )
        )
        assertEquals(
            "${SECTION_HABITS}?target=${ChronosRoute.TARGET_ADD}&capture=gym%203x%20week%20evening",
            ChronosRoute.Habits.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "gym 3x week evening"
            )
        )
        assertEquals(
            "${SECTION_MEDICATION}?target=${ChronosRoute.TARGET_ADD}&capture=vitamin%20d%201000%20iu%20morning",
            ChronosRoute.Medication.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "vitamin d 1000 iu morning"
            )
        )
    }

    @Test
    fun `one shot sheet routes can re-enter while primary tabs stay single top`() {
        assertFalse(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
            )
        )
        assertFalse(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
            )
        )
        assertFalse(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
            )
        )
        assertFalse(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD)
            )
        )
        assertTrue(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)
            )
        )
        assertTrue(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN)
            )
        )
        assertTrue(
            shouldLaunchSingleTopForRoute(
                ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
            )
        )
        assertTrue(shouldLaunchSingleTopForRoute(ChronosRoute.Tasks.route))
    }

    @Test
    fun `day primary tab routes do not restore a stale day tab state`() {
        val dayPrimaryTabRoutes = listOf(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY),
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN),
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
        )

        dayPrimaryTabRoutes.forEach { route ->
            val policy = navigationPolicyForRoute(route)

            assertTrue("Expected launchSingleTop for $route", policy.launchSingleTop)
            assertFalse("Expected no restoreState for $route", policy.restoreState)
            assertFalse("Expected no saveState for $route", policy.saveState)
            assertEquals(ChronosRoute.Day.route, policy.popUpToRoute)
        }
    }

    @Test
    fun `today reset route does not restore a stale day state`() {
        // Double-tapping the Today tab navigates to "today-reset"; it must apply fresh
        // (launchSingleTop, no restoreState/saveState) so selectDate(now) runs. If it
        // restored a saved Day back stack, the stale target/ViewModel would clobber the
        // reset and the dial would stay on the previously viewed date.
        val policy = navigationPolicyForRoute(ChronosRoute.Day.createRoute("today-reset"))

        assertTrue("Expected launchSingleTop for today-reset", policy.launchSingleTop)
        assertFalse("Expected no restoreState for today-reset", policy.restoreState)
        assertFalse("Expected no saveState for today-reset", policy.saveState)
        assertEquals(ChronosRoute.Day.route, policy.popUpToRoute)
    }

    @Test
    fun `stable shell routes save and restore back stack state`() {
        val stableRoutes = listOf(
            ChronosRoute.Day.createRoute(),
            ChronosRoute.Tasks.route,
            ChronosRoute.Habits.route,
            ChronosRoute.Medication.route
        )

        stableRoutes.forEach { route ->
            val policy = navigationPolicyForRoute(route)

            assertTrue("Expected launchSingleTop for $route", policy.launchSingleTop)
            assertTrue("Expected restoreState for $route", policy.restoreState)
            assertTrue("Expected saveState for $route", policy.saveState)
            assertEquals(ChronosRoute.Day.route, policy.popUpToRoute)
        }
    }

    @Test
    fun `insights route is back stackable without restoring stale day state`() {
        val policy = navigationPolicyForRoute(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)
        )

        assertFalse(policy.launchSingleTop)
        assertFalse(policy.restoreState)
        assertFalse(policy.saveState)
        assertNull(policy.popUpToRoute)
    }

    @Test
    fun `one shot sheet routes do not restore stale sheet state`() {
        val oneShotRoutes = listOf(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK),
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD),
            ChronosRoute.Tasks.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "call mom tomorrow"
            ),
            ChronosRoute.Tasks.createRoute(taskId = "task-1", target = "context"),
            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD),
            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD, capture = "gym 3x week evening"),
            ChronosRoute.Medication.createRoute(ChronosRoute.TARGET_ADD),
            ChronosRoute.Medication.createRoute(
                target = ChronosRoute.TARGET_ADD,
                capture = "vitamin d 1000 iu morning"
            )
        )

        oneShotRoutes.forEach { route ->
            val policy = navigationPolicyForRoute(route)

            assertFalse("Expected no launchSingleTop for $route", policy.launchSingleTop)
            assertFalse("Expected no restoreState for $route", policy.restoreState)
            assertFalse("Expected no saveState for $route", policy.saveState)
            assertEquals(ChronosRoute.Day.route, policy.popUpToRoute)
        }
    }

    @Test
    fun `capture create routes encode typed command text for query navigation`() {
        val capture = "call mom & vitamin d"
        val encodedCapture = "call%20mom%20%26%20vitamin%20d"

        assertEquals(
            "$SECTION_TASKS?taskId=&target=add&capture=$encodedCapture",
            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            "$SECTION_HABITS?target=add&capture=$encodedCapture",
            ChronosRoute.Habits.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            "$SECTION_MEDICATION?target=add&capture=$encodedCapture",
            ChronosRoute.Medication.createRoute(target = ChronosRoute.TARGET_ADD, capture = capture)
        )
        assertEquals(
            "$SECTION_DAY?target=${ChronosRoute.Day.TARGET_FOCUS_PLANNER}&capture=$encodedCapture",
            ChronosRoute.Day.createRoute(target = ChronosRoute.Day.TARGET_FOCUS_PLANNER, capture = capture)
        )
    }

    @Test
    fun `top level sections open their full feature screens directly`() {
        assertEquals(ChronosRoute.Day.createRoute(), ChronosRoute.topLevelRouteFor(SECTION_DAY))
        assertEquals(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER), ChronosRoute.topLevelRouteFor(SECTION_FOCUS))
        assertEquals(ChronosRoute.Tasks.route, ChronosRoute.topLevelRouteFor(SECTION_TASKS))
        assertEquals(ChronosRoute.Habits.route, ChronosRoute.topLevelRouteFor(SECTION_HABITS))
        assertEquals(ChronosRoute.Medication.route, ChronosRoute.topLevelRouteFor(SECTION_MEDICATION))
        assertEquals(
            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS),
            ChronosRoute.topLevelRouteFor(SECTION_REVIEW)
        )
    }
}
