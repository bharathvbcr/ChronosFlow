package com.ChronosFlow.VBCR.core.data.reading

import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls

/** Metadata extracted from a fetched HTML page. All fields are best-effort and may be null. */
data class ReadingPageMetadata(
    val title: String?,
    val faviconUrl: String?,
    val wordCount: Int?,
    val estimatedReadMinutes: Int?
)

/**
 * Pure, dependency-free parser for the small slice of page metadata the reading list needs. Kept
 * Android-free so it can be unit-tested without a fetch. Order of preference for the title is
 * og:title → <title> → null.
 */
object ReadingMetadataParser {
    private const val WORDS_PER_MINUTE = 200
    private const val MAX_TITLE_LENGTH = 200

    private val OG_TITLE = Regex(
        """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TITLE_TAG = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ICON_LINK = Regex(
        """<link[^>]+rel=["'][^"']*icon[^"']*["'][^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HREF = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val HEAD = Regex("""<head[^>]*>.*?</head>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_STYLE = Regex("""<(script|style)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("""<[^>]+>""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(html: String, baseUrl: String): ReadingPageMetadata {
        val title = (OG_TITLE.find(html)?.groupValues?.get(1) ?: TITLE_TAG.find(html)?.groupValues?.get(1))
            ?.let(::decodeBasicEntities)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_TITLE_LENGTH)

        val faviconUrl = resolveFaviconUrl(html, baseUrl)

        val words = wordCount(html)
        val minutes = if (words > 0) ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1) else null

        return ReadingPageMetadata(
            title = title,
            faviconUrl = faviconUrl,
            wordCount = words.takeIf { it > 0 },
            estimatedReadMinutes = minutes
        )
    }

    private fun resolveFaviconUrl(html: String, baseUrl: String): String? {
        val href = ICON_LINK.find(html)?.value?.let { HREF.find(it)?.groupValues?.get(1) }?.trim()
        val resolved = href?.let { resolveUrl(it, baseUrl) }
        if (resolved != null) return resolved
        // Default well-known location.
        val scheme = baseUrl.substringBefore("://", "https")
        val host = ReadingUrls.domainOf(baseUrl)
        return if (host.isNotBlank()) "$scheme://$host/favicon.ico" else null
    }

    /** Resolves a possibly-relative [href] against [baseUrl]. Handles protocol-relative and root paths. */
    internal fun resolveUrl(href: String, baseUrl: String): String? {
        if (href.isBlank()) return null
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("//") -> baseUrl.substringBefore("://", "https") + ":" + href
            href.startsWith("/") -> {
                val scheme = baseUrl.substringBefore("://", "https")
                val host = ReadingUrls.domainOf(baseUrl)
                if (host.isBlank()) null else "$scheme://$host$href"
            }
            else -> baseUrl.substringBeforeLast('/', baseUrl).trimEnd('/') + "/" + href
        }
    }

    private fun wordCount(html: String): Int {
        val text = html
            .replace(HEAD, " ")
            .replace(SCRIPT_STYLE, " ")
            .replace(TAG, " ")
            .let(::decodeBasicEntities)
            .replace(WHITESPACE, " ")
            .trim()
        if (text.isEmpty()) return 0
        return text.split(' ').count { it.isNotBlank() }
    }

    private fun decodeBasicEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
}
