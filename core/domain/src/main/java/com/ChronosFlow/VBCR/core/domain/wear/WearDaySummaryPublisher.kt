package com.ChronosFlow.VBCR.core.domain.wear

/**
 * Lightweight push of the day summary (including folded reminders) to the paired watch without
 * re-rendering home-screen widgets. Implemented in the app module via [ChronosWidgetHub].
 */
fun interface WearDaySummaryPublisher {
    suspend fun publish()
}
