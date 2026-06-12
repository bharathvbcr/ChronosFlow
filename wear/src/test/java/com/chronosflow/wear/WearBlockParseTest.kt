package com.chronosflow.wear

import com.chronosflow.core.domain.wear.WearDaySummaryContract
import com.chronosflow.wear.model.WearBlock
import com.chronosflow.wear.model.parseBlocks
import org.junit.Assert.assertEquals
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
}
