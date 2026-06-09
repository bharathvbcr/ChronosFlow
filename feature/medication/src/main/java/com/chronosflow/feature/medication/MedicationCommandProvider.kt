package com.chronosflow.feature.medication

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider

fun medicationCommandProvider(onOpenMedication: () -> Unit): CommandProvider = CommandProvider {
    listOf(
        CommandPaletteItem(
            id = "medication.open",
            title = "Open medication",
            subtitle = "Review medication reminders and adherence",
            keywords = setOf("medication", "meds", "medicine", "reminder", "adherence"),
            group = CommandPaletteGroups.SUPPORTING,
            shortcutLabel = "Meds",
            priority = 70,
            onRun = onOpenMedication
        )
    )
}
