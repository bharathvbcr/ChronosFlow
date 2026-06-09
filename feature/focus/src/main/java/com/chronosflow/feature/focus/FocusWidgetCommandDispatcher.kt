package com.chronosflow.feature.focus

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
}
