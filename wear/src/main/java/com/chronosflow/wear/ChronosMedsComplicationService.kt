package com.chronosflow.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.presentation.WearStartPage

/** Watch-face complication: medication doses due today ("1 due" / all-taken), tapping to Meds. */
class ChronosMedsComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildChronosComplicationData(
            this,
            type,
            ChronosComplicationContent("1 due", "Meds", "1 dose due", 0.5f, WearStartPage.MEDS)
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        buildChronosComplicationData(
            this,
            request.complicationType,
            chronosMedsComplicationContent(DaySummaryStore.read(this))
        ) ?: NoDataComplicationData()
}

/** Maps the mirrored medication state to complication content. Pure for unit testing. */
internal fun chronosMedsComplicationContent(summary: WearDaySummary): ChronosComplicationContent {
    val due = summary.medsDueCount
    val totalMeds = summary.meds.size
    val taken = summary.meds.count { it.taken }
    // Progress = how many of today's doses are taken, when the dose list isn't privacy-redacted.
    val progress = if (totalMeds > 0) (taken.toFloat() / totalMeds).coerceIn(0f, 1f) else null
    return when {
        due > 0 -> ChronosComplicationContent(
            short = "$due due",
            title = "Meds",
            long = if (due == 1) "1 dose due" else "$due doses due",
            progress = progress,
            tapPage = WearStartPage.MEDS
        )
        totalMeds > 0 -> ChronosComplicationContent(
            short = "✓",
            title = "Meds",
            long = "All doses taken",
            progress = 1f,
            tapPage = WearStartPage.MEDS
        )
        else -> ChronosComplicationContent(
            short = "—",
            title = "Meds",
            long = if (summary.receivedAtMillis == 0L) "Open on phone to sync" else "No meds today",
            progress = null,
            tapPage = WearStartPage.MEDS
        )
    }
}
