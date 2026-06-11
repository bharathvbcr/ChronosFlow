package com.chronosflow.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors

class FocusWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        entryPoint.focusWidgetCommandDispatcher().dispatch(
            context = context,
            widgetAction = parameters[ActionParameters.Key<String>(ACTION_KEY)]
        )
        ChronosWidgetHub.refreshAll(context)
    }

    companion object {
        const val ACTION_KEY = "focusAction"
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
    }
}
