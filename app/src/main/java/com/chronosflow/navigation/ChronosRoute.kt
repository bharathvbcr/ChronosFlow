package com.chronosflow.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.notifications.SECTION_DAY
import com.chronosflow.core.notifications.SECTION_FOCUS
import com.chronosflow.core.notifications.SECTION_MEDICATION
import com.chronosflow.core.notifications.SECTION_REVIEW
import com.chronosflow.core.notifications.SECTION_TASKS
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import java.net.URLEncoder

const val SECTION_HABITS = "habits"

sealed interface ChronosRoute {
    val route: String
    val section: String

    data class ShellDestination(
        val id: String,
        val label: String,
        val icon: ImageVector,
        val route: String,
        val section: String,
        val dayTarget: String? = null,
        val showInCompact: Boolean = true
    )

    data object Day : ChronosRoute {
        override val route: String = "$SECTION_DAY?target={target}&capture={capture}"
        override val section: String = SECTION_DAY

        const val TARGET_TODAY = "today"
        const val TARGET_PLAN = "plan"
        const val TARGET_FOCUS_PLANNER = "focus-planner"
        const val TARGET_DAY_TOOLS = "day-tools"
        const val TARGET_TASKS = "tasks"
        const val TARGET_HABITS = "habits"
        const val TARGET_MEDICATION = "medication"
        const val TARGET_TEMPLATES = "templates"
        const val TARGET_AI_SETTINGS = "ai-settings"
        const val TARGET_PRIVACY_SYNC = "privacy-sync"
        const val TARGET_NOTIFICATIONS = "notifications"
        const val TARGET_APPEARANCE = "appearance"
        const val TARGET_ADD_BLOCK = "add-block"

        /** The Review page (execution score, planned vs actual, insights, recommendations). */
        const val TARGET_INSIGHTS = "insights"

        fun createRoute(target: String? = null, capture: String? = null): String {
            if (target == null && capture == null) return SECTION_DAY
            return buildString {
                append(SECTION_DAY)
                append("?target=")
                append(target?.let(::encodeRouteValue).orEmpty())
                if (capture != null) {
                    append("&capture=")
                    append(encodeRouteValue(capture))
                }
            }
        }
    }

    data object Focus : ChronosRoute {
        override val route: String = Day.createRoute(Day.TARGET_FOCUS_PLANNER)
        override val section: String = SECTION_FOCUS

        fun createRoute(): String =
            Day.createRoute(Day.TARGET_FOCUS_PLANNER)
    }

    data object Tasks : ChronosRoute {
        override val route: String = SECTION_TASKS
        override val section: String = SECTION_TASKS

        const val contextRoute: String = "$SECTION_TASKS?taskId={taskId}&target={target}&capture={capture}"

        fun createRoute(taskId: String? = null, target: String? = null, capture: String? = null): String {
            if (taskId == null && target == null && capture == null) return SECTION_TASKS
            return buildString {
                append(SECTION_TASKS)
                append("?")
                append("taskId=")
                append(taskId?.let(::encodeRouteValue).orEmpty())
                append("&target=")
                append(target?.let(::encodeRouteValue).orEmpty())
                if (capture != null) {
                    append("&capture=")
                    append(encodeRouteValue(capture))
                }
            }
        }
    }

    data object Habits : ChronosRoute {
        override val route: String = SECTION_HABITS
        override val section: String = SECTION_HABITS

        const val contextRoute: String = "$SECTION_HABITS?target={target}&capture={capture}"

        fun createRoute(target: String? = null, capture: String? = null): String =
            if (target == null && capture == null) {
                SECTION_HABITS
            } else if (capture == null) {
                "$SECTION_HABITS?target=${target?.let(::encodeRouteValue).orEmpty()}"
            } else {
                "$SECTION_HABITS?target=${target?.let(::encodeRouteValue).orEmpty()}" +
                    "&capture=${encodeRouteValue(capture)}"
            }
    }

    data object Medication : ChronosRoute {
        override val route: String = SECTION_MEDICATION
        override val section: String = SECTION_MEDICATION

        const val contextRoute: String = "$SECTION_MEDICATION?target={target}&capture={capture}"

        fun createRoute(target: String? = null, capture: String? = null): String =
            if (target == null && capture == null) {
                SECTION_MEDICATION
            } else if (capture == null) {
                "$SECTION_MEDICATION?target=${target?.let(::encodeRouteValue).orEmpty()}"
            } else {
                "$SECTION_MEDICATION?target=${target?.let(::encodeRouteValue).orEmpty()}" +
                    "&capture=${encodeRouteValue(capture)}"
            }
    }

    data object Review : ChronosRoute {
        override val route: String = Day.createRoute(Day.TARGET_INSIGHTS)
        override val section: String = SECTION_REVIEW
    }

    companion object {
        const val SHELL_TODAY = "today"
        const val SHELL_PLAN = "plan"
        const val SHELL_FOCUS = "focus"
        const val SHELL_TASKS = "tasks"
        const val SHELL_HABITS = "habits"
        const val SHELL_MEDICATION = "medication"
        const val SHELL_REVIEW = "review"
        const val TARGET_ADD = "add"

        val all: List<ChronosRoute> = listOf(Day, Focus, Tasks, Habits, Medication, Review)
        val shellDestinations: List<ShellDestination> = listOf(
            ShellDestination(
                id = SHELL_PLAN,
                label = "Plan",
                icon = Icons.AutoMirrored.Filled.EventNote,
                route = Day.createRoute(Day.TARGET_PLAN),
                section = Day.section,
                dayTarget = Day.TARGET_PLAN
            ),
            ShellDestination(
                id = SHELL_TODAY,
                label = "Today",
                icon = Icons.Default.Today,
                route = Day.createRoute(Day.TARGET_TODAY),
                section = Day.section,
                dayTarget = Day.TARGET_TODAY
            ),
            ShellDestination(
                id = SHELL_FOCUS,
                label = "Focus",
                icon = Icons.Default.Timer,
                route = Day.createRoute(Day.TARGET_FOCUS_PLANNER),
                section = Focus.section,
                dayTarget = Day.TARGET_FOCUS_PLANNER
            ),
            ShellDestination(
                id = SHELL_TASKS,
                label = "Tasks",
                icon = Icons.Default.Checklist,
                route = Tasks.route,
                section = Tasks.section,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_HABITS,
                label = "Habits",
                icon = Icons.Default.Favorite,
                route = Habits.route,
                section = Habits.section,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_MEDICATION,
                label = "Meds",
                icon = Icons.Default.Medication,
                route = Medication.route,
                section = Medication.section,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_REVIEW,
                label = "Review",
                icon = Icons.Default.Assessment,
                // Review resolves to the Insights tab — a real, persistent page — rather
                // than the transient review sheet. Routing a sheet through a sticky shell
                // day-target left the destination "selected" after dismissal and no-op'd on
                // a repeat tap; a normal tab navigates reliably every time. The detailed
                // planned/actual/missed sheet is reachable from within that page.
                route = Day.createRoute(Day.TARGET_INSIGHTS),
                section = Review.section,
                dayTarget = Day.TARGET_INSIGHTS,
                showInCompact = false
            )
        )
        fun fromSection(section: String?): ChronosRoute =
            all.firstOrNull { it.section == section } ?: Day

        fun compactShellDestinations(
            featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
        ): List<ShellDestination> =
            shellDestinations.filter { it.showInCompact && it.isAvailable(featureFlags) }

        fun expandedShellDestinations(
            featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
        ): List<ShellDestination> = shellDestinations.filter { it.isAvailable(featureFlags) }

        fun shellDestinationFor(
            section: String?,
            dayTarget: String?,
            featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
        ): ShellDestination =
            expandedShellDestinations(featureFlags).firstOrNull { destination ->
                when (destination.id) {
                    SHELL_TODAY -> section == Day.section && (
                            dayTarget == null ||
                                dayTarget == Day.TARGET_TODAY ||
                            dayTarget !in setOf(
                                Day.TARGET_PLAN,
                                Day.TARGET_FOCUS_PLANNER,
                                Day.TARGET_INSIGHTS,
                                Day.TARGET_TASKS,
                                Day.TARGET_HABITS,
                                Day.TARGET_MEDICATION
                            )
                        )
                    SHELL_PLAN -> section == Day.section && dayTarget == Day.TARGET_PLAN
                    SHELL_FOCUS -> section == Focus.section || (
                        section == Day.section && dayTarget == Day.TARGET_FOCUS_PLANNER
                        )
                    SHELL_TASKS -> section == Tasks.section || (
                        section == Day.section && dayTarget == Day.TARGET_TASKS
                        )
                    SHELL_HABITS -> section == Habits.section || (
                        section == Day.section && dayTarget == Day.TARGET_HABITS
                        )
                    SHELL_MEDICATION -> section == Medication.section || (
                        section == Day.section && dayTarget == Day.TARGET_MEDICATION
                        )
                    SHELL_REVIEW -> section == Review.section || (
                        section == Day.section && dayTarget == Day.TARGET_INSIGHTS
                        )
                    else -> destination.section == section
                }
            } ?: shellDestinations.first()

        fun routeForNotificationLaunch(
            launch: NotificationLaunch,
            featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
        ): String =
            when (launch.section) {
                Medication.section -> if (featureFlags.medicationEnabled) Medication.route else Day.createRoute()
                Review.section -> if (featureFlags.reviewEnabled) Review.route else Day.createRoute()
                Tasks.section -> Tasks.createRoute(launch.taskId, launch.target)
                Habits.section -> if (featureFlags.habitsEnabled) Habits.route else Day.createRoute()
                Focus.section -> Day.createRoute(Day.TARGET_FOCUS_PLANNER)
                Day.section -> Day.createRoute(launch.dayTarget)
                else -> Day.createRoute(launch.dayTarget)
            }

        fun topLevelRouteFor(section: String): String =
            when (section) {
                Tasks.section -> Tasks.route
                Habits.section -> Habits.route
                Medication.section -> Medication.route
                Review.section -> Day.createRoute(Day.TARGET_INSIGHTS)
                else -> routeForNotificationLaunch(NotificationLaunch(section = section))
            }
    }
}

private fun ChronosRoute.ShellDestination.isAvailable(featureFlags: ChronosFeatureFlags): Boolean {
    return when (id) {
        ChronosRoute.SHELL_HABITS -> featureFlags.habitsEnabled
        ChronosRoute.SHELL_MEDICATION -> featureFlags.medicationEnabled
        ChronosRoute.SHELL_REVIEW -> featureFlags.reviewEnabled
        else -> true
    }
}

private fun encodeRouteValue(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.toString()).replace("+", "%20")
