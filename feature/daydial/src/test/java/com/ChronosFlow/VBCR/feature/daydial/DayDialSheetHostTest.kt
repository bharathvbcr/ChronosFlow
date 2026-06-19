package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.feature.daydial.model.SheetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayDialSheetHostTest {

    @Test
    fun `resolveDayDialSheetTarget clears stale block editor when selected block is gone`() {
        assertNull(
            resolveDayDialSheetTarget(
                activeSheet = SheetTarget.BlockEditor("deleted"),
                selectedBlockId = null
            )
        )
    }

    @Test
    fun `resolveDayDialSheetTarget clears stale block editor when selection no longer matches`() {
        assertNull(
            resolveDayDialSheetTarget(
                activeSheet = SheetTarget.BlockEditor("old"),
                selectedBlockId = "new"
            )
        )
    }

    @Test
    fun `resolveDayDialSheetTarget keeps matching block editor`() {
        val target = SheetTarget.BlockEditor("block")

        assertEquals(
            target,
            resolveDayDialSheetTarget(activeSheet = target, selectedBlockId = "block")
        )
    }

    @Test
    fun `resolveDayDialSheetTarget keeps non block sheets without selection`() {
        assertEquals(
            SheetTarget.AiPlan,
            resolveDayDialSheetTarget(activeSheet = SheetTarget.AiPlan, selectedBlockId = null)
        )
    }
}
