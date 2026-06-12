package com.chronosflow.navigation

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider
import com.chronosflow.core.ui.settings.ChronosFeatureFlags

fun quickCreateCommandProvider(
    onNewBlock: () -> Unit,
    onNewTask: () -> Unit,
    onNewFocus: () -> Unit,
    onNewHabit: () -> Unit,
    onNewGoal: () -> Unit,
    onNewMedication: () -> Unit,
    onNewJournal: () -> Unit,
    onLogSleep: () -> Unit,
    onOpenRoutines: () -> Unit,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
): CommandProvider = CommandProvider {
    buildList {
        add(
        CommandPaletteItem(
            id = "quick.create-block",
            title = "New block",
            subtitle = "Add a time block on today's dial",
            keywords = setOf("add", "block", "create", "new", "time", "schedule"),
            group = CommandPaletteGroups.QUICK_CREATE,
            shortcutLabel = "Block",
            priority = 95,
            onRun = onNewBlock
        ),
        )
        add(
        CommandPaletteItem(
            id = "quick.create-task",
            title = "New task",
            subtitle = "Capture a task in the inbox",
            keywords = setOf("add", "task", "create", "new", "todo", "inbox"),
            group = CommandPaletteGroups.QUICK_CREATE,
            shortcutLabel = "Task",
            priority = 90,
                onRun = onNewTask
            )
        )
        add(
            CommandPaletteItem(
                id = "quick.create-focus",
                title = "New focus session",
                subtitle = "Open the focus planner for the next protected block",
                keywords = setOf("add", "create", "new", "focus", "session", "deep work", "timer"),
                group = CommandPaletteGroups.QUICK_CREATE,
                shortcutLabel = "Focus",
                priority = 88,
                onRun = onNewFocus
            )
        )
        if (featureFlags.habitsEnabled) {
            add(
                CommandPaletteItem(
                    id = "quick.create-habit",
                    title = "New habit",
                    subtitle = "Track a repeatable routine",
                    keywords = setOf("add", "habit", "create", "new", "routine", "streak"),
                    group = CommandPaletteGroups.QUICK_CREATE,
                    shortcutLabel = "Habit",
                    priority = 85,
                    onRun = onNewHabit
                )
            )
        }
        if (featureFlags.goalsEnabled) {
            add(
                CommandPaletteItem(
                    id = "quick.create-goal",
                    title = "New goal",
                    subtitle = "Create a measurable objective",
                    keywords = setOf("add", "goal", "create", "new", "milestone", "objective"),
                    group = CommandPaletteGroups.QUICK_CREATE,
                    shortcutLabel = "Goal",
                    priority = 84,
                    onRun = onNewGoal
                )
            )
        }
        if (featureFlags.medicationEnabled) {
            add(
                CommandPaletteItem(
                    id = "quick.create-medication",
                    title = "New medication",
                    subtitle = "Add a medication plan and reminder",
                    keywords = setOf("add", "medication", "meds", "create", "new", "dose", "reminder"),
                    group = CommandPaletteGroups.QUICK_CREATE,
                    shortcutLabel = "Meds",
                    priority = 80,
                    onRun = onNewMedication
                )
            )
        }
        if (featureFlags.reviewEnabled) {
            add(
                CommandPaletteItem(
                    id = "quick.create-journal",
                    title = "Journal entry",
                    subtitle = "Write a quick reflection for today",
                    keywords = setOf("add", "journal", "create", "new", "reflect", "diary", "note"),
                    group = CommandPaletteGroups.QUICK_CREATE,
                    shortcutLabel = "Journal",
                    priority = 78,
                    onRun = onNewJournal
                )
            )
            add(
                CommandPaletteItem(
                    id = "quick.create-sleep",
                    title = "Log sleep",
                    subtitle = "Record last night's sleep",
                    keywords = setOf("add", "sleep", "log", "new", "night", "rest", "bedtime"),
                    group = CommandPaletteGroups.QUICK_CREATE,
                    shortcutLabel = "Sleep",
                    priority = 76,
                    onRun = onLogSleep
                )
            )
        }
        add(
            CommandPaletteItem(
                id = "quick.open-routines",
                title = "Apply routine",
                subtitle = "Apply a saved routine to today's plan",
                keywords = setOf("apply", "routine", "template", "preset", "plan", "open"),
                group = CommandPaletteGroups.QUICK_CREATE,
                shortcutLabel = "Routine",
                priority = 74,
                onRun = onOpenRoutines
            )
        )
    }
}
