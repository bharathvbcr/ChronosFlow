package com.ChronosFlow.VBCR.wear

import com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryContract
import com.ChronosFlow.VBCR.wear.model.WearFoldedReminderKind
import com.ChronosFlow.VBCR.wear.model.parseFoldedReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearFoldedParseTest {

    private val sep = WearDaySummaryContract.FIELD_SEP

    @Test
    fun `parses packed folded reminder entries`() {
        val parsed = parseFoldedReminders(
            listOf("0${sep}med-1${sep}Vitamin D${sep}Due 8:00 AM${sep}1")
        )
        assertEquals(1, parsed.size)
        assertEquals(WearFoldedReminderKind.MEDICATION, parsed[0].kind)
        assertEquals("med-1", parsed[0].entityId)
        assertEquals("Vitamin D", parsed[0].title)
        assertTrue(parsed[0].isOverdue)
    }

    @Test
    fun `malformed folded entries are dropped`() {
        val parsed = parseFoldedReminders(listOf("bad", "1${sep}task-1${sep}Taxes"))
        assertEquals(0, parsed.size)
    }
}
