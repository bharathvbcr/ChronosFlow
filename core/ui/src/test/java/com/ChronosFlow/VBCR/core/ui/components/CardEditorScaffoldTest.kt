package com.ChronosFlow.VBCR.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEditorScaffoldTest {

    private val timeSection = CardEditorSection(
        id = "time",
        title = "Time",
        summary = "09:00 – 10:00",
        alwaysPrimary = true,
        hasValue = true,
        content = {},
    )

    private val plannerSection = CardEditorSection(
        id = "planner",
        title = "Planner",
        summary = "Movable",
        content = {},
    )

    @Test
    fun cardEditorSectionIsPrimary_expandsEverythingWhenAdaptiveOff() {
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = false,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
            )
        )
    }

    @Test
    fun cardEditorSectionIsPrimary_respectsAlwaysPrimaryAndHasValue() {
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = timeSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
            )
        )
        assertFalse(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
            )
        )
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection.copy(hasValue = true),
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
            )
        )
    }

    @Test
    fun cardEditorSectionIsPrimary_honorsSessionRevealAndPin() {
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = setOf("planner"),
                pinned = false,
                hidden = false,
            )
        )
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = true,
                hidden = false,
            )
        )
        assertFalse(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = true,
            )
        )
    }

    @Test
    fun cardEditorEstimatedMoreSectionCount_countsUnsetSecondarySections() {
        assertEquals(
            1,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection),
                sessionRevealed = emptySet(),
            )
        )
        assertEquals(
            0,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection.copy(hasValue = true)),
                sessionRevealed = emptySet(),
            )
        )
    }

    @Test
    fun cardEditorMoreOptionsSummary_formatsCount() {
        assertEquals("1 more option", cardEditorMoreOptionsSummary(count = 1, expanded = false))
        assertEquals("2 more options", cardEditorMoreOptionsSummary(count = 2, expanded = false))
        assertEquals("Showing 2 additional sections", cardEditorMoreOptionsSummary(count = 2, expanded = true))
    }

    @Test
    fun cardEditorSectionIsPrimary_honorsAdaptiveHint() {
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection.copy(adaptiveHint = true),
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
            )
        )
    }

    @Test
    fun cardEditorEstimatedMoreSectionCount_excludesAdaptiveHintSections() {
        assertEquals(
            0,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection.copy(adaptiveHint = true)),
                sessionRevealed = emptySet(),
            )
        )
    }

    @Test
    fun cardEditorEstimatedMoreSectionCount_excludesAutoSurfacedAndPinned() {
        assertEquals(
            0,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection),
                sessionRevealed = emptySet(),
                autoSurfacedIds = setOf("planner"),
            )
        )
        assertEquals(
            0,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection),
                sessionRevealed = emptySet(),
                pinnedIds = setOf("planner"),
            )
        )
        assertEquals(
            1,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection),
                sessionRevealed = emptySet(),
            )
        )
    }

    @Test
    fun cardEditorEstimatedMoreSectionCount_excludesSessionRevealed() {
        assertEquals(
            0,
            cardEditorEstimatedMoreSectionCount(
                compactEditors = true,
                sections = listOf(timeSection, plannerSection),
                sessionRevealed = setOf("planner"),
            )
        )
    }

    @Test
    fun cardEditorSectionIsPrimary_honorsAutoSurfaced() {
        assertTrue(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = false,
                autoSurfaced = true,
            )
        )
        assertFalse(
            cardEditorSectionIsPrimary(
                adaptiveEnabled = true,
                section = plannerSection,
                sessionRevealed = emptySet(),
                pinned = false,
                hidden = true,
                autoSurfaced = true,
            )
        )
    }

    @Test
    fun editorSectionIsAutoSurfaced_matchesThreshold() {
        assertFalse(editorSectionIsAutoSurfaced(openCount = 2, hidden = false))
        assertTrue(editorSectionIsAutoSurfaced(openCount = 3, hidden = false))
        assertFalse(editorSectionIsAutoSurfaced(openCount = 5, hidden = true))
    }

    @Test
    fun cardEditorRevealController_accumulatesSectionIds() {
        val controller = CardEditorRevealController()
        controller.reveal("dose")
        assertEquals(setOf("dose"), controller.pendingReveals)
        controller.reveal("reminder")
        assertEquals(setOf("dose", "reminder"), controller.pendingReveals)
        controller.revealAll(listOf("safety", "dose"))
        assertEquals(setOf("dose", "reminder", "safety"), controller.pendingReveals)
    }

    @Test
    fun cardEditorRevealController_consumePendingReveals_allowsRepeatReveal() {
        val controller = CardEditorRevealController()
        controller.reveal("planner")
        controller.consumePendingReveals()
        assertTrue(controller.pendingReveals.isEmpty())
        controller.reveal("planner")
        assertEquals(setOf("planner"), controller.pendingReveals)
    }
}
