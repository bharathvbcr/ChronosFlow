package com.chronosflow.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.presentation.WearStartPage

/** Watch-face complication: today's habit completion (a progress arc / "3/5"), tapping to Habits. */
class ChronosHabitsComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildChronosComplicationData(
            this,
            type,
            ChronosComplicationContent("3/5", "Habits", "3 of 5 habits done", 0.6f, WearStartPage.HABITS)
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        buildChronosComplicationData(
            this,
            request.complicationType,
            chronosHabitsComplicationContent(DaySummaryStore.read(this))
        ) ?: NoDataComplicationData()
}

/** Maps the mirrored habit counts to complication content. Pure for unit testing. */
internal fun chronosHabitsComplicationContent(summary: WearDaySummary): ChronosComplicationContent {
    val total = summary.habitsTotal
    val done = summary.habitsDone
    if (total <= 0) {
        return ChronosComplicationContent(
            short = "—",
            title = "Habits",
            long = if (summary.receivedAtMillis == 0L) "Open on phone to sync" else "No habits today",
            progress = 0f,
            tapPage = WearStartPage.HABITS
        )
    }
    return ChronosComplicationContent(
        short = "$done/$total",
        title = "Habits",
        long = if (done >= total) "All $total habits done" else "$done of $total habits done",
        progress = (done.toFloat() / total).coerceIn(0f, 1f),
        tapPage = WearStartPage.HABITS
    )
}
