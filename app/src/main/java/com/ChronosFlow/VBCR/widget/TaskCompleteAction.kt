package com.ChronosFlow.VBCR.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskCompleteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[ActionParameters.Key<String>(TASK_ID_KEY)] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        withContext(Dispatchers.IO) {
            entryPoint.toggleTaskCompletionUseCase()(taskId)
        }
        ChronosWidgetHub.refreshAll(context)
    }

    companion object {
        const val TASK_ID_KEY = "taskId"
    }
}
