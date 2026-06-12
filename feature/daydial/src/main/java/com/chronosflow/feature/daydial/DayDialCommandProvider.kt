package com.chronosflow.feature.daydial

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.components.CommandProvider
import com.chronosflow.core.ui.settings.ChronosFeatureFlags

fun dayDialCommandProvider(
    onOpenDayDial: () -> Unit,
    onOpenPlan: () -> Unit = onOpenDayDial,
    onOpenFocusPlanner: () -> Unit = onOpenDayDial,
    onOpenInsights: () -> Unit = onOpenDayDial,
    onOpenJournal: () -> Unit = onOpenInsights,
    onOpenSleepLog: () -> Unit = onOpenInsights,
    onOpenPlanningTools: () -> Unit = onOpenDayDial,
    onOpenTemplates: () -> Unit = onOpenDayDial,
    onOpenReview: () -> Unit = onOpenDayDial,
    onOpenAiSettings: () -> Unit = onOpenDayDial,
    onOpenPrivacySync: () -> Unit = onOpenDayDial,
    onOpenNotificationSettings: () -> Unit = onOpenDayDial,
    onOpenAppearanceSettings: () -> Unit = onOpenDayDial,
    featureFlags: ChronosFeatureFlags = ChronosFeatureFlags.AllEnabled
): CommandProvider = CommandProvider {
    buildList {
        add(CommandPaletteItem(
            id = "daydial.open",
            title = "Open daily dial",
            subtitle = "Review and adjust today's Chronos Dial",
            keywords = setOf("day", "daily", "dial", "planner", "today"),
            group = CommandPaletteGroups.DAY,
            shortcutLabel = "Today",
            priority = 100,
            onRun = onOpenDayDial
        ))
        add(CommandPaletteItem(
            id = "daydial.plan",
            title = "Open plan",
            subtitle = "Build and rebalance the focus plan",
            keywords = setOf("plan", "planning", "schedule", "rebalance", "focus"),
            group = CommandPaletteGroups.PLAN,
            shortcutLabel = "Plan",
            priority = 90,
            onRun = onOpenPlan
        ))
        add(CommandPaletteItem(
            id = "daydial.focus-planner",
            title = "Open focus planner",
            subtitle = "Review the next focus block before starting",
            keywords = setOf("focus", "planner", "session", "deep work", "block"),
            group = CommandPaletteGroups.FOCUS,
            shortcutLabel = "Focus",
            priority = 90,
            onRun = onOpenFocusPlanner
        ))
        if (featureFlags.reviewEnabled) {
            add(CommandPaletteItem(
                id = "daydial.insights",
                title = "Open insights",
                subtitle = "View planned vs actual trends and recommendations",
                keywords = setOf(
                    "insights", "analytics", "stats", "performance", "trends",
                    "mood", "energy", "habit", "medication", "adherence", "patterns", "companion"
                ),
                group = CommandPaletteGroups.DAY,
                shortcutLabel = "Insights",
                priority = 85,
                onRun = onOpenInsights
            ))
            add(CommandPaletteItem(
                id = "daydial.journal",
                title = "Open journal",
                subtitle = "Capture tonight's reflection and review recent entries",
                keywords = setOf("journal", "reflection", "diary", "notes", "evening", "entry"),
                group = CommandPaletteGroups.DAY,
                shortcutLabel = "Journal",
                priority = 78,
                onRun = onOpenJournal
            ))
            add(CommandPaletteItem(
                id = "daydial.sleep",
                title = "Open sleep log",
                subtitle = "Record last night's sleep quality and timing",
                keywords = setOf("sleep", "rest", "wind down", "bedtime", "quality", "log"),
                group = CommandPaletteGroups.DAY,
                shortcutLabel = "Sleep",
                priority = 77,
                onRun = onOpenSleepLog
            ))
            add(CommandPaletteItem(
                id = "daydial.review",
                title = "Open daily review",
                subtitle = "Full review with insights and weekly roll-up",
                keywords = setOf("review", "summary", "retrospective", "end of day"),
                group = CommandPaletteGroups.SUPPORTING,
                shortcutLabel = "Review",
                priority = 80,
                onRun = onOpenReview
            ))
        }
        add(CommandPaletteItem(
            id = "daydial.tools",
            title = "Open planning tools",
            subtitle = "Generate, fill gaps, rebalance, and recover the day",
            keywords = setOf("tools", "generate", "gaps", "recover", "missed", "rebalance"),
            group = CommandPaletteGroups.PLAN,
            shortcutLabel = "Tools",
            priority = 80,
            onRun = onOpenPlanningTools
        ))
        add(CommandPaletteItem(
            id = "daydial.templates",
            title = "Open routines",
            subtitle = "Apply or save repeatable focus-day layouts",
            keywords = setOf("routines", "templates", "routine", "copy", "save", "apply"),
            group = CommandPaletteGroups.PLAN,
            shortcutLabel = "Routines",
            priority = 70,
            onRun = onOpenTemplates
        ))
        add(CommandPaletteItem(
            id = "daydial.ai-settings",
            title = "Open AI settings",
            subtitle = "Tune privacy and focus-planning style",
            keywords = setOf("ai", "settings", "privacy", "strict", "balanced", "flexible"),
            group = CommandPaletteGroups.SETTINGS,
            shortcutLabel = "AI",
            priority = 60,
            onRun = onOpenAiSettings
        ))
        add(CommandPaletteItem(
            id = "daydial.privacy-sync",
            title = "Open privacy and sync",
            subtitle = "Manage local checkpoints and privacy mode",
            keywords = setOf("privacy", "sync", "checkpoint", "export", "local"),
            group = CommandPaletteGroups.SETTINGS,
            shortcutLabel = "Privacy",
            priority = 55,
            onRun = onOpenPrivacySync
        ))
        add(CommandPaletteItem(
            id = "daydial.notifications",
            title = "Open notification settings",
            subtitle = "Tune focus, break, missed-block, and review reminders",
            keywords = setOf("notification", "notifications", "reminder", "break", "missed", "review"),
            group = CommandPaletteGroups.SETTINGS,
            shortcutLabel = "Alerts",
            priority = 50,
            onRun = onOpenNotificationSettings
        ))
        add(CommandPaletteItem(
            id = "daydial.appearance",
            title = "Open appearance settings",
            subtitle = "Adjust dynamic color, glass, motion, and contrast",
            keywords = setOf("appearance", "theme", "color", "glass", "motion", "contrast"),
            group = CommandPaletteGroups.SETTINGS,
            shortcutLabel = "Theme",
            priority = 50,
            onRun = onOpenAppearanceSettings
        ))
    }
}
