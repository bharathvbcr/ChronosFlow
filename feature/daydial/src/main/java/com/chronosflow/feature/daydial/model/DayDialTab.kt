package com.chronosflow.feature.daydial.model

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
    INSIGHTS("Insights", Icons.Default.Insights);

    companion object {
        val primary = listOf(PLAN, TODAY, FOCUS)
    }
}
