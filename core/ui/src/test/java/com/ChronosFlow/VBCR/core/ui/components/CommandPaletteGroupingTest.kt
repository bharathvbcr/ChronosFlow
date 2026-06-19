package com.ChronosFlow.VBCR.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteGroupingTest {

    @Test
    fun mergeCommandPaletteResults_prefersHigherPriorityDuplicateIds() {
        val low = CommandPaletteItem(
            id = "tasks.open",
            title = "Open tasks",
            subtitle = "Low",
            priority = 10,
            onRun = {}
        )
        val high = low.copy(subtitle = "High", priority = 90)

        val merged = mergeCommandPaletteResults(listOf(low), listOf(high))

        assertEquals(1, merged.size)
        assertEquals("High", merged.single().subtitle)
    }

    @Test
    fun groupCommandsForDisplay_ordersKnownGroupsAndSortsByPriority() {
        val commands = listOf(
            sampleCommand(id = "settings", group = CommandPaletteGroups.SETTINGS, priority = 1, title = "Z"),
            sampleCommand(id = "day", group = CommandPaletteGroups.DAY, priority = 50, title = "B"),
            sampleCommand(id = "plan", group = CommandPaletteGroups.PLAN, priority = 100, title = "A"),
            sampleCommand(id = "focus", group = CommandPaletteGroups.FOCUS, priority = 75, title = "C")
        )

        val labels = groupCommandsForDisplay(commands)
            .mapNotNull { entry ->
                when (entry) {
                    is CommandPaletteDisplayEntry.GroupHeader -> entry.label
                    else -> null
                }
            }

        assertEquals(
            listOf(
                CommandPaletteGroups.DAY,
                CommandPaletteGroups.PLAN,
                CommandPaletteGroups.FOCUS,
                CommandPaletteGroups.SETTINGS
            ),
            labels
        )

        val entries = groupCommandsForDisplay(commands)
        val planSection = entries.dropWhile { it !is CommandPaletteDisplayEntry.GroupHeader || it.label != CommandPaletteGroups.PLAN }
            .drop(1)
            .takeWhile { it is CommandPaletteDisplayEntry.Command }
            .filterIsInstance<CommandPaletteDisplayEntry.Command>()
            .map { it.command.title }

        assertEquals(listOf("A"), planSection)
    }

    @Test
    fun groupCommandsForDisplay_ordersQuickCreateBeforeDay() {
        val commands = listOf(
            sampleCommand(id = "day", group = CommandPaletteGroups.DAY, priority = 50, title = "Today"),
            sampleCommand(id = "quick", group = CommandPaletteGroups.QUICK_CREATE, priority = 90, title = "New block")
        )

        val labels = groupCommandsForDisplay(commands)
            .mapNotNull { entry ->
                when (entry) {
                    is CommandPaletteDisplayEntry.GroupHeader -> entry.label
                    else -> null
                }
            }

        assertEquals(
            listOf(CommandPaletteGroups.QUICK_CREATE, CommandPaletteGroups.DAY),
            labels
        )
    }

    @Test
    fun groupCommandsForDisplay_omitsEmptyGroups() {
        val commands = listOf(
            sampleCommand(id = "day", group = CommandPaletteGroups.DAY, priority = 1, title = "Today")
        )

        val labels = groupCommandsForDisplay(commands)
            .mapNotNull { entry ->
                when (entry) {
                    is CommandPaletteDisplayEntry.GroupHeader -> entry.label
                    else -> null
                }
            }

        assertEquals(listOf(CommandPaletteGroups.DAY), labels)
    }

    @Test
    fun filterCommands_matchesFieldsCaseInsensitivelyAndRanksPrefixHitsFirst() {
        val commands = listOf(
            sampleCommand(
                id = "contains",
                group = CommandPaletteGroups.OTHER,
                priority = 50,
                title = "Open planning board"
            ),
            sampleCommand(
                id = "prefix",
                group = CommandPaletteGroups.OTHER,
                priority = 10,
                title = "Plan day"
            )
        )

        val matches = filterCommands(commands, "PLAN")

        assertEquals(listOf("prefix", "contains"), matches.map { it.id })
    }

    @Test
    fun filterCommands_matchesKeywordsAndReturnsAllCommandsForBlankQuery() {
        val commands = listOf(
            sampleCommand(
                id = "focus",
                group = CommandPaletteGroups.FOCUS,
                priority = 1,
                title = "Start session",
                keywords = setOf("pomodoro", "deep work")
            ),
            sampleCommand(
                id = "day",
                group = CommandPaletteGroups.DAY,
                priority = 1,
                title = "Today"
            )
        )

        assertEquals(listOf("focus"), filterCommands(commands, "POMO").map { it.id })
        assertEquals(commands, filterCommands(commands, "   "))
    }

    @Test
    fun commandPaletteCloseActionLabel_namesTheDialog() {
        assertEquals("Close command palette", commandPaletteCloseActionLabel())
    }

    @Test
    fun commandPaletteSpeechQuery_trimsAndNormalizesTranscript() {
        assertEquals(
            "vitamin d 1000 iu morning",
            commandPaletteSpeechQuery("  vitamin   d  1000 iu   morning  ")
        )
    }

    @Test
    fun chronosSpeechTranscriptFromResults_usesFirstNonBlankNormalizedResult() {
        assertEquals(
            "call mom tomorrow",
            chronosSpeechTranscriptFromResults(listOf("   ", " call   mom   tomorrow ", "call mom"))
        )
        assertEquals("", chronosSpeechTranscriptFromResults(null))
    }

    private fun sampleCommand(
        id: String,
        group: String,
        priority: Int,
        title: String,
        keywords: Set<String> = emptySet()
    ): CommandPaletteItem = CommandPaletteItem(
        id = id,
        title = title,
        subtitle = "Subtitle",
        keywords = keywords,
        group = group,
        priority = priority,
        onRun = {}
    )
}
