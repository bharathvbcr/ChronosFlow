package com.chronosflow.feature.daydial.dial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.feature.daydial.DialUtils.durationToSweepInWindow
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
        assertEquals(listOf("24", "6", "12", "18"), labeledTicks.map { it.label })
        labeledTicks.forEach { assertTrue(it.isMajor) }
        assertFalse(model.hourTicks[1].isMajor)
        assertNull(model.hourTicks[1].label)
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
