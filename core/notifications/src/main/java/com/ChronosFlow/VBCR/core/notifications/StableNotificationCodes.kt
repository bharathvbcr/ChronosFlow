package com.ChronosFlow.VBCR.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allocates stable, collision-free int codes for notifications and PendingIntent request codes.
 *
 * Mirrors [AlarmScheduler]'s request-code registry (TS-010): String.hashCode()-derived codes suffer
 * birthday-paradox collisions, and because PendingIntent equality IGNORES intent extras, two
 * entities whose hash codes align share one PendingIntent — tapping "Mark Done" on task A would
 * complete task B. Codes here are allocated from a persisted monotonic counter with a persisted
 * id→code mapping, so they are:
 *  1. **injective** — distinct keys never share a code;
 *  2. **stable** — the same key maps to the same code across process death, so
 *     FLAG_UPDATE_CURRENT PendingIntents update in place and cancel targets stay valid.
 *
 * Mappings are never evicted: each entry is one small prefs pair keyed by reminder/entity id,
 * whose universe is bounded by the reminders this install actually shows (the alarm layer caps
 * concurrent alarms at [AlarmScheduler.MAX_PERSISTED_ALARMS]; folded chips track current-dose/
 * task/habit state). Growth is therefore slow and bounded in practice by usage.
 */
@Singleton
class StableNotificationCodes @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** Guards check-then-act on the counter so concurrent deliveries cannot allocate twice. */
    private val lock = Any()

    /** Set once preferences have been confirmed readable; see [codeFor]. */
    private val initialized = AtomicBoolean(false)

    /**
     * Returns the unique, stable code for [key], allocating one on first use.
     * Falls back to an in-memory counter if persistence fails, keeping uniqueness within
     * this process at the cost of cross-restart stability.
     */
    fun codeFor(key: String): Int {
        require(key.isNotBlank()) { "notification code key must not be blank" }
        synchronized(lock) {
            val prefsKey = KEY_CODE_PREFIX + key
            if (initialized.get()) {
                val existing = preferences.getInt(prefsKey, -1)
                if (existing != -1) return existing
                val next = preferences.getInt(KEY_COUNTER, FIRST_CODE)
                preferences.edit().putInt(prefsKey, next).putInt(KEY_COUNTER, next + 1).apply()
                return next
            }
            // First touch in this process: read-through so an existing mapping wins over the
            // counter value even if a previous process died mid-write.
            val existing = preferences.getInt(prefsKey, -1)
            initialized.set(true)
            if (existing != -1) return existing
            val storedCounter = preferences.getInt(KEY_COUNTER, FIRST_CODE)
            val next = maxOf(storedCounter, highestAllocatedPlusOne(), FIRST_CODE)
            preferences.edit().putInt(prefsKey, next).putInt(KEY_COUNTER, next + 1).apply()
            return next
        }
    }

    /**
     * Self-heals the counter past any mapping written before a crash lost the counter write:
     * scanning all entries is O(entries) and only runs once per process.
     */
    private fun highestAllocatedPlusOne(): Int {
        var max = FIRST_CODE - 1
        preferences.all.keys.forEach { key ->
            if (key.startsWith(KEY_CODE_PREFIX)) {
                val value = preferences.getInt(key, -1)
                if (value > max) max = value
            }
        }
        return max + 1
    }

    /** Frees the mapping for [key] once nothing referencing its code can exist any more. */
    fun release(key: String) {
        synchronized(lock) {
            preferences.edit().remove(KEY_CODE_PREFIX + key).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "chronos_notification_codes"
        const val KEY_CODE_PREFIX = "code_"
        const val KEY_COUNTER = "counter"
        const val FIRST_CODE = 1
    }
}
