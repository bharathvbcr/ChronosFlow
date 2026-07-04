package com.ChronosFlow.VBCR.widget

import android.content.Context
import com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryPublisher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pushes wear day summary only — no widget re-render. */
@Singleton
class WearDaySummaryPublisherImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WearDaySummaryPublisher {
    override suspend fun publish() {
        ChronosWidgetHub.publishWearSummary(context)
    }
}
