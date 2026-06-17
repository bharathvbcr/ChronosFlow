package com.chronosflow.widget

import android.content.Context
import com.chronosflow.core.domain.model.BlockCategories
import com.chronosflow.core.domain.model.ChronosDayOverview
import com.chronosflow.core.domain.wear.WearDaySummaryContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Generic block label used when sensitive titles must not leave the phone. */
internal const val REDACTED_BLOCK_TITLE = "Scheduled block"

/**
 * The Data Layer key/value entries for a day overview. Kept as a plain function (mirroring
 * WearFocusPayload.toDataEntries in :feature:focus) so the publish contract is unit-testable
 * without Google Play services on the classpath.
 *
 * With [redactTitles] (the redact-sensitive-notifications preference, same one the focus
 * mirror honours), block titles become generic and the per-item task/habit/medication
 * payloads are dropped entirely — the watch still shows times and counts, never free text,
 * and offers no per-item actions for those surfaces.
 *
 * Each list entry packs the fields the watch needs (including the entity id, so the watch can
 * act on it) joined by [WearDaySummaryContract.FIELD_SEP].
 */
internal fun ChronosDayOverview.toWearDaySummaryEntries(
    redactTitles: Boolean = false,
    digest: String? = null
): Map<String, Any> {
    val sep = WearDaySummaryContract.FIELD_SEP
    val entries = linkedMapOf<String, Any>()
    // The digest can mention the next block's title, so it never leaves the phone redacted.
    if (!redactTitles) {
        digest?.takeIf { it.isNotBlank() }?.let { entries[WearDaySummaryContract.KEY_DIGEST] = it }
    }
    currentBlock?.let {
        entries[WearDaySummaryContract.KEY_NOW_TITLE] = if (redactTitles) REDACTED_BLOCK_TITLE else it.title
        entries[WearDaySummaryContract.KEY_NOW_END_MINUTE] = it.endMinuteOfDay
        // A bare id leaks no title, so it flows even when redacted — lets the watch complete it.
        entries[WearDaySummaryContract.KEY_NOW_BLOCK_ID] = it.id
        // Category (not sensitive) flows even redacted, so the watch can gate "Start focus".
        entries[WearDaySummaryContract.KEY_NOW_CATEGORY] = it.category
    }
    // Split the next break out from the next event so the watch mirrors the phone notification's
    // "next event + next break" glance instead of just naming whichever block comes next.
    val upcoming = blocks.filterNot { it.isCurrent }
    val nextEvent = upcoming.firstOrNull { !BlockCategories.isBreak(it.category) }
    val nextBreak = upcoming.firstOrNull { BlockCategories.isBreak(it.category) }
    nextEvent?.let {
        entries[WearDaySummaryContract.KEY_NEXT_TITLE] = if (redactTitles) REDACTED_BLOCK_TITLE else it.title
        entries[WearDaySummaryContract.KEY_NEXT_START_MINUTE] = it.startMinuteOfDay
    }
    nextBreak?.let {
        entries[WearDaySummaryContract.KEY_NEXT_BREAK_START_MINUTE] = it.startMinuteOfDay
        // The break's start (a time) is safe; its title is dropped under redaction.
        if (!redactTitles) entries[WearDaySummaryContract.KEY_NEXT_BREAK_TITLE] = it.title
    }
    // Times only — safe to publish even when titles are redacted; feeds the watch day dial.
    entries[WearDaySummaryContract.KEY_BLOCK_ENTRIES] = blocks
        .take(WearDaySummaryContract.MAX_BLOCK_ENTRIES)
        .map { "${it.startMinuteOfDay}$sep${it.endMinuteOfDay}" }
        .toTypedArray()
    entries[WearDaySummaryContract.KEY_OPEN_TASK_COUNT] = openTasks.size
    entries[WearDaySummaryContract.KEY_TASK_ENTRIES] = if (redactTitles) {
        emptyArray()
    } else {
        openTasks.take(WearDaySummaryContract.MAX_ENTRIES)
            .map { "${it.id}$sep${it.title}" }
            .toTypedArray()
    }
    entries[WearDaySummaryContract.KEY_HABITS_DONE] = habitsDoneToday
    entries[WearDaySummaryContract.KEY_HABITS_TOTAL] = habits.size
    entries[WearDaySummaryContract.KEY_HABIT_ENTRIES] = if (redactTitles) {
        emptyArray()
    } else {
        habits.take(WearDaySummaryContract.MAX_ENTRIES)
            .map { "${it.id}$sep${if (it.isDoneToday) 1 else 0}$sep${it.streakCount}$sep${it.title}" }
            .toTypedArray()
    }
    entries[WearDaySummaryContract.KEY_MEDS_DUE_COUNT] = medications.count { !it.isTakenToday }
    entries[WearDaySummaryContract.KEY_MED_ENTRIES] = if (redactTitles) {
        emptyArray()
    } else {
        medications.take(WearDaySummaryContract.MAX_ENTRIES)
            .map {
                "${it.id}$sep${if (it.isTakenToday) 1 else 0}$sep${it.reminderMinuteOfDay}" +
                    "$sep${it.doseLabel}$sep${it.name}"
            }
            .toTypedArray()
    }
    return entries
}

/**
 * Publishes the day summary to the Wearable Data Layer so the paired watch's Today and Habits
 * tiles can mirror it (see the :wear module's DaySummaryWearListenerService).
 *
 * All calls are best-effort and never throw — Wearable APIs are absent on devices without
 * Google Play services.
 */
@Singleton
class WearDaySummaryBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val linkStatusStore: WearLinkStatusStore
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun publish(overview: ChronosDayOverview, redactTitles: Boolean = false, digest: String? = null) {
        runCatching {
            val request = PutDataMapRequest.create(WearDaySummaryContract.DAY_SUMMARY_PATH).apply {
                overview.toWearDaySummaryEntries(redactTitles, digest).forEach { (key, value) ->
                    when (value) {
                        is Boolean -> dataMap.putBoolean(key, value)
                        is Int -> dataMap.putInt(key, value)
                        is Long -> dataMap.putLong(key, value)
                        is String -> dataMap.putString(key, value)
                        is Array<*> -> dataMap.putStringArray(
                            key,
                            value.filterIsInstance<String>().toTypedArray()
                        )
                    }
                }
            // Urgent like the focus mirror: non-urgent items batch for minutes and the watch
            // looks like it simply isn't syncing.
            }.asPutDataRequest().setUrgent()
            // Stamp on handoff success so Privacy & Sync can show an accurate "Last synced …".
            dataClient.putDataItem(request)
                .addOnSuccessListener { linkStatusStore.recordPublished() }
        }
    }
}
