package com.chronosflow.feature.goals

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider

fun goalCommandProvider(onOpenGoals: () -> Unit): CommandProvider = CommandProvider {
    listOf(
        CommandPaletteItem(
            id = "goals.open",
            title = "Open goals",
            subtitle = "Track long-term objectives and progress",
            keywords = setOf("goal", "goals", "milestone", "objective", "progress"),
            group = CommandPaletteGroups.SUPPORTING,
            shortcutLabel = "Goals",
            priority = 74,
            onRun = onOpenGoals
        )
    )
}
