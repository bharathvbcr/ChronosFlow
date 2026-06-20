package com.ChronosFlow.VBCR.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.SECTION_DAY
import com.ChronosFlow.VBCR.core.notifications.SECTION_FOCUS
import com.ChronosFlow.VBCR.core.notifications.SECTION_MEDICATION
import com.ChronosFlow.VBCR.core.notifications.SECTION_REVIEW
import com.ChronosFlow.VBCR.core.notifications.SECTION_TASKS
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import kotlinx.serialization.Serializable

const val SECTION_HABITS = "habits"
const val SECTION_GOALS = "goals"

/**
 * Type-safe Navigation 3 keys for ChronosFlow's top-level destinations.
 *
 * Each route is a [NavKey] so it can be stored directly in a [androidx.navigation3.runtime.NavBackStack]
 * and serialized across config changes / process death. Routes carry their navigation arguments as
 * strongly-typed nullable fields instead of URL-encoded query strings.
 *
 * The Day destination is parameterized by [Day.target]: Plan / Today / Focus / Review are *in-place*
 * tabs inside DayDialScreen rather than separate destinations (see the navigation shell), so they all
 * resolve to the single [Day] key with a different target.
 */
sealed interface ChronosRoute : NavKey {
    val section: String

    @Serializable
    data class Day(val target: String? = null, val capture: String? = null) : ChronosRoute {
        override val section: String get() = SECTION_DAY

        companion object {
            const val section: String = SECTION_DAY

            const val TARGET_TODAY = "today"

            /**
             * Like [TARGET_TODAY] but also snaps the dial back to the current date. Used by the
             * Today bottom-bar double-tap so that, after browsing to another day, returning to
             * Today actually jumps to today rather than just re-selecting the Today tab.
             */
            const val TARGET_TODAY_RESET = "today-reset"
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
            const val TARGET_JOURNAL = "journal"
            const val TARGET_SLEEP = "sleep"

            fun createRoute(target: String? = null, capture: String? = null): Day = Day(target, capture)
        }
    }

    @Serializable
    data class Tasks(
        val taskId: String? = null,
        val target: String? = null,
        val capture: String? = null,
        val bulkCapture: List<String>? = null,
        /** Human-readable label of the app that shared content via ACTION_SEND/ACTION_PROCESS_TEXT. */
        val sourceAppLabel: String? = null
    ) : ChronosRoute {
        override val section: String get() = SECTION_TASKS

        companion object {
            const val section: String = SECTION_TASKS

            fun createRoute(
                taskId: String? = null,
                target: String? = null,
                capture: String? = null,
                bulkCapture: List<String>? = null,
                sourceAppLabel: String? = null
            ): Tasks = Tasks(taskId, target, capture, bulkCapture, sourceAppLabel)
        }
    }

    @Serializable
    data class Habits(val target: String? = null, val capture: String? = null) : ChronosRoute {
        override val section: String get() = SECTION_HABITS

        companion object {
            const val section: String = SECTION_HABITS

            fun createRoute(target: String? = null, capture: String? = null): Habits = Habits(target, capture)
        }
    }

    @Serializable
    data class Goals(val target: String? = null, val capture: String? = null) : ChronosRoute {
        override val section: String get() = SECTION_GOALS

        companion object {
            const val section: String = SECTION_GOALS

            fun createRoute(target: String? = null, capture: String? = null): Goals = Goals(target, capture)
        }
    }

    @Serializable
    data class Medication(val target: String? = null, val capture: String? = null) : ChronosRoute {
        override val section: String get() = SECTION_MEDICATION

        companion object {
            const val section: String = SECTION_MEDICATION

            fun createRoute(target: String? = null, capture: String? = null): Medication = Medication(target, capture)
        }
    }

    /**
     * Focus is a Day tab (the focus planner), not a separate destination. Kept as a namespace so
     * existing `ChronosRoute.Focus.*` references keep resolving to the underlying Day key.
     */
    object Focus {
        const val section: String = SECTION_FOCUS

        fun createRoute(): Day = Day(Day.TARGET_FOCUS_PLANNER)
    }

    /**
     * Review resolves to the Insights Day tab — a real, persistent page — rather than the transient
     * review sheet. Kept as a namespace for the same reason as [Focus].
     */
    object Review {
        const val section: String = SECTION_REVIEW

        fun createRoute(): Day = Day(Day.TARGET_INSIGHTS)
    }

    data class ShellDestination(
        val id: String,
        val label: String,
        val icon: ImageVector,
        val route: ChronosRoute,
        val section: String,
        val dayTarget: String? = null,
        val showInCompact: Boolean = true
    )

    companion object {
        const val SHELL_TODAY = "today"
        const val SHELL_PLAN = "plan"
        const val SHELL_FOCUS = "focus"
        const val SHELL_TASKS = "tasks"
        const val SHELL_HABITS = "habits"
        const val SHELL_GOALS = "goals"
        const val SHELL_MEDICATION = "medication"
        const val SHELL_REVIEW = "review"
        const val TARGET_ADD = "add"

        /** Top-level sections, each backed by its own Nav3 back stack. */
        val topLevelSections: List<String> = listOf(
            SECTION_DAY,
            SECTION_TASKS,
            SECTION_HABITS,
            SECTION_GOALS,
            SECTION_MEDICATION
        )

        /** Base (argument-free) key for each top-level section. */
        val topLevelRoutes: List<ChronosRoute> = listOf(
            Day(),
            Tasks(),
            Habits(),
            Goals(),
            Medication()
        )

        val shellDestinations: List<ShellDestination> = listOf(
            ShellDestination(
                id = SHELL_PLAN,
                label = "Plan",
                icon = Icons.AutoMirrored.Filled.EventNote,
                route = Day(Day.TARGET_PLAN),
                section = SECTION_DAY,
                dayTarget = Day.TARGET_PLAN
            ),
            ShellDestination(
                id = SHELL_TODAY,
                label = "Today",
                icon = Icons.Default.Today,
                route = Day(Day.TARGET_TODAY),
                section = SECTION_DAY,
                dayTarget = Day.TARGET_TODAY
            ),
            ShellDestination(
                id = SHELL_FOCUS,
                label = "Focus",
                icon = Icons.Default.Timer,
                route = Day(Day.TARGET_FOCUS_PLANNER),
                section = SECTION_FOCUS,
                dayTarget = Day.TARGET_FOCUS_PLANNER
            ),
            ShellDestination(
                id = SHELL_TASKS,
                label = "Tasks",
                icon = Icons.Default.Checklist,
                route = Tasks(),
                section = SECTION_TASKS,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_HABITS,
                label = "Habits",
                icon = Icons.Default.Favorite,
                route = Habits(),
                section = SECTION_HABITS,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_GOALS,
                label = "Goals",
                icon = Icons.Default.Flag,
                route = Goals(),
                section = SECTION_GOALS,
                dayTarget = null,
                showInCompact = false
            ),
            ShellDestination(
                id = SHELL_MEDICATION,
                label = "Meds",
                icon = Icons.Default.Medication,
                route = Medication(),
                section = SECTION_MEDICATION,
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
                route = Day(Day.TARGET_INSIGHTS),
                section = SECTION_REVIEW,
                dayTarget = Day.TARGET_INSIGHTS,
                showInCompact = false
            )
        )

        fun fromSection(section: String?): ChronosRoute =
            topLevelRoutes.firstOrNull { it.section == section } ?: Day()

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
                    SHELL_TODAY -> section == SECTION_DAY && (
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
                    SHELL_PLAN -> section == SECTION_DAY && dayTarget == Day.TARGET_PLAN
                    SHELL_FOCUS -> section == SECTION_FOCUS || (
                        section == SECTION_DAY && dayTarget == Day.TARGET_FOCUS_PLANNER
                        )
                    SHELL_TASKS -> section == SECTION_TASKS || (
                        section == SECTION_DAY && dayTarget == Day.TARGET_TASKS
                        )
                    SHELL_HABITS -> section == SECTION_HABITS || (
                        section == SECTION_DAY && dayTarget == Day.TARGET_HABITS
                        )
                    SHELL_GOALS -> section == SECTION_GOALS
                    SHELL_MEDICATION -> section == SECTION_MEDICATION || (
                        section == SECTION_DAY && dayTarget == Day.TARGET_MEDICATION
                        )
                    SHELL_REVIEW -> section == SECTION_REVIEW || (
                        section == SECTION_DAY && dayTarget == Day.TARGET_INSIGHTS
                        )
                    else -> destination.section == section
                }
            } ?: shellDestinations.first()

        fun routeForNotificationLaunch(
            launch: NotificationLaunch,
            featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
        ): ChronosRoute =
            when (launch.section) {
                SECTION_MEDICATION -> if (featureFlags.medicationEnabled) Medication() else Day()
                SECTION_REVIEW -> if (featureFlags.reviewEnabled) Day(Day.TARGET_INSIGHTS) else Day()
                SECTION_TASKS -> Tasks(
                    taskId = launch.taskId,
                    target = launch.target,
                    capture = launch.capture,
                    bulkCapture = launch.bulkCapture,
                    sourceAppLabel = launch.sourceAppLabel
                )
                SECTION_HABITS -> if (featureFlags.habitsEnabled) Habits() else Day()
                SECTION_GOALS -> if (featureFlags.goalsEnabled) Goals() else Day()
                SECTION_FOCUS -> Day(Day.TARGET_FOCUS_PLANNER)
                SECTION_DAY -> Day(launch.dayTarget)
                else -> Day(launch.dayTarget)
            }

        fun topLevelRouteFor(section: String): ChronosRoute =
            when (section) {
                SECTION_TASKS -> Tasks()
                SECTION_HABITS -> Habits()
                SECTION_GOALS -> Goals()
                SECTION_MEDICATION -> Medication()
                SECTION_REVIEW -> Day(Day.TARGET_INSIGHTS)
                else -> routeForNotificationLaunch(NotificationLaunch(section = section))
            }
    }
}

private fun ChronosRoute.ShellDestination.isAvailable(featureFlags: ChronosFeatureFlags): Boolean {
    return when (id) {
        ChronosRoute.SHELL_HABITS -> featureFlags.habitsEnabled
        ChronosRoute.SHELL_GOALS -> featureFlags.goalsEnabled
        ChronosRoute.SHELL_MEDICATION -> featureFlags.medicationEnabled
        ChronosRoute.SHELL_REVIEW -> featureFlags.reviewEnabled
        else -> true
    }
}
