package com.ChronosFlow.VBCR.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class TileDayLineTest {

    @Test
    fun `folds the remaining-block count in with the task count`() {
        assertEquals("3 blocks left · 2 tasks open", tileDayLine(blocksLeft = 3, openTaskCount = 2))
        assertEquals("1 block left · No open tasks", tileDayLine(blocksLeft = 1, openTaskCount = 0))
    }

    @Test
    fun `drops the block count when none remain`() {
        assertEquals("2 tasks open", tileDayLine(blocksLeft = 0, openTaskCount = 2))
        assertEquals("No open tasks", tileDayLine(blocksLeft = 0, openTaskCount = 0))
    }
}
