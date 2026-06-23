package com.ChronosFlow.VBCR.core.domain.model

/** Pure URL helpers shared by share intake, the reading-list UI, and the metadata worker. */
object ReadingUrls {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** The first http(s) URL found in [text], or null. Trailing punctuation is trimmed. */
    fun firstUrlIn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_REGEX.find(text)?.value ?: return null
        return match.trimEnd('.', ',', ')', ']', '}', '!', '?', ';', ':', '"', '\'')
    }

    /** Whether [text], trimmed, is itself a single http(s) URL. */
    fun looksLikeUrl(text: String?): Boolean {
        val trimmed = text?.trim().orEmpty()
        return trimmed.isNotEmpty() && firstUrlIn(trimmed) == trimmed
    }

    /** Host of [url] without a leading "www.", or the trimmed input if it can't be parsed. */
    fun domainOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.removePrefix("www.").ifBlank { url.trim() }
    }
}
