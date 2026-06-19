package com.ChronosFlow.VBCR.core.ai.genai

/**
 * Bounded, access-ordered replay cache with a per-entry TTL, shared by the on-device GenAI gateways.
 * Repeating the same request — re-opening a plan, re-proofreading unchanged text — returns instantly
 * without a cold inference. In-memory and size-bounded, so nothing is persisted off-device.
 *
 * Not thread-safe on its own; callers access it from suspend functions on a single dispatcher, which
 * the effectively serial nature of on-device inference keeps from racing.
 */
internal class GenAiResponseCache(
    private val maxEntries: Int,
    private val ttlMs: Long
) {
    private data class Entry(val value: String, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    fun get(key: String, nowMs: Long): String? {
        val entry = entries[key] ?: return null
        if (nowMs - entry.storedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: String, nowMs: Long) {
        entries[key] = Entry(value, nowMs)
    }
}
