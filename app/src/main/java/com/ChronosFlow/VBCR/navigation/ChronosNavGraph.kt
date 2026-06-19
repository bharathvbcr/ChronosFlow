package com.ChronosFlow.VBCR.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ChronosFlow.VBCR.core.data.security.SensitiveArea
import com.ChronosFlow.VBCR.core.ui.components.ChronosScreenScaffold
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.chronosCrossSectionTransitionSet
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import com.ChronosFlow.VBCR.feature.daydial.DayDialScreen
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.goals.GoalScreen
import com.ChronosFlow.VBCR.feature.habits.HabitScreen
import com.ChronosFlow.VBCR.feature.medication.MedicationScreen
import com.ChronosFlow.VBCR.feature.tasks.TaskScreen

private val daySidebarLaunchTargets = setOf(
    ChronosRoute.Day.TARGET_DAY_TOOLS,
    ChronosRoute.Day.TARGET_TASKS,
    ChronosRoute.Day.TARGET_HABITS,
    ChronosRoute.Day.TARGET_MEDICATION,
    ChronosRoute.Day.TARGET_TEMPLATES,
    ChronosRoute.Day.TARGET_AI_SETTINGS,
    ChronosRoute.Day.TARGET_PRIVACY_SYNC,
    ChronosRoute.Day.TARGET_NOTIFICATIONS,
    ChronosRoute.Day.TARGET_APPEARANCE
)

internal fun isDaySidebarLaunchTarget(target: String?): Boolean =
    target in daySidebarLaunchTargets

internal fun dayLaunchTargetForRoute(
    routeTarget: String?,
    shellDayTarget: String?
): String? = routeTarget ?: shellDayTarget?.takeIf(::isDaySidebarLaunchTarget)

internal fun taskScreenDayDialTarget(): String = ChronosRoute.Day.TARGET_TODAY

/**
 * Renders the Navigation 3 [NavDisplay] for the ChronosFlow shell. The back stack only models
 * cross-section navigation (Day ↔ Tasks/Habits/Goals/Medication); Day tabs are surfaced in-place
 * inside [DayDialScreen] via [shellDayTarget].
 */
@Composable
fun ChronosNavDisplay(
    navState: ChronosNavigationState,
    contentPadding: PaddingValues,
    onOpenCommandPalette: () -> Unit,
    onOpenMedication: () -> Unit,
    modifier: Modifier = Modifier,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled,
    requestedPrimaryTab: DayDialTab? = null,
    shellDayTarget: String? = null,
    dayLaunchTargetGeneration: Int = 0,
    reducedMotion: Boolean = false,
    onDayPrimaryTabSelected: (DayDialTab) -> Unit = {}
) {
    NavDisplay(
        backStack = navState.backStack,
        onBack = { navState.goBack() },
        modifier = modifier.fillMaxSize(),
        // Cross-section pushes/pops (Day ↔ Tasks/Habits/Goals/Medication) use the shared-axis
        // motion from the design system. Day-internal tab switches don't change the back stack,
        // so DayDialScreen's own AnimatedContent still owns that motion.
        transitionSpec = { chronosCrossSectionTransform(reducedMotion, ChronosTransitionDirection.Forward) },
        popTransitionSpec = { chronosCrossSectionTransform(reducedMotion, ChronosTransitionDirection.Backward) },
        predictivePopTransitionSpec = { chronosCrossSectionTransform(reducedMotion, ChronosTransitionDirection.Backward) },
        entryProvider = entryProvider<NavKey> {
            entry<ChronosRoute.Day> {
                val launchTarget = dayLaunchTargetForRoute(
                    routeTarget = shellDayTarget,
                    shellDayTarget = shellDayTarget
                )
                DayDialScreen(
                    launchTarget = launchTarget,
                    launchTargetGeneration = dayLaunchTargetGeneration,
                    initialFocusCapture = navState.requestedDayCapture,
                    requestedPrimaryTab = requestedPrimaryTab,
                    contentPadding = contentPadding,
                    onOpenFocusScreen = {
                        navState.selectDayTarget(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                    },
                    onOpenTasks = { navState.navigate(ChronosRoute.Tasks()) },
                    onOpenHabits = {
                        if (featureFlags.habitsEnabled) navState.navigate(ChronosRoute.Habits())
                    },
                    onOpenGoals = {
                        if (featureFlags.goalsEnabled) navState.navigate(ChronosRoute.Goals())
                    },
                    onOpenMedication = onOpenMedication,
                    onOpenReview = {
                        navState.selectDayTarget(ChronosRoute.Day.TARGET_INSIGHTS)
                    },
                    onSelectPrimaryTab = onDayPrimaryTabSelected,
                    onOpenCommandPalette = onOpenCommandPalette,
                    // During a predictive-back swipe out of a sub-section, NavDisplay composes the
                    // incoming Day screen for the preview while topLevelSection is still the
                    // sub-section. Telling Day it isn't the active section keeps its in-place modal
                    // sheet host and tab-back handler inert so they can't swallow the back gesture.
                    isActiveSection = navState.topLevelSection == ChronosRoute.Day.section
                )
            }
            entry<ChronosRoute.Tasks> { key ->
                PaddedDestination(contentPadding) {
                    TaskScreen(
                        onBack = { navState.goBack() },
                        onOpenDayDial = { navState.selectDayTarget(taskScreenDayDialTarget()) },
                        onOpenCommandPalette = onOpenCommandPalette,
                        initialContextTaskId = key.taskId?.takeIf { it.isNotBlank() },
                        openInitialContextSheet = key.target == "context",
                        openAddSheet = key.target == ChronosRoute.TARGET_ADD,
                        initialAddCapture = key.capture,
                        navTargetGeneration = navState.sectionTargetGeneration
                    )
                }
            }
            entry<ChronosRoute.Habits> { key ->
                PaddedDestination(contentPadding) {
                    if (featureFlags.habitsEnabled) {
                        HabitScreen(
                            onBack = { navState.goBack() },
                            onOpenCommandPalette = onOpenCommandPalette,
                            openAddSheet = key.target == ChronosRoute.TARGET_ADD,
                            initialAddCapture = key.capture,
                            navTargetGeneration = navState.sectionTargetGeneration
                        )
                    } else {
                        ParkedFeatureDestination("Habits", onBack = { navState.goBack() })
                    }
                }
            }
            entry<ChronosRoute.Goals> { key ->
                PaddedDestination(contentPadding) {
                    if (featureFlags.goalsEnabled) {
                        GoalScreen(
                            onBack = { navState.goBack() },
                            onOpenCommandPalette = onOpenCommandPalette,
                            openAddSheet = key.target == ChronosRoute.TARGET_ADD,
                            initialAddCapture = key.capture,
                            navTargetGeneration = navState.sectionTargetGeneration
                        )
                    } else {
                        ParkedFeatureDestination("Goals")
                    }
                }
            }
            entry<ChronosRoute.Medication> { key ->
                PaddedDestination(contentPadding) {
                    if (featureFlags.medicationEnabled) {
                        SensitiveRouteGate(
                            area = SensitiveArea.MEDICATION,
                            title = "Medications are protected",
                            message = "Unlock to view and manage your medication plans and dose history."
                        ) {
                            MedicationScreen(
                                onBack = { navState.goBack() },
                                onOpenCommandPalette = onOpenCommandPalette,
                                openAddSheet = key.target == ChronosRoute.TARGET_ADD,
                                initialAddCapture = key.capture,
                                navTargetGeneration = navState.sectionTargetGeneration
                            )
                        }
                    } else {
                        ParkedFeatureDestination("Medications", onBack = { navState.goBack() })
                    }
                }
            }
        }
    )
}

private fun chronosCrossSectionTransform(
    reducedMotion: Boolean,
    direction: ChronosTransitionDirection
): ContentTransform {
    // Shared source of truth with the in-shell sidebar pages (chronosCrossSectionTransitionSet)
    // so NavDisplay routes and in-place destinations animate identically.
    val set = chronosCrossSectionTransitionSet(direction = direction, reducedMotion = reducedMotion)
    return set.enter togetherWith set.exit
}

@Composable
private fun PaddedDestination(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
private fun ParkedFeatureDestination(name: String, onBack: (() -> Unit)? = null) {
    ChronosScreenScaffold(
        title = name,
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = disabledFeatureTitle(name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = disabledFeatureMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun disabledFeatureTitle(name: String): String = "$name is disabled"

internal fun disabledFeatureMessage(): String = "Enable it from Developer settings to show this module."
