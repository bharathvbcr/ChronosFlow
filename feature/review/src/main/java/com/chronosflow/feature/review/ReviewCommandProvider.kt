package com.chronosflow.feature.review

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider

fun reviewCommandProvider(onOpenReview: () -> Unit): CommandProvider = CommandProvider {
    listOf(
        CommandPaletteItem(
            id = "review.open",
            title = "Open review",
            subtitle = "Review planned vs actual time and daily outcomes",
            keywords = setOf("review", "insights", "daily review", "actual", "summary"),
            group = CommandPaletteGroups.SUPPORTING,
            shortcutLabel = "Review",
            priority = 65,
            onRun = onOpenReview
        )
    )
}
