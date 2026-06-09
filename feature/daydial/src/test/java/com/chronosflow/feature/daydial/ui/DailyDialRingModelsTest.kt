package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyDialRingModelsTest {

    @Test
    fun `selected block state takes priority over active block`() {
        val activeBlock = block(
            id = "active",
            title = "Deep Work",
            startMinute = 9 * 60,
            durationMinutes = 60,
            category = "WORK"
        )
        val selectedBlock = block(
            id = "selected",
            title = "Standup",
            startMinute = 10 * 60,
            durationMinutes = 30,
            category = "MEETING"
        )

        val state = buildDailyDialCenterState(
            currentMinute = 9 * 60 + 35,
            selectedBlock = selectedBlock,
            activeBlock = activeBlock,
            nextBlock = selectedBlock
        )

        assertEquals("Standup", state.title)
        assertEquals("10:00 AM - 10:30 AM", state.timeWindow)
        assertEquals("Starts in 25 min", state.status)
        assertEquals("Selected meeting block", state.supporting)
        assertEquals("Drag to move. Handles resize.", state.actionHint)
        assertEquals("Meeting", state.categoryLabel)
    }

    @Test
    fun `selected past block is not presented as an upcoming start`() {
        val selectedBlock = block(
            id = "selected",
            title = "Standup",
            startMinute = 10 * 60,
            durationMinutes = 30,
            category = "MEETING"
        )

        val state = buildDailyDialCenterState(
            currentMinute = 12 * 60,
            selectedBlock = selectedBlock,
            activeBlock = null,
            nextBlock = null
        )

        assertEquals("Ended 90 min ago", state.status)
    }

    @Test
    fun `active block state summarizes current and next work`() {
        val activeBlock = block(
            id = "active",
            title = "Deep Work",
            startMinute = 9 * 60,
            durationMinutes = 60,
            category = "WORK"
        )
        val nextBlock = block(
            id = "next",
            title = "Review",
            startMinute = 10 * 60 + 30,
            durationMinutes = 30,
            category = "ADMIN"
        )

        val state = buildDailyDialCenterState(
            currentMinute = 9 * 60 + 25,
            selectedBlock = null,
            activeBlock = activeBlock,
            nextBlock = nextBlock
        )

        assertEquals("Deep Work", state.title)
        assertEquals("9:00 AM - 10:00 AM", state.timeWindow)
        assertEquals("Ends in 35 min", state.status)
        assertEquals("Next: Review at 10:30 AM", state.supporting)
        assertEquals("Tap block for details", state.actionHint)
        assertEquals("Work", state.categoryLabel)
    }

    @Test
    fun `open time state nudges ring based quick create`() {
        val nextBlock = block(
            id = "next",
            title = "Workout",
            startMinute = 18 * 60,
            durationMinutes = 45,
            category = "HEALTH"
        )

        val state = buildDailyDialCenterState(
            currentMinute = 17 * 60 + 20,
            selectedBlock = null,
            activeBlock = null,
            nextBlock = nextBlock
        )

        assertEquals("Open time", state.title)
        assertEquals("5:20 PM", state.timeWindow)
        assertEquals("Free for 40 min", state.status)
        assertEquals("Next: Workout at 6:00 PM", state.supporting)
        assertEquals("Tap a ring to add a block", state.actionHint)
        assertEquals("Open window", state.categoryLabel)
    }

    @Test
    fun `legend items expose ring meaning and quick create labels`() {
        val items = dailyDialLegendItems()

        assertEquals(
            listOf("Calendar", "Plan", "Actions"),
            items.map { it.title }
        )
        assertEquals(
            listOf("Calendar hold", "Focus Block", "Routine checkpoint"),
            items.map { it.quickCreateLabel }
        )
        assertEquals(
            listOf(
                "Imported events and fixed holds",
                "Flexible blocks you can shape",
                "Tasks habits and medication"
            ),
            items.map { it.description }
        )
    }

    @Test
    fun `expanded legend state keeps explanatory copy and minimize action`() {
        val state = buildDailyDialRingLegendState(isExpanded = true)

        assertEquals("Ring guide", state.title)
        assertEquals(
            "Outer is fixed time, middle is your plan, inner is action-oriented routines. Tap any lane to add in context.",
            state.description
        )
        assertEquals("Minimize", state.actionLabel)
    }

    @Test
    fun `collapsed legend state removes explanatory copy and exposes restore action`() {
        val state = buildDailyDialRingLegendState(isExpanded = false)

        assertEquals("Ring guide", state.title)
        assertNull(state.description)
        assertEquals("Show", state.actionLabel)
    }

    @Test
    fun `legend toggle action label names expanded state change`() {
        assertEquals(
            "Show ring guide details",
            dailyDialRingLegendToggleActionLabel(isExpanded = false)
        )
        assertEquals(
            "Minimize ring guide details",
            dailyDialRingLegendToggleActionLabel(isExpanded = true)
        )
    }

    @Test
    fun `remaining minutes wraps for overnight blocks`() {
        val overnight = block(
            id = "overnight",
            title = "Late focus",
            startMinute = 23 * 60 + 30,
            durationMinutes = 60,
            category = "WORK"
        )

        assertEquals(20, remainingMinutesInBlock(overnight, 10))
    }

    private fun block(
        id: String,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = title,
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            color = Color(0xFF6750A4),
            category = category
        )
    }
}
