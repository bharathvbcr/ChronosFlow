package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

enum class DayDialTab(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.AutoMirrored.Filled.EventNote),
    TODAY("Today", Icons.Default.Today),
    FOCUS("Focus", Icons.Default.Timer),
    // Surfaced to users as "Review" (the unified review page); the enum name stays
    // INSIGHTS to avoid churn across the planner/state code that references it.
    INSIGHTS("Review", Icons.Default.Insights);

    companion object {
        val primary = listOf(PLAN, TODAY, FOCUS)
    }
}
