package com.ChronosFlow.VBCR.core.data.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingMetadataParserTest {

    @Test
    fun prefersOgTitleOverTitleTag() {
        val html = """
            <html><head>
            <title>Fallback Title</title>
            <meta property="og:title" content="The Real Title" />
            </head><body><p>Hello world</p></body></html>
        """.trimIndent()

        val result = ReadingMetadataParser.parse(html, "https://example.com/article")
        assertEquals("The Real Title", result.title)
    }

    @Test
    fun fallsBackToTitleTagAndDecodesEntities() {
        val html = "<html><head><title>Tips &amp; Tricks</title></head><body>x</body></html>"
        val result = ReadingMetadataParser.parse(html, "https://example.com")
        assertEquals("Tips & Tricks", result.title)
    }

    @Test
    fun estimatesReadTimeFromWordCount() {
        // ~400 words → about 2 minutes at 200 wpm.
        val body = (1..400).joinToString(" ") { "word" }
        val html = "<html><head><title>t</title></head><body><p>$body</p></body></html>"
        val result = ReadingMetadataParser.parse(html, "https://example.com")
        assertEquals(2, result.estimatedReadMinutes)
        assertNotNull(result.wordCount)
        assertTrue(result.wordCount!! >= 400)
    }

    @Test
    fun ignoresScriptAndStyleContentInWordCount() {
        val html = """
            <html><head><title>t</title><style>.a { color: red; }</style></head>
            <body><script>var x = 1; doSomething();</script><p>one two three</p></body></html>
        """.trimIndent()
        val result = ReadingMetadataParser.parse(html, "https://example.com")
        // Only the three visible words should be counted.
        assertEquals(3, result.wordCount)
    }

    @Test
    fun resolvesRelativeFaviconAgainstHost() {
        val html = """<html><head><link rel="icon" href="/assets/favicon.png"></head><body>x</body></html>"""
        val result = ReadingMetadataParser.parse(html, "https://news.example.com/2026/story")
        assertEquals("https://news.example.com/assets/favicon.png", result.faviconUrl)
    }

    @Test
    fun fallsBackToWellKnownFaviconWhenNoLink() {
        val html = "<html><head><title>t</title></head><body>x</body></html>"
        val result = ReadingMetadataParser.parse(html, "https://example.com/page")
        assertEquals("https://example.com/favicon.ico", result.faviconUrl)
    }
}
