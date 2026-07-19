package com.ChronosFlow.VBCR.feature.daydial.model

import com.ChronosFlow.VBCR.feature.daydial.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyActionResolverTest {

    @Test
    fun emptyDayShowsPlanAndAddBlock() {
        val action = resolveDailyAction(
            timeBlocks = emptyList(),
            activeBlock = null,
            nextBlock = null,
            missedBlocks = emptyList()
        )
        assertEquals(DailyActionKind.EMPTY_DAY, action?.kind)
        assertEquals("Plan day", action?.primaryLabel)
        assertEquals("Add block", action?.secondaryLabel)
    }

    @Test
    fun activeBlockShowsFocusActions() {
        val active = block("active", 9 * 60)
        val action = resolveDailyAction(
            timeBlocks = listOf(active),
            activeBlock = active,
            nextBlock = null,
            missedBlocks = emptyList()
        )
        assertEquals(DailyActionKind.ACTIVE_BLOCK, action?.kind)
        assertEquals("Start focus", action?.primaryLabel)
        assertEquals("Complete", action?.secondaryLabel)
    }

    @Test
    fun upcomingBlockWithoutActiveShowsStartNext() {
        val next = block("next", 10 * 60)
        val action = resolveDailyAction(
            timeBlocks = listOf(next),
            activeBlock = null,
            nextBlock = next,
            missedBlocks = emptyList()
        )
        assertEquals(DailyActionKind.UPCOMING_BLOCK, action?.kind)
        assertEquals("Start next", action?.primaryLabel)
    }

    @Test
    fun missedBlocksSurfaceReviewMissed() {
        val missed = block("missed", 8 * 60)
        val action = resolveDailyAction(
            timeBlocks = listOf(missed),
            activeBlock = null,
            nextBlock = null,
            missedBlocks = listOf(missed)
        )
        assertEquals(DailyActionKind.MISSED_BLOCKS, action?.kind)
        assertEquals("Review missed", action?.primaryLabel)
        assertEquals("Reflow day", action?.secondaryLabel)
    }

    @Test
    fun missedBlocksOutrankUpcomingBlock() {
        val missed = block("missed", 8 * 60)
        val next = block("next", 11 * 60)
        val action = resolveDailyAction(
            timeBlocks = listOf(missed, next),
            activeBlock = null,
            nextBlock = next,
            missedBlocks = listOf(missed)
        )
        assertEquals(DailyActionKind.MISSED_BLOCKS, action?.kind)
        assertEquals("Review missed", action?.primaryLabel)
    }

    @Test
    fun openTimeShowsAddBlockAndFillGaps() {
        val past = block("past", 7 * 60)
        val action = resolveDailyAction(
            timeBlocks = listOf(past),
            activeBlock = null,
            nextBlock = null,
            missedBlocks = emptyList()
        )
        assertEquals(DailyActionKind.OPEN_TIME, action?.kind)
        assertEquals("Add block", action?.primaryLabel)
        assertEquals("Fill gap", action?.secondaryLabel)
    }

    private fun block(id: String, start: Int) = TimeBlockUiModel(
        id = id,
        title = id,
        startMinuteOfDay = start,
        durationMinutes = 30,
        color = androidx.compose.ui.graphics.Color.Gray
    )
}
