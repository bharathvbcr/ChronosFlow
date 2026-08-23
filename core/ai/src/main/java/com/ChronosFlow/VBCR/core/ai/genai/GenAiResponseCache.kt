package com.ChronosFlow.VBCR.core.ai.genai

/**
 * Bounded, access-ordered replay cache with a per-entry TTL, shared by the on-device GenAI gateways
 * and the cloud gateway. Repeating the same request — re-opening a plan, re-proofreading unchanged
 * text — returns instantly without a cold inference or network round-trip. In-memory and
 * size-bounded, so nothing is persisted off-device.
 *
 * All access is guarded by an internal lock: unlike the on-device paths (effectively serialised by
 * single inference), the cloud gateway can be entered from several features concurrently, and a
 * plain [LinkedHashMap] would race under simultaneous [get]/[put].
 */
internal class GenAiResponseCache(
    private val maxEntries: Int,
    private val ttlMs: Long
) {
    private data class Entry(val value: String, val storedAtMs: Long)

    private val lock = Any()

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    fun get(key: String, nowMs: Long): String? = synchronized(lock) {
        val entry = entries[key] ?: return null
        if (nowMs - entry.storedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: String, nowMs: Long) = synchronized(lock) {
        entries[key] = Entry(value, nowMs)
    }
}
