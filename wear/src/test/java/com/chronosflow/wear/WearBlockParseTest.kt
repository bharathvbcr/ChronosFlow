package com.chronosflow.wear

import com.chronosflow.core.domain.wear.WearDaySummaryContract
import com.chronosflow.wear.model.WearBlock
import com.chronosflow.wear.model.currentBlock
import com.chronosflow.wear.model.parseBlocks
import com.chronosflow.wear.model.upcomingBlockCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearBlockParseTest {

    private val sep = WearDaySummaryContract.FIELD_SEP

    @Test
    fun `parses packed start and end minutes`() {
        val blocks = parseBlocks(listOf("540${sep}600", "990${sep}1050"))
        assertEquals(
            listOf(WearBlock(540, 600), WearBlock(990, 1050)),
            blocks
        )
    }

    @Test
    fun `malformed entries are dropped`() {
        val blocks = parseBlocks(listOf("540", "abc${sep}600", "540${sep}xyz", "600${sep}660"))
        assertEquals(listOf(WearBlock(600, 660)), blocks)
    }

    @Test
    fun `current block is the one spanning now, null in a gap`() {
        val blocks = listOf(WearBlock(540, 600), WearBlock(660, 720)) // 9:00-10:00, 11:00-12:00
        assertEquals(WearBlock(540, 600), currentBlock(blocks, nowMinute = 570))   // 9:30 → first
        assertNull(currentBlock(blocks, nowMinute = 630))                          // 10:30 → gap
        assertNull(currentBlock(blocks, nowMinute = 600))                          // exactly at end → not current
    }

    @Test
    fun `upcoming count tallies only blocks starting after now`() {
        val blocks = listOf(WearBlock(540, 600), WearBlock(660, 720), WearBlock(780, 840))
        assertEquals(2, upcomingBlockCount(blocks, nowMinute = 570))  // 9:30 → two ahead
        assertEquals(0, upcomingBlockCount(blocks, nowMinute = 800))  // past the last start
    }
}
