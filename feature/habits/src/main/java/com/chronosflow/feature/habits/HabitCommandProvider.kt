package com.chronosflow.feature.habits

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider

fun habitCommandProvider(onOpenHabits: () -> Unit): CommandProvider = CommandProvider {
    listOf(
        CommandPaletteItem(
            id = "habits.open",
            title = "Open habits",
            subtitle = "Track streaks and recovery suggestions",
            keywords = setOf("habit", "habits", "streak", "routine"),
            group = CommandPaletteGroups.SUPPORTING,
            shortcutLabel = "Habits",
            priority = 75,
            onRun = onOpenHabits
        )
    )
}
