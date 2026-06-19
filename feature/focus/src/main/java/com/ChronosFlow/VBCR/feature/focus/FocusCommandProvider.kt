package com.ChronosFlow.VBCR.feature.focus

import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteGroups
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteItem
import com.ChronosFlow.VBCR.core.ui.components.CommandProvider

fun focusCommandProvider(onOpenFocus: () -> Unit): CommandProvider = CommandProvider {
    buildList {
        add(
            CommandPaletteItem(
                id = "focus.open",
                title = "Open focus",
                subtitle = "Open the focus planner and start your next focused session.",
                keywords = setOf("focus", "planner", "session", "deep work"),
                group = CommandPaletteGroups.FOCUS,
                shortcutLabel = "Focus",
                priority = 100,
                onRun = onOpenFocus
            )
        )
    }
}
