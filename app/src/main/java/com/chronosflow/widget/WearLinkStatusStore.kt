package com.chronosflow.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records when the phone last handed a day summary to the Wearable Data Layer, so the Privacy &
 * Sync settings can show "Last synced …". [WearDaySummaryBridge] stamps it on every successful
 * push; [WearLinkStatusProviderImpl] reads it.
 */
@Singleton
class WearLinkStatusStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Epoch-millis of the last successful publish, or 0 if the phone has never pushed. */
    fun lastPublishedAtMillis(): Long = prefs.getLong(KEY_LAST_PUBLISHED_AT, 0L)

    fun recordPublished(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_PUBLISHED_AT, nowMillis).apply()
    }

    /** Epoch-millis the watch last talked to the phone (any action / sync request), or 0 if never. */
    fun lastWatchActivityMillis(): Long = prefs.getLong(KEY_LAST_WATCH_ACTIVITY, 0L)

    fun recordWatchActivity(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_WATCH_ACTIVITY, nowMillis).apply()
    }

    /**
     * Whether the watch has talked to the phone within [windowMillis]. Used to keep the background
     * refresh loop alive for watch-only users (no home-screen widgets) without running forever once
     * the watch stops being used.
     */
    fun watchActiveWithin(windowMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = lastWatchActivityMillis()
        return last > 0L && nowMillis - last <= windowMillis
    }

    private companion object {
        const val PREFS_NAME = "chronos_wear_link_status"
        const val KEY_LAST_PUBLISHED_AT = "last_published_at"
        const val KEY_LAST_WATCH_ACTIVITY = "last_watch_activity"
    }
}
