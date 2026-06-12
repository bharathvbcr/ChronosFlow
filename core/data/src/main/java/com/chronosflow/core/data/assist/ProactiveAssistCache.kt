package com.chronosflow.core.data.assist

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class CachedAssistLine(
    val headline: String,
    val nextStep: String,
    val source: String
)

/**
 * Foreground-generated assist content for surfaces that cannot run GenAI
 * themselves (notifications posted from background services, the home-screen
 * widget). Entries are best-effort: a stale or absent entry means "no AI line"
 * and readers fall back to their local copy.
 */
@Singleton
class ProactiveAssistCache @Inject constructor(
    private val preferences: ChronosPreferencesDataSource
) {
    private val _dailyCoachWrites = MutableSharedFlow<LocalDate>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Fires after each daily-coach write so passive readers (widget) can refresh. */
    val dailyCoachWrites: SharedFlow<LocalDate> = _dailyCoachWrites.asSharedFlow()

    fun putDailyCoachLine(date: LocalDate, headline: String, nextStep: String, source: String) {
        preferences.putString(KEY_DAILY_COACH_DATE, date.toString())
        preferences.putString(KEY_DAILY_COACH_HEADLINE, headline)
        preferences.putString(KEY_DAILY_COACH_NEXT_STEP, nextStep)
        preferences.putString(KEY_DAILY_COACH_SOURCE, source)
        _dailyCoachWrites.tryEmit(date)
    }

    fun dailyCoachLine(date: LocalDate): CachedAssistLine? {
        if (preferences.getString(KEY_DAILY_COACH_DATE) != date.toString()) return null
        val headline = preferences.getString(KEY_DAILY_COACH_HEADLINE)
        if (headline.isBlank()) return null
        return CachedAssistLine(
            headline = headline,
            nextStep = preferences.getString(KEY_DAILY_COACH_NEXT_STEP),
            source = preferences.getString(KEY_DAILY_COACH_SOURCE)
        )
    }

    fun putFocusNextBlockLine(date: LocalDate, blockId: String, line: String) {
        preferences.putString(KEY_FOCUS_NEXT_DATE, date.toString())
        preferences.putString(KEY_FOCUS_NEXT_BLOCK_ID, blockId)
        preferences.putString(KEY_FOCUS_NEXT_LINE, line)
    }

    /** Returns the cached line only when it was generated today for [blockId]. */
    fun focusNextBlockLine(date: LocalDate, blockId: String): String? {
        if (preferences.getString(KEY_FOCUS_NEXT_DATE) != date.toString()) return null
        if (preferences.getString(KEY_FOCUS_NEXT_BLOCK_ID) != blockId) return null
        return preferences.getString(KEY_FOCUS_NEXT_LINE).ifBlank { null }
    }

    companion object {
        const val KEY_DAILY_COACH_DATE = "assist_cache_daily_coach_date"
        const val KEY_DAILY_COACH_HEADLINE = "assist_cache_daily_coach_headline"
        const val KEY_DAILY_COACH_NEXT_STEP = "assist_cache_daily_coach_next_step"
        const val KEY_DAILY_COACH_SOURCE = "assist_cache_daily_coach_source"
        const val KEY_FOCUS_NEXT_DATE = "assist_cache_focus_next_date"
        const val KEY_FOCUS_NEXT_BLOCK_ID = "assist_cache_focus_next_block_id"
        const val KEY_FOCUS_NEXT_LINE = "assist_cache_focus_next_line"
    }
}
