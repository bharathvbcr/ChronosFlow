package com.ChronosFlow.VBCR.feature.goals

import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteGroups
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteItem
import com.ChronosFlow.VBCR.core.ui.components.CommandProvider

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
