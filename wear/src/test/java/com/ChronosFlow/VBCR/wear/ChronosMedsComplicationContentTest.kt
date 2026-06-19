package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearMed
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChronosMedsComplicationContentTest {

    private fun med(taken: Boolean) =
        WearMed(id = "m$taken", name = "Med", doseLabel = "1 tab", reminderMinute = 8 * 60, taken = taken)

    @Test
    fun `doses due shows the count and taken-fraction, taps to meds`() {
        val content = chronosMedsComplicationContent(
            WearDaySummary(medsDueCount = 2, meds = listOf(med(false), med(true), med(false)))
        )

        assertEquals("2 due", content.short)
        assertEquals("Meds", content.title)
        assertEquals("2 doses due", content.long)
        assertEquals(WearStartPage.MEDS, content.tapPage)
        assertEquals(1f / 3f, content.progress!!, 0.01f) // 1 of 3 taken
    }

    @Test
    fun `a single due dose reads in the singular`() {
        val content = chronosMedsComplicationContent(
            WearDaySummary(medsDueCount = 1, meds = listOf(med(false)))
        )

        assertEquals("1 due", content.short)
        assertEquals("1 dose due", content.long)
    }

    @Test
    fun `all taken shows a check and a full bar`() {
        val content = chronosMedsComplicationContent(
            WearDaySummary(medsDueCount = 0, meds = listOf(med(true), med(true)))
        )

        assertEquals("✓", content.short)
        assertEquals("All doses taken", content.long)
        assertEquals(1f, content.progress!!, 0.001f)
    }

    @Test
    fun `a synced day with no meds reads as none`() {
        assertEquals(
            "No meds today",
            chronosMedsComplicationContent(WearDaySummary(receivedAtMillis = 1L)).long
        )
    }
}
