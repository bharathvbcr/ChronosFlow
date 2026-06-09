package com.chronosflow

import com.chronosflow.core.ui.components.CommandPaletteGroups
import com.chronosflow.feature.daydial.dayDialCommandProvider
import com.chronosflow.feature.focus.focusCommandProvider
import com.chronosflow.feature.habits.habitCommandProvider
import com.chronosflow.feature.medication.medicationCommandProvider
import com.chronosflow.feature.review.reviewCommandProvider
import com.chronosflow.feature.tasks.taskCommandProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCommandProviderTest {

    @Test
    fun featureOwnedProvidersExposeExpectedCommandIds() {
        val commands = listOf(
            dayDialCommandProvider(onOpenDayDial = {}),
            focusCommandProvider(onOpenFocus = {}),
            taskCommandProvider {},
            habitCommandProvider {},
            medicationCommandProvider {},
            reviewCommandProvider {}
        ).flatMap { provider -> provider.commands() }

        assertEquals(
            listOf(
                "daydial.open",
                "daydial.plan",
                "daydial.focus-planner",
                "daydial.insights",
                "daydial.review",
                "daydial.tools",
                "daydial.templates",
                "daydial.ai-settings",
                "daydial.privacy-sync",
                "daydial.notifications",
                "daydial.appearance",
                "focus.open",
                "tasks.open",
                "habits.open",
                "medication.open",
                "review.open"
            ),
            commands.map { it.id }
        )
        assertEquals(commands.size, commands.map { it.id }.toSet().size)
        assertTrue(commands.all { it.title.startsWith("Open ") })
        assertTrue(commands.all { it.keywords.isNotEmpty() })
    }

    @Test
    fun featureOwnedProviderCommandsExposePaletteMetadata() {
        val commands = listOf(
            dayDialCommandProvider(onOpenDayDial = {}),
            focusCommandProvider(onOpenFocus = {}),
            taskCommandProvider {},
            habitCommandProvider {},
            medicationCommandProvider {},
            reviewCommandProvider {}
        ).flatMap { provider -> provider.commands() }

        assertTrue(commands.all { !it.group.isNullOrBlank() })
        assertTrue(commands.all { it.priority > 0 })
        assertEquals(CommandPaletteGroups.DAY, commands.single { it.id == "daydial.open" }.group)
        assertEquals(CommandPaletteGroups.PLAN, commands.single { it.id == "daydial.plan" }.group)
        assertEquals(CommandPaletteGroups.FOCUS, commands.single { it.id == "focus.open" }.group)
        assertEquals(CommandPaletteGroups.SUPPORTING, commands.single { it.id == "review.open" }.group)
        assertEquals("Today", commands.single { it.id == "daydial.open" }.shortcutLabel)
        assertEquals(
            listOf(80, 80, 75, 70, 65),
            commands
                .filter { it.group == CommandPaletteGroups.SUPPORTING }
                .map { it.priority }
                .sortedDescending()
        )
    }

    @Test
    fun featureOwnedProviderCommandsRunTheirFeatureCallbacks() {
        val invoked = mutableListOf<String>()
        val providers = listOf(
            dayDialCommandProvider(
                onOpenDayDial = { invoked += "day" },
                onOpenPlan = { invoked += "plan" },
                onOpenFocusPlanner = { invoked += "focus-planner" },
                onOpenInsights = { invoked += "insights" },
                onOpenReview = { invoked += "day-review" },
                onOpenPlanningTools = { invoked += "tools" },
                onOpenTemplates = { invoked += "templates" },
                onOpenAiSettings = { invoked += "ai-settings" },
                onOpenPrivacySync = { invoked += "privacy-sync" },
                onOpenNotificationSettings = { invoked += "notifications" },
                onOpenAppearanceSettings = { invoked += "appearance" }
            ),
            focusCommandProvider(onOpenFocus = { invoked += "focus" }),
            taskCommandProvider { invoked += "tasks" },
            habitCommandProvider { invoked += "habits" },
            medicationCommandProvider { invoked += "medication" },
            reviewCommandProvider { invoked += "review" }
        )

        providers.flatMap { provider -> provider.commands() }.forEach { command -> command.onRun() }

        assertEquals(
            listOf(
                "day",
                "plan",
                "focus-planner",
                "insights",
                "day-review",
                "tools",
                "templates",
                "ai-settings",
                "privacy-sync",
                "notifications",
                "appearance",
                "focus",
                "tasks",
                "habits",
                "medication",
                "review"
            ),
            invoked
        )
    }
}
