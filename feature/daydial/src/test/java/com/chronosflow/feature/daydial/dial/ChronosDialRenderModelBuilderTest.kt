package com.chronosflow.feature.daydial.dial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.feature.daydial.DialUtils.durationToSweep
import com.chronosflow.feature.daydial.DialUtils.durationToSweepInWindow
import com.chronosflow.feature.daydial.DialUtils.minuteToAngle
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import com.chronosflow.feature.daydial.model.TimeRangeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosDialRenderModelBuilderTest {

    @Test
    fun `builder marks semantic ring and emphasis states for arcs`() {
        val calendar = block(
            id = "calendar",
            title = "Standup",
            startMinute = 9 * 60,
            durationMinutes = 30,
            calendarEventId = 11L
        )
        val plan = block(
            id = "plan",
            title = "Deep Work",
            startMinute = 10 * 60,
            durationMinutes = 90
        )
        val action = block(
            id = "action",
            title = "Medication",
            startMinute = 13 * 60,
            durationMinutes = 15,
            medicationPlanId = "med-1"
        )

        val model = ChronosDialRenderModelBuilder.build(
            blocks = listOf(calendar, plan, action),
            freeTimeSegments = listOf(TimeRangeUi(14 * 60, 15 * 60)),
            selectedBlockId = "plan",
            activeBlockId = "calendar",
            upcomingBlockId = "action",
            missedBlockIds = setOf("plan"),
            compactMode = false,
            compactWindowStart = 0
        )

        val arcs = model.blockArcs.associateBy { it.blockId }

        assertEquals(DialRing.OUTER, arcs.getValue("calendar").ring)
        assertEquals(DialRing.MIDDLE, arcs.getValue("plan").ring)
        assertEquals(DialRing.INNER, arcs.getValue("action").ring)

        assertTrue(arcs.getValue("calendar").isActive)
        assertFalse(arcs.getValue("calendar").isUpcoming)
        assertFalse(arcs.getValue("calendar").isMissed)

        assertTrue(arcs.getValue("plan").isSelected)
        assertTrue(arcs.getValue("plan").isMissed)
        assertFalse(arcs.getValue("plan").isInnerAction)

        assertTrue(arcs.getValue("action").isUpcoming)
        assertTrue(arcs.getValue("action").isInnerAction)
    }

    @Test
    fun `builder labels cardinal hours on the 24 hour dial`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = false,
            compactWindowStart = 0
        )

        assertEquals(24, model.hourTicks.size)
        val labeledTicks = model.hourTicks.filter { it.label != null }
        assertEquals(
            listOf("24", "3", "6", "9", "12", "15", "18", "21"),
            labeledTicks.map { it.label }
        )
        val majorLabels = labeledTicks.filter { it.isMajor }.map { it.label }
        assertEquals(listOf("24", "6", "12", "18"), majorLabels)
        assertFalse(model.hourTicks[1].isMajor)
        assertNull(model.hourTicks[1].label)
    }

    @Test
    fun `builder only keeps hour ticks inside the compact window`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 8 * 60
        )

        // 12h window from 08:00 covers 08..20 at 2h steps; 20:00 is the seam (excluded),
        // so the zoomed dial must show six hour labels, not a wrapped full day.
        // Labels use 12-hour clock numbering: the zoomed face reads like a clock.
        assertEquals(
            listOf("8", "10", "12", "2", "4", "6"),
            model.hourTicks.map { it.label }
        )
    }

    @Test
    fun `builder clips compact mode blocks to the visible window`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = listOf(
                block(
                    id = "late",
                    title = "Late block",
                    startMinute = 19 * 60,
                    durationMinutes = 120
                )
            ),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 8 * 60
        )

        val arc = model.blockArcs.single()
        assertEquals(durationToSweepInWindow(60, 720), arc.sweepAngle)
    }

    @Test
    fun `builder does not overstate long overlap as a full compact window`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = listOf(
                block(
                    id = "long",
                    title = "Long block",
                    startMinute = 7 * 60 + 30,
                    durationMinutes = 12 * 60
                )
            ),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 8 * 60
        )

        val arc = model.blockArcs.single()
        assertEquals(durationToSweepInWindow(11 * 60 + 30, 720), arc.sweepAngle)
    }

    @Test
    fun `builder keeps both visible slices when compact window wraps midnight`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = listOf(
                block(
                    id = "wrap",
                    title = "Wide block",
                    startMinute = 7 * 60,
                    durationMinutes = 14 * 60
                )
            ),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 20 * 60
        )

        val arcs = model.blockArcs.filter { it.blockId == "wrap" }
        assertEquals(2, arcs.size)
        assertEquals(durationToSweepInWindow(60, 720), arcs[0].sweepAngle)
        assertEquals(durationToSweepInWindow(60, 720), arcs[1].sweepAngle)
    }

    @Test
    fun `night band is one arc anchored to clock time on the 24 hour dial`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = false,
            compactWindowStart = 0,
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )

        val night = model.nightArcs.single()
        // 21:00 start, wrapping 10h to 07:00 — a single arc across the midnight seam.
        assertEquals(minuteToAngle(21 * 60), night.startAngle, 0.001f)
        assertEquals(durationToSweep(10 * 60), night.sweepAngle, 0.001f)
    }

    @Test
    fun `night band is omitted when the sleep window is empty`() {
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = false,
            compactWindowStart = 0,
            nightStartMinute = 6 * 60,
            nightEndMinute = 6 * 60
        )

        assertTrue(model.nightArcs.isEmpty())
    }

    @Test
    fun `night band clips out of a daytime compact window`() {
        // 12h window 08:00..20:00 contains none of a 21:00..07:00 night, so no band shows.
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 8 * 60,
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )

        assertTrue(model.nightArcs.isEmpty())
    }

    @Test
    fun `night band clips to the visible part of an evening compact window`() {
        // 12h window 18:00..06:00 sees 21:00 through its 06:00 edge: a 9h slice.
        val model = ChronosDialRenderModelBuilder.build(
            blocks = emptyList(),
            freeTimeSegments = emptyList(),
            selectedBlockId = null,
            compactMode = true,
            compactWindowStart = 18 * 60,
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )

        val night = model.nightArcs.single()
        assertEquals(durationToSweepInWindow(9 * 60, 720), night.sweepAngle, 0.001f)
    }

    @Test
    fun `free time is clipped out of the night window`() {
        // 08:00-23:00 open, night 21:00-07:00 -> only 08:00-21:00 stays schedulable.
        val clipped = ChronosDialRenderModelBuilder.subtractNightFromFreeSegments(
            segments = listOf(TimeRangeUi(8 * 60, 23 * 60)),
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )
        assertEquals(listOf(TimeRangeUi(8 * 60, 21 * 60)), clipped)
    }

    @Test
    fun `free time fully inside the night window drops out`() {
        val clipped = ChronosDialRenderModelBuilder.subtractNightFromFreeSegments(
            segments = listOf(TimeRangeUi(22 * 60, 23 * 60)),
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )
        assertTrue(clipped.isEmpty())
    }

    @Test
    fun `full open day keeps only its daytime remainder`() {
        // TimeRangeUi(0,0) reads as the whole day; night 21:00-07:00 leaves 07:00-21:00.
        val clipped = ChronosDialRenderModelBuilder.subtractNightFromFreeSegments(
            segments = listOf(TimeRangeUi(0, 0)),
            nightStartMinute = 21 * 60,
            nightEndMinute = 7 * 60
        )
        assertEquals(listOf(TimeRangeUi(7 * 60, 21 * 60)), clipped)
    }

    private fun block(
        id: String,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        calendarEventId: Long? = null,
        medicationPlanId: String? = null
    ): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = id,
            title = title,
            startMinuteOfDay = startMinute,
            durationMinutes = durationMinutes,
            color = Color(0xFF6750A4),
            calendarEventId = calendarEventId,
            medicationPlanId = medicationPlanId
        )
    }
}
