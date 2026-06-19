package com.ChronosFlow.VBCR.core.domain.notifications

/**
 * Surfaces a gentle "you're more distracted than usual" nudge. Defined in domain so the background
 * screen-time worker (core:data) can trigger it without depending on the notification layer; the
 * implementation that actually posts a notification lives in core:notifications.
 */
interface ScreenTimeNudgePresenter {
    fun notifyDistraction(todayDistractingMinutes: Int, averageDistractingMinutes: Int)
}
