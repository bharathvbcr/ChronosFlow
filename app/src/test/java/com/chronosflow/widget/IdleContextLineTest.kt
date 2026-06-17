package com.chronosflow.widget

import com.chronosflow.core.domain.model.ChronosDayOverview
import com.chronosflow.core.domain.model.DayOverviewBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class IdleContextLineTest {

    private fun block(
        id: String,
        title: String,
        start: Int,
        end: Int,
        isCurrent: Boolean,
        category: String
    ) = DayOverviewBlock(
        id = id,
        title = title,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        isCurrent = isCurrent,
        category = category
    )

    @Test
    fun `current block shows its full time window`() {
        val overview = ChronosDayOverview(
            blocks = listOf(block("b1", "Deep work", 9 * 60, 10 * 60, isCurrent = true, category = "WORK"))
        )
        assertEquals("Now: Deep work · 09:00–10:00", idleContextLine(overview))
    }

    @Test
    fun `idle line splits the next event from the next break`() {
        val overview = ChronosDayOverview(
            blocks = listOf(
                block("b1", "Coffee", 15 * 60, 15 * 60 + 15, isCurrent = false, category = "BREAK"),
                block("b2", "Gym", 16 * 60, 17 * 60, isCurrent = false, category = "WORK")
            )
        )
        assertEquals("Next: Gym · 16:00 · Break · 15:00", idleContextLine(overview))
    }

    @Test
    fun `idle line with only a break shows just the break`() {
        val overview = ChronosDayOverview(
            blocks = listOf(block("b1", "Lunch", 12 * 60, 12 * 60 + 45, isCurrent = false, category = "BREAK"))
        )
        assertEquals("Break · 12:00", idleContextLine(overview))
    }

    @Test
    fun `empty day reads as no session running`() {
        assertEquals("No session running", idleContextLine(ChronosDayOverview()))
    }
}
