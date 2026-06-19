package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ChronosFlow.VBCR.core.ui.components.ChronosPageHeader
import com.ChronosFlow.VBCR.feature.daydial.model.DayDialTab
import com.ChronosFlow.VBCR.feature.daydial.model.SidebarPage

@Composable
internal fun DayDialPageHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    ChronosPageHeader(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        trailing = trailing
    )
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
        SidebarPage.GOALS -> "Long-term objectives and progress"
        SidebarPage.MEDICATION -> "Medication reminders and dose tracking"
        SidebarPage.REVIEW -> "Planned, actual, and missed time"
        SidebarPage.JOURNAL -> "Reflections, mood, and journaling insights"
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
