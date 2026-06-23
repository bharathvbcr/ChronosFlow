package com.ChronosFlow.VBCR.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingUrlsTest {

    @Test
    fun firstUrlInExtractsFromSurroundingText() {
        assertEquals(
            "https://example.com/post",
            ReadingUrls.firstUrlIn("Check this https://example.com/post out!")
        )
    }

    @Test
    fun firstUrlInTrimsTrailingPunctuation() {
        assertEquals("https://example.com", ReadingUrls.firstUrlIn("See (https://example.com)."))
    }

    @Test
    fun firstUrlInReturnsNullWhenNoUrl() {
        assertNull(ReadingUrls.firstUrlIn("no link here"))
        assertNull(ReadingUrls.firstUrlIn(null))
    }

    @Test
    fun looksLikeUrlOnlyTrueForBareUrl() {
        assertTrue(ReadingUrls.looksLikeUrl("https://example.com/x"))
        assertTrue(ReadingUrls.looksLikeUrl("  https://example.com/x  "))
        assertFalse(ReadingUrls.looksLikeUrl("read https://example.com/x later"))
        assertFalse(ReadingUrls.looksLikeUrl("just text"))
    }

    @Test
    fun domainOfStripsSchemeWwwAndPath() {
        assertEquals("example.com", ReadingUrls.domainOf("https://www.example.com/a/b?c=1#d"))
        assertEquals("news.example.com", ReadingUrls.domainOf("http://news.example.com"))
    }
}
