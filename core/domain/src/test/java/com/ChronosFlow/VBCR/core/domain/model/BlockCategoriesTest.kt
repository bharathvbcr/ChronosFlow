package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockCategoriesTest {

    @Test
    fun `isBreak matches the break category case- and whitespace-insensitively`() {
        assertTrue(BlockCategories.isBreak("break"))
        assertTrue(BlockCategories.isBreak("BREAK"))
        assertTrue(BlockCategories.isBreak("  Break "))
        assertFalse(BlockCategories.isBreak("work"))
        assertFalse(BlockCategories.isBreak(""))
    }

    @Test
    fun `supportsFocus excludes rest categories and keeps everything else`() {
        assertTrue(BlockCategories.supportsFocus("WORK"))
        assertTrue(BlockCategories.supportsFocus("study"))
        assertTrue(BlockCategories.supportsFocus("Exercise"))
        assertTrue(BlockCategories.supportsFocus("calendar"))
        assertTrue(BlockCategories.supportsFocus(""))
        assertFalse(BlockCategories.supportsFocus("break"))
        assertFalse(BlockCategories.supportsFocus(" Sleep "))
        assertFalse(BlockCategories.supportsFocus("meal"))
        assertFalse(BlockCategories.supportsFocus("food"))
    }
}
