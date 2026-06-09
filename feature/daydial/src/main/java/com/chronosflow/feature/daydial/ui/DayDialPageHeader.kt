package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.theme.ChronosSpacing
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SidebarPage

@Composable
internal fun DayDialPageHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronosSpacing.Standard)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(10.dp)
            )
        }
        ChronosSectionTitle(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

internal fun dayDialPrimaryPageSubtitle(tab: DayDialTab): String =
    when (tab) {
        DayDialTab.PLAN -> "Build blocks, templates, and AI suggestions"
        DayDialTab.TODAY -> "Live schedule, quick actions, and recovery"
        DayDialTab.FOCUS -> "Run sessions, pacing, and protection"
        DayDialTab.INSIGHTS -> "Execution score, review, and recommendations"
    }

internal fun dayDialSidebarPageSubtitle(page: SidebarPage): String =
    when (page) {
        SidebarPage.DAY_TOOLS -> "Actions, metrics, and day operations"
        SidebarPage.TASKS -> "Capture, prioritize, and complete tasks"
        SidebarPage.FOCUS_TIMER -> "Open focus sessions and timer controls"
        SidebarPage.HABITS -> "Track routines for the selected day"
        SidebarPage.MEDICATION -> "Medication reminders and dose tracking"
        SidebarPage.REVIEW -> "Planned, actual, and missed time"
        SidebarPage.TEMPLATES -> "Reusable day blueprints"
        SidebarPage.CALENDARS -> "Device events and linked exports"
        SidebarPage.AI_SETTINGS -> "Planner privacy, model status, and planning style"
        SidebarPage.PRIVACY_SYNC -> "On-device AI, checkpoints, and app lock"
        SidebarPage.NOTIFICATIONS -> "Reminders, quiet hours, and alarm permissions"
        SidebarPage.APPEARANCE -> "Theme, motion, and DayDial density"
        SidebarPage.DATA_EXPORT -> "Local backup, import, and JSON export"
        SidebarPage.DEVELOPER -> "Diagnostics and feature flags"
        SidebarPage.ABOUT -> "Product information and build details"
    }
