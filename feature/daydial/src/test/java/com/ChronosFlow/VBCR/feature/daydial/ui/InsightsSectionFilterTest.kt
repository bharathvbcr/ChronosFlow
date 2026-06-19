package com.ChronosFlow.VBCR.feature.daydial.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Review page's quick-pill section filter: an empty selection shows every section, and a
 * non-empty selection narrows the page to exactly the chosen sections.
 */
class InsightsSectionFilterTest {

    @Test
    fun `empty selection shows every section`() {
        InsightsSection.values().forEach { section ->
            assertTrue(
                "Expected $section visible with no pill selected",
                insightsSectionVisible(section, emptySet())
            )
        }
    }

    @Test
    fun `a single selected pill hides the other sections`() {
        val selected = setOf(InsightsSection.TRENDS.name)

        assertTrue(insightsSectionVisible(InsightsSection.TRENDS, selected))
        assertFalse(insightsSectionVisible(InsightsSection.EXECUTION, selected))
        assertFalse(insightsSectionVisible(InsightsSection.CATEGORIES, selected))
        assertFalse(insightsSectionVisible(InsightsSection.INSIGHTS, selected))
        assertFalse(insightsSectionVisible(InsightsSection.SCREEN_TIME, selected))
    }

    @Test
    fun `multiple selected pills show exactly those sections`() {
        val selected = setOf(InsightsSection.EXECUTION.name, InsightsSection.SCREEN_TIME.name)

        assertTrue(insightsSectionVisible(InsightsSection.EXECUTION, selected))
        assertTrue(insightsSectionVisible(InsightsSection.SCREEN_TIME, selected))
        assertFalse(insightsSectionVisible(InsightsSection.TRENDS, selected))
    }

    @Test
    fun `section labels stay user-facing and stable`() {
        assertEquals(
            listOf("Execution", "Categories", "Insights", "Screen time", "Trends"),
            InsightsSection.values().map { it.label }
        )
    }
}
