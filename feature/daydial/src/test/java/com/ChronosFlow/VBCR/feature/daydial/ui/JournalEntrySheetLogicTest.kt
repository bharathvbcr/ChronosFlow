package com.ChronosFlow.VBCR.feature.daydial.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalEntrySheetLogicTest {

    @Test
    fun `word count ignores blank runs`() {
        assertEquals(0, journalWordCount(""))
        assertEquals(0, journalWordCount("   \n  "))
        assertEquals(1, journalWordCount("  hello  "))
        assertEquals(3, journalWordCount("a good\nday"))
    }
}
