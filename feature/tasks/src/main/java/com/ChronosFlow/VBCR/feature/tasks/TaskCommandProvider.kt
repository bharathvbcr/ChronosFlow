package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteGroups
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteItem
import com.ChronosFlow.VBCR.core.ui.components.CommandProvider

fun taskCommandProvider(onOpenTasks: () -> Unit): CommandProvider = CommandProvider {
    listOf(
        CommandPaletteItem(
            id = "tasks.open",
            title = "Open tasks",
            subtitle = "Manage the task inbox and schedule work",
            keywords = setOf("task", "tasks", "todo", "inbox"),
            group = CommandPaletteGroups.SUPPORTING,
            shortcutLabel = "Tasks",
            priority = 80,
            onRun = onOpenTasks
        )
    )
}
