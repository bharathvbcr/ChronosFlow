package com.ChronosFlow.VBCR

import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteGroups
import com.ChronosFlow.VBCR.feature.daydial.dayDialCommandProvider
import com.ChronosFlow.VBCR.feature.focus.focusCommandProvider
import com.ChronosFlow.VBCR.feature.habits.habitCommandProvider
import com.ChronosFlow.VBCR.feature.medication.medicationCommandProvider
import com.ChronosFlow.VBCR.feature.tasks.taskCommandProvider
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
            medicationCommandProvider {}
        ).flatMap { provider -> provider.commands() }

        assertEquals(
            listOf(
                "daydial.open",
                "daydial.plan",
                "daydial.focus-planner",
                "daydial.journal",
                "daydial.sleep",
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
                "medication.open"
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
            medicationCommandProvider {}
        ).flatMap { provider -> provider.commands() }

        assertTrue(commands.all { !it.group.isNullOrBlank() })
        assertTrue(commands.all { it.priority > 0 })
        assertEquals(CommandPaletteGroups.DAY, commands.single { it.id == "daydial.open" }.group)
        assertEquals(CommandPaletteGroups.PLAN, commands.single { it.id == "daydial.plan" }.group)
        assertEquals(CommandPaletteGroups.FOCUS, commands.single { it.id == "focus.open" }.group)
        assertEquals(CommandPaletteGroups.SUPPORTING, commands.single { it.id == "daydial.review" }.group)
        assertEquals("Today", commands.single { it.id == "daydial.open" }.shortcutLabel)
        assertEquals(
            listOf(85, 80, 75, 70),
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
                onOpenJournal = { invoked += "journal" },
                onOpenSleepLog = { invoked += "sleep" },
                onOpenReview = { invoked += "review" },
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
            medicationCommandProvider { invoked += "medication" }
        )

        providers.flatMap { provider -> provider.commands() }.forEach { command -> command.onRun() }

        assertEquals(
            listOf(
                "day",
                "plan",
                "focus-planner",
                "journal",
                "sleep",
                "review",
                "tools",
                "templates",
                "ai-settings",
                "privacy-sync",
                "notifications",
                "appearance",
                "focus",
                "tasks",
                "habits",
                "medication"
            ),
            invoked
        )
    }
}
