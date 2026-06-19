package com.ChronosFlow.VBCR.feature.focus

import android.content.Context
import javax.inject.Inject

object FocusWidgetCommand {
    const val ACTION_START = "start"
    const val ACTION_PAUSE = "pause"
    const val ACTION_RESUME = "resume"
    const val ACTION_STOP = "stop"

    fun serviceActionFor(widgetAction: String?): String? = when (widgetAction) {
        ACTION_START -> FocusService.ACTION_START
        ACTION_PAUSE -> FocusService.ACTION_PAUSE
        ACTION_RESUME -> FocusService.ACTION_RESUME
        ACTION_STOP -> FocusService.ACTION_STOP
        else -> null
    }
}

class FocusWidgetCommandDispatcher @Inject constructor() {
    fun dispatch(context: Context, widgetAction: String?) {
        val serviceAction = FocusWidgetCommand.serviceActionFor(widgetAction) ?: return
        context.sendFocusServiceCommand(
            action = serviceAction,
            timeLeft = 0,
            totalSeconds = 0,
            sessionId = null,
            archiveOnStop = widgetAction == FocusWidgetCommand.ACTION_STOP
        )
    }

    /**
     * Starts a fresh focus session of an explicit length (used by the watch's duration picker).
     * Passing [totalSeconds] as the hint makes [FocusService] honour it instead of falling back
     * to the default; a non-positive value defers to the standard [dispatch] start.
     */
    fun start(context: Context, totalSeconds: Int) {
        if (totalSeconds <= 0) {
            dispatch(context, FocusWidgetCommand.ACTION_START)
            return
        }
        context.sendFocusServiceCommand(
            action = FocusService.ACTION_START,
            timeLeft = 0,
            totalSeconds = totalSeconds,
            sessionId = null
        )
    }
}
