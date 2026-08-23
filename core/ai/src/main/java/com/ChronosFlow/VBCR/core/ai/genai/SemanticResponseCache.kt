package com.ChronosFlow.VBCR.core.ai.genai

import kotlin.math.sqrt

/**
 * On-device semantic response cache — the near-duplicate layer that sits in front of the exact-key
 * [GenAiResponseCache]. Where the exact cache only replays a byte-identical prompt, this also reuses a
 * prior on-device generation for a prompt that differs only trivially (reordered words, whitespace,
 * a stray edit), so a cold Gemini Nano inference is skipped far more often.
 *
 * ### How a match is decided
 * Similarity is **term-frequency cosine** over tokenised prompt text — fully on-device and
 * dependency-free (no embedding model is shipped). A neighbour is accepted only when it:
 *  1. lives in the same [namespace] (the generation profile), so differently-sampled requests — a
 *     `DETERMINISTIC` plan vs a `CREATIVE` chat — never share an answer;
 *  2. clears [similarityThreshold]; and
 *  3. beats the runner-up neighbour by [similarityMargin] (ambiguity rejection — if two stored prompts
 *     are both "close", we cannot safely pick one, so we miss and let inference run).
 *
 * ### Why the caller passes `semanticText` separately from `exactKey`
 * Planning prompts are `prefix + suffix`, where the prefix is a large *static* role/schema block shared
 * by every request. Cosine over the whole prompt would be dominated by that shared prefix and could
 * merge two genuinely different days' plans. Callers therefore pass only the **dynamic** part as
 * [semanticText] (the suffix for prefix-cached calls, or the whole prompt for plain calls); the exact
 * replay key still covers the full prompt.
 *
 * Bounded, access-ordered, in-memory, per-entry TTL — nothing is persisted off-device. Unlike the
 * exact-only cache it augments, every method is guarded by an internal lock, so it is safe under the
 * coordinator's concurrent access.
 */
internal class SemanticResponseCache(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
    private val similarityMargin: Float = DEFAULT_SIMILARITY_MARGIN,
    private val minTokensForSemantic: Int = DEFAULT_MIN_TOKENS_FOR_SEMANTIC
) {
    private class Entry(
        val namespace: String,
        /** L2-normalised term-frequency vector of the entry's semantic text. */
        val vector: Map<String, Float>,
        val tokenCount: Int,
        /**
         * Tokens whose change signals a genuinely different request (dates, times, durations,
         * month/weekday names). A near-duplicate hit is accepted only when this set matches the
         * query's exactly, so "plan tomorrow" can never replay today's answer.
         */
        val salientTokens: Set<String>,
        val value: String,
        val storedAtMs: Long
    )

    private val lock = Any()

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    /**
     * Exact replay only: returns the value stored under [exactKey] if present and unexpired. Kept as a
     * fast path so identical repeats never pay the neighbour scan.
     */
    fun getExact(exactKey: String, nowMs: Long): String? = synchronized(lock) {
        val entry = entries[exactKey] ?: return null
        if (isExpired(entry, nowMs)) {
            entries.remove(exactKey)
            return null
        }
        return entry.value
    }

    /**
     * Exact first, then the best same-namespace near-duplicate of [semanticText]. Returns null (a miss)
     * unless a neighbour clears the threshold and the margin.
     */
    fun lookup(exactKey: String, namespace: String, semanticText: String, nowMs: Long): String? =
        synchronized(lock) {
            getExact(exactKey, nowMs)?.let { return it }

            val tokens = tokenize(semanticText)
            if (tokens.size < minTokensForSemantic) return null
            val queryVector = normalize(termFrequency(tokens))
            if (queryVector.isEmpty()) return null
            val querySalient = salientTokens(tokens)

            var bestKey: String? = null
            var bestScore = -1f
            var secondScore = -1f
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val (key, entry) = iterator.next()
                if (isExpired(entry, nowMs)) {
                    iterator.remove()
                    continue
                }
                if (entry.namespace != namespace) continue
                if (entry.tokenCount < minTokensForSemantic) continue
                val score = cosine(queryVector, entry.vector)
                if (score > bestScore) {
                    secondScore = bestScore
                    bestScore = score
                    bestKey = key
                } else if (score > secondScore) {
                    secondScore = score
                }
            }

            val key = bestKey ?: return null
            if (bestScore < similarityThreshold) return null
            // Ambiguity gate: a clear runner-up means we cannot safely pick a single answer.
            if (secondScore >= 0f && bestScore - secondScore < similarityMargin) return null

            // Staleness gates: cosine over term frequencies cannot tell a word reordering from a
            // changed date or an added task. Before replaying a near-duplicate, require that the
            // date/time-bearing tokens are identical and the overall shape (token count) is within
            // a few percent — cosmetic edits pass both; a different day or task list does not.
            val winnerCandidate = entries[key] ?: return null
            if (winnerCandidate.salientTokens != querySalient) return null
            val tokenDelta = kotlin.math.abs(winnerCandidate.tokenCount - tokens.size)
            if (tokenDelta > maxOf(MIN_TOKEN_COUNT_TOLERANCE, (winnerCandidate.tokenCount * TOKEN_COUNT_TOLERANCE_RATIO).toInt())) {
                return null
            }

            // Re-fetch through get() so access-order LRU marks the winner as most-recently used.
            val winner = entries[key] ?: return null
            if (isExpired(winner, nowMs)) {
                entries.remove(key)
                return null
            }
            return winner.value
        }

    fun put(exactKey: String, namespace: String, semanticText: String, value: String, nowMs: Long) =
        synchronized(lock) {
            val tokens = tokenize(semanticText)
            entries[exactKey] = Entry(
                namespace = namespace,
                vector = normalize(termFrequency(tokens)),
                tokenCount = tokens.size,
                salientTokens = salientTokens(tokens),
                value = value,
                storedAtMs = nowMs
            )
        }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun isExpired(entry: Entry, nowMs: Long): Boolean = nowMs - entry.storedAtMs > ttlMs

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(TOKEN_DELIMITER)
            .filter { it.length > 1 }

    /**
     * The tokens that identify *what* a request is about rather than how it is phrased: anything
     * containing a digit (dates like 2026-08-22, clock times, minute durations) plus month and
     * weekday names. Two prompts whose salient tokens differ describe different data even when
     * their cosine similarity is high.
     */
    private fun salientTokens(tokens: List<String>): Set<String> =
        tokens.filterTo(HashSet()) { token -> token.any(Char::isDigit) || token in DATE_TIME_WORDS }

    private fun termFrequency(tokens: List<String>): Map<String, Float> {
        if (tokens.isEmpty()) return emptyMap()
        val counts = HashMap<String, Float>(tokens.size)
        for (token in tokens) {
            counts[token] = (counts[token] ?: 0f) + 1f
        }
        return counts
    }

    private fun normalize(vector: Map<String, Float>): Map<String, Float> {
        if (vector.isEmpty()) return vector
        var sumSquares = 0f
        for (weight in vector.values) sumSquares += weight * weight
        val norm = sqrt(sumSquares)
        if (norm == 0f) return vector
        val out = HashMap<String, Float>(vector.size)
        for ((term, weight) in vector) out[term] = weight / norm
        return out
    }

    /** Dot product of two already L2-normalised vectors == cosine similarity. */
    private fun cosine(a: Map<String, Float>, b: Map<String, Float>): Float {
        // Iterate the smaller map for fewer lookups.
        val (small, large) = if (a.size <= b.size) a to b else b to a
        var dot = 0f
        for ((term, weight) in small) {
            val other = large[term] ?: continue
            dot += weight * other
        }
        return dot
    }

    companion object {
        /**
         * Conservative default: only confident near-duplicates merge. Cosine over term frequencies puts
         * word reorderings and single-token edits well above this, while genuinely different requests
         * (a different task list, a different day's data) fall below it.
         */
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.90f

        /** Reject a hit when a second stored prompt is nearly as close — the choice would be ambiguous. */
        const val DEFAULT_SIMILARITY_MARGIN = 0.05f

        /**
         * Below this token count a single word flips the meaning ("summarize" vs "shorten"), so short
         * prompts fall back to exact-only matching.
         */
        const val DEFAULT_MIN_TOKENS_FOR_SEMANTIC = 3

        /** Absolute token-count slack before shapes are considered different (protects tiny prompts). */
        private const val MIN_TOKEN_COUNT_TOLERANCE = 3

        /** Relative token-count slack (~one added task line on a full planning prompt). */
        private const val TOKEN_COUNT_TOLERANCE_RATIO = 0.05f

        /** Month and weekday names participate in the salient-token gate alongside digit tokens. */
        private val DATE_TIME_WORDS = setOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        )

        private val TOKEN_DELIMITER = Regex("[^\\p{L}\\p{N}]+")
    }
}
