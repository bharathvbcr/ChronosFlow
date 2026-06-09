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
    onNewMedication: () -> Unit,
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
    }
}
