package com.ChronosFlow.VBCR.feature.daydial.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags

enum class SidebarPage(val label: String, val icon: ImageVector) {
    DAY_TOOLS("Day Tools", Icons.Outlined.Tune),
    TASKS("Tasks", Icons.Default.Checklist),
    FOCUS_TIMER("Focus", Icons.Default.Timer),
    HABITS("Habits", Icons.Default.Favorite),
    GOALS("Goals", Icons.Default.Flag),
    MEDICATION("Meds", Icons.Default.Medication),
    REVIEW("Review", Icons.Default.Assessment),
    JOURNAL("Journal", Icons.Default.EditNote),
    TEMPLATES("Routines", Icons.AutoMirrored.Filled.ViewList),
    CALENDARS("Calendars", Icons.Default.CalendarMonth),
    AI_SETTINGS("AI Settings", Icons.Default.Settings),
    PRIVACY_SYNC("Privacy & Sync", Icons.Outlined.DeviceHub),
    NOTIFICATIONS("Notifications", Icons.Default.Schedule),
    APPEARANCE("Appearance", Icons.Outlined.DarkMode),
    DATA_EXPORT("Data & Export", Icons.Outlined.Cloud),
    DEVELOPER("Developer", Icons.Default.AutoAwesome),
    ABOUT("About", Icons.Outlined.Info);

    companion object {
        val settingsPages = listOf(AI_SETTINGS, PRIVACY_SYNC, NOTIFICATIONS, APPEARANCE)
        val morePages = listOf(JOURNAL, TEMPLATES, CALENDARS, DATA_EXPORT, DEVELOPER, ABOUT)
        val rootPages = listOf(DAY_TOOLS, TASKS, HABITS, GOALS, MEDICATION, REVIEW)

        fun rootPages(featureFlags: ChronosFeatureFlags): List<SidebarPage> =
            rootPages.filter { page ->
                when (page) {
                    HABITS -> featureFlags.habitsEnabled
                    GOALS -> featureFlags.goalsEnabled
                    MEDICATION -> featureFlags.medicationEnabled
                    REVIEW -> featureFlags.reviewEnabled
                    else -> true
                }
            }
    }
}
