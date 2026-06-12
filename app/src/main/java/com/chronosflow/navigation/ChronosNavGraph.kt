package com.chronosflow.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.feature.daydial.DayDialScreen
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.goals.GoalScreen
import com.chronosflow.feature.habits.HabitScreen
import com.chronosflow.core.data.security.SensitiveArea
import com.chronosflow.feature.medication.MedicationScreen
import com.chronosflow.feature.review.ReviewScreen
import com.chronosflow.feature.tasks.TaskScreen
import java.net.URLDecoder

/**
 * Returns true when both the source and destination routes belong to the Day composable
 * (i.e. plan / today / focus-planner tab switches). In that case the NavHost-level transition
 * is suppressed so it does not double-stack with the AnimatedContent transition that
 * DayDialMainContent runs internally.
 */
internal fun isWithinDayRoute(from: String?, to: String?): Boolean {
    val dayBase = ChronosRoute.Day.section
    return from?.startsWith(dayBase) == true && to?.startsWith(dayBase) == true
}

/**
 * Returns true when both sides are top-level shell destinations navigated via the bottom bar
 * (i.e. no real back-stack relationship between them). These transitions should use a simple
 * cross-fade rather than a directional slide to avoid double-animation artifacts.
 *
 * Specifically: any forward navigation where neither side is within a Day sub-route counts
 * as a peer/shell switch.
 */
internal fun isShellPeerNavigation(from: String?, to: String?): Boolean {
    // Day-internal is already handled by isWithinDayRoute and uses None.
    if (isWithinDayRoute(from, to)) return false
    // Both sides are top-level sections — no back-stack hierarchy involved.
    val shellSections = setOf(
        ChronosRoute.Day.section,
        ChronosRoute.Tasks.section,
        ChronosRoute.Habits.section,
        ChronosRoute.Goals.section,
        ChronosRoute.Medication.section,
    )
    val fromSection = from?.substringBefore("?")?.substringBefore("/")
    val toSection = to?.substringBefore("?")?.substringBefore("/")
    return fromSection in shellSections && toSection in shellSections
}

private val daySidebarLaunchTargets = setOf(
    ChronosRoute.Day.TARGET_DAY_TOOLS,
    ChronosRoute.Day.TARGET_TASKS,
    ChronosRoute.Day.TARGET_HABITS,
    ChronosRoute.Day.TARGET_MEDICATION,
    ChronosRoute.Day.TARGET_REVIEW,
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

@Composable
fun ChronosNavGraph(
    navController: NavHostController,
    startRoute: String,
    contentPadding: PaddingValues,
    onOpenCommandPalette: () -> Unit,
    onOpenMedication: () -> Unit,
    modifier: Modifier = Modifier,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled,
    reducedMotion: Boolean = false,
    requestedPrimaryTab: DayDialTab? = null,
    shellDayTarget: String? = null,
    onDayPrimaryTabSelected: (DayDialTab) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            chronosRouteTransition(
                from = initialState.destination.route,
                to = targetState.destination.route,
                operation = ChronosNavigationOperation.Push,
                reducedMotion = reducedMotion
            ).enter
        },
        exitTransition = {
            chronosRouteTransition(
                from = initialState.destination.route,
                to = targetState.destination.route,
                operation = ChronosNavigationOperation.Push,
                reducedMotion = reducedMotion
            ).exit
        },
        popEnterTransition = {
            chronosRouteTransition(
                from = initialState.destination.route,
                to = targetState.destination.route,
                operation = ChronosNavigationOperation.Pop,
                reducedMotion = reducedMotion
            ).enter
        },
        popExitTransition = {
            chronosRouteTransition(
                from = initialState.destination.route,
                to = targetState.destination.route,
                operation = ChronosNavigationOperation.Pop,
                reducedMotion = reducedMotion
            ).exit
        }
    ) {
        composable(
            route = ChronosRoute.Day.route,
            arguments = listOf(
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("capture") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val launchTarget = dayLaunchTargetForRoute(
                routeTarget = it.arguments?.getString("target"),
                shellDayTarget = shellDayTarget
            )
            val capture = decodeCaptureArgument(it.arguments?.getString("capture"))
            DayDialScreen(
                launchTarget = launchTarget,
                initialFocusCapture = capture,
                requestedPrimaryTab = requestedPrimaryTab,
                contentPadding = contentPadding,
                onOpenFocusScreen = {
                    navController.navigateSingleTop(
                        ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                    )
                },
                onOpenTasks = {
                    navController.navigateSingleTop(ChronosRoute.Tasks.route)
                },
                onOpenHabits = {
                    if (featureFlags.habitsEnabled) {
                        navController.navigateSingleTop(ChronosRoute.Habits.route)
                    }
                },
                onOpenGoals = {
                    if (featureFlags.goalsEnabled) {
                        navController.navigateSingleTop(ChronosRoute.Goals.route)
                    }
                },
                onOpenMedication = onOpenMedication,
                onOpenReview = {
                    if (featureFlags.reviewEnabled) {
                        navController.navigateSingleTop(ChronosRoute.ReviewDetail.route)
                    } else {
                        navController.navigateDayTarget(ChronosRoute.Day.TARGET_INSIGHTS)
                    }
                },
                onSelectPrimaryTab = { tab ->
                    onDayPrimaryTabSelected(tab)
                },
                onOpenCommandPalette = onOpenCommandPalette
            )
        }
        composable(ChronosRoute.Tasks.route) {
            PaddedDestination(contentPadding) {
                TaskScreen(
                    onBack = { navController.navigateBackToDay() },
                    onOpenDayDial = { navController.navigateSingleTop(taskScreenDayDialRoute()) }
                )
            }
        }
        composable(
            route = ChronosRoute.Tasks.contextRoute,
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("capture") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val target = it.arguments?.getString("target")
            val capture = decodeCaptureArgument(it.arguments?.getString("capture"))
            PaddedDestination(contentPadding) {
                TaskScreen(
                    onBack = { navController.navigateBackToDay() },
                    onOpenDayDial = { navController.navigateSingleTop(taskScreenDayDialRoute()) },
                    initialContextTaskId = it.arguments?.getString("taskId")?.takeIf { taskId -> taskId.isNotBlank() },
                    openInitialContextSheet = target == "context",
                    openAddSheet = target == ChronosRoute.TARGET_ADD,
                    initialAddCapture = capture
                )
            }
        }
        composable(ChronosRoute.Habits.route) {
            PaddedDestination(contentPadding) {
                if (featureFlags.habitsEnabled) {
                    HabitScreen(
                        onBack = { navController.navigateBackToDay() }
                    )
                } else {
                    ParkedFeatureDestination("Habits")
                }
            }
        }
        composable(ChronosRoute.ReviewDetail.route) {
            PaddedDestination(contentPadding) {
                if (featureFlags.reviewEnabled) {
                    ReviewScreen(
                        onBack = { navController.navigateBackToDay() }
                    )
                } else {
                    ParkedFeatureDestination("Review")
                }
            }
        }
        composable(ChronosRoute.Goals.route) {
            PaddedDestination(contentPadding) {
                if (featureFlags.goalsEnabled) {
                    GoalScreen(
                        onBack = { navController.navigateBackToDay() }
                    )
                } else {
                    ParkedFeatureDestination("Goals")
                }
            }
        }
        composable(
            route = ChronosRoute.Goals.contextRoute,
            arguments = listOf(
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("capture") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val target = it.arguments?.getString("target")
            val capture = decodeCaptureArgument(it.arguments?.getString("capture"))
            PaddedDestination(contentPadding) {
                if (featureFlags.goalsEnabled) {
                    GoalScreen(
                        onBack = { navController.navigateBackToDay() },
                        openAddSheet = target == ChronosRoute.TARGET_ADD,
                        initialAddCapture = capture
                    )
                } else {
                    ParkedFeatureDestination("Goals")
                }
            }
        }
        composable(
            route = ChronosRoute.Habits.contextRoute,
            arguments = listOf(
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("capture") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val target = it.arguments?.getString("target")
            val capture = decodeCaptureArgument(it.arguments?.getString("capture"))
            PaddedDestination(contentPadding) {
                if (featureFlags.habitsEnabled) {
                    HabitScreen(
                        onBack = { navController.navigateBackToDay() },
                        openAddSheet = target == ChronosRoute.TARGET_ADD,
                        initialAddCapture = capture
                    )
                } else {
                    ParkedFeatureDestination("Habits")
                }
            }
        }
        composable(ChronosRoute.Medication.route) {
            PaddedDestination(contentPadding) {
                if (featureFlags.medicationEnabled) {
                    SensitiveRouteGate(
                        area = SensitiveArea.MEDICATION,
                        title = "Medications are protected",
                        message = "Unlock to view and manage your medication plans and dose history."
                    ) {
                        MedicationScreen(
                            onBack = { navController.navigateBackToDay() }
                        )
                    }
                } else {
                    ParkedFeatureDestination("Medications")
                }
            }
        }
        composable(
            route = ChronosRoute.Medication.contextRoute,
            arguments = listOf(
                navArgument("target") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("capture") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val target = it.arguments?.getString("target")
            val capture = decodeCaptureArgument(it.arguments?.getString("capture"))
            PaddedDestination(contentPadding) {
                if (featureFlags.medicationEnabled) {
                    SensitiveRouteGate(
                        area = SensitiveArea.MEDICATION,
                        title = "Medications are protected",
                        message = "Unlock to view and manage your medication plans and dose history."
                    ) {
                        MedicationScreen(
                            onBack = { navController.navigateBackToDay() },
                            openAddSheet = target == ChronosRoute.TARGET_ADD,
                            initialAddCapture = capture
                        )
                    }
                } else {
                    ParkedFeatureDestination("Medications")
                }
            }
        }
    }
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
private fun ParkedFeatureDestination(name: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
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

internal fun disabledFeatureTitle(name: String): String = "$name is disabled"

internal fun disabledFeatureMessage(): String = "Enable it from Developer settings to show this module."

fun NavHostController.navigateSingleTop(route: String) {
    val policy = navigationPolicyForRoute(route)
    navigate(route) {
        launchSingleTop = policy.launchSingleTop
        restoreState = policy.restoreState
        policy.popUpToRoute?.let { popUpToRoute ->
            popUpTo(popUpToRoute) {
                saveState = policy.saveState
            }
        }
    }
}

fun NavHostController.navigateDayTarget(dayTarget: String) {
    val route = ChronosRoute.Day.createRoute(dayTarget)
    navigateSingleTop(route)
}

internal data class NavigationRoutePolicy(
    val launchSingleTop: Boolean,
    val restoreState: Boolean,
    val popUpToRoute: String?,
    val saveState: Boolean
)

internal fun navigationPolicyForRoute(route: String): NavigationRoutePolicy {
    val isOneShot = route.isOneShotSheetRoute()
    val isDayPrimaryTab = route.isDayPrimaryTabRoute()
    val isBackStackedDayTarget = route.isBackStackedDayTargetRoute()
    return NavigationRoutePolicy(
        launchSingleTop = !isOneShot && !isBackStackedDayTarget,
        restoreState = !isOneShot && !isDayPrimaryTab && !isBackStackedDayTarget,
        popUpToRoute = if (isBackStackedDayTarget) null else ChronosRoute.Day.route,
        saveState = !isOneShot && !isDayPrimaryTab && !isBackStackedDayTarget
    )
}

internal fun shouldLaunchSingleTopForRoute(route: String): Boolean =
    navigationPolicyForRoute(route).launchSingleTop

private fun String.isOneShotSheetRoute(): Boolean =
    contains("target=${ChronosRoute.TARGET_ADD}") ||
        contains("target=${ChronosRoute.Day.TARGET_ADD_BLOCK}") ||
        contains("target=context")

private fun String.isDayPrimaryTabRoute(): Boolean =
    this == ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY) ||
        this == ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN) ||
        this == ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER) ||
        startsWith("${ChronosRoute.Day.section}?target=${ChronosRoute.Day.TARGET_FOCUS_PLANNER}&capture=")

private fun String.isBackStackedDayTargetRoute(): Boolean =
    this == ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)

private fun NavHostController.navigateBackToDay() {
    val previousDayTarget = previousBackStackEntry
        ?.takeIf { it.destination.route == ChronosRoute.Day.route }
        ?.arguments
        ?.getString("target")
        ?.takeIf { it.isNotBlank() }

    if (!popBackStack(ChronosRoute.Day.route, inclusive = false)) {
        val dayTarget = if (previousDayTarget == ChronosRoute.Day.TARGET_PLAN) {
            ChronosRoute.Day.TARGET_PLAN
        } else {
            ChronosRoute.Day.TARGET_TODAY
        }
        navigateSingleTop(ChronosRoute.Day.createRoute(dayTarget))
    }
}

internal fun taskScreenDayDialRoute(): String =
    ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)

private fun decodeCaptureArgument(value: String?): String? {
    val capture = value?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        URLDecoder.decode(capture, Charsets.UTF_8.toString())
    }.getOrDefault(capture).takeIf(String::isNotBlank)
}
