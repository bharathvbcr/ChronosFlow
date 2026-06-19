package com.ChronosFlow.VBCR.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.ChronosFlow.VBCR.wear.presentation.WearStartPage

/**
 * Shared building blocks for the ChronosFlow watch-face complications (Now / Habits / Meds), so the
 * services differ only in the data they map and the page they open — never in how a slot is built.
 */

/** Display content for a complication, independent of the slot type it ends up rendered in. */
internal data class ChronosComplicationContent(
    /** Compact glance (~7 chars): "40m", "→25m", "3/5", "1 due". */
    val short: String,
    /** Optional title line for slots that show one. */
    val title: String?,
    /** Full single line for LONG_TEXT and as the accessibility description. */
    val long: String,
    /** 0..1 for the RANGED_VALUE arc, or null when the datum has no natural progress. */
    val progress: Float?,
    /** Which app page a tap opens (a [WearStartPage] value). */
    val tapPage: String,
    /**
     * When non-null, the slot's text renders as a live [TimeDifferenceComplicationText] counting
     * down to this epoch-milli instant, so the watch face ticks it second-by-second without a data
     * refresh; the static [short]/[long] then serve only as the preview/fallback. Null for data
     * that has no live countdown (next-up, free, habits, meds, paused focus).
     */
    val countDownToMillis: Long? = null
)

/** Builds the [type] rendering of [content], or null for an unsupported slot type. */
internal fun buildChronosComplicationData(
    context: Context,
    type: ComplicationType,
    content: ChronosComplicationContent
): ComplicationData? {
    val tap = chronosComplicationTapAction(context, content.tapPage)
    val description = PlainComplicationText.Builder(content.long).build()
    fun plain(value: String) = PlainComplicationText.Builder(value).build()
    // A live, face-ticked countdown when the datum has an end instant; the static snapshot otherwise.
    val countdown: ComplicationText? = content.countDownToMillis?.let { endMillis ->
        TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            CountDownTimeReference(java.time.Instant.ofEpochMilli(endMillis))
        ).build()
    }
    val shortText = countdown ?: plain(content.short)
    val longText = countdown ?: plain(content.long)
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(shortText, description)
            .apply {
                content.title?.let { setTitle(plain(it)) }
                setTapAction(tap)
            }
            .build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(longText, description)
            .apply {
                content.title?.let { setTitle(plain(it)) }
                setTapAction(tap)
            }
            .build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = content.progress ?: 0f,
            min = 0f,
            max = 1f,
            contentDescription = description
        )
            .apply {
                setText(shortText)
                content.title?.let { setTitle(plain(it)) }
                setTapAction(tap)
            }
            .build()
        else -> null
    }
}

private fun chronosComplicationTapAction(context: Context, page: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).putExtra(WearStartPage.EXTRA, page)
    return PendingIntent.getActivity(
        context,
        page.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/**
 * Refreshes every ChronosFlow complication so the watch face reflects a just-arrived day-summary /
 * focus change instead of waiting for the periodic update. Each request is wrapped so it's a no-op
 * when that complication isn't currently hosted by a face.
 */
internal fun requestChronosComplicationUpdates(context: Context) {
    listOf(
        ChronosNowComplicationService::class.java,
        ChronosHabitsComplicationService::class.java,
        ChronosMedsComplicationService::class.java
    ).forEach { service ->
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, service))
                .requestUpdateAll()
        }
    }
}
