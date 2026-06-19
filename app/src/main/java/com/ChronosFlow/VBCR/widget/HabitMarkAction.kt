package com.ChronosFlow.VBCR.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HabitMarkAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[ActionParameters.Key<String>(HABIT_ID_KEY)] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        withContext(Dispatchers.IO) {
            entryPoint.completeHabitByIdUseCase()(habitId, LocalDate.now())
        }
        ChronosWidgetHub.refreshAll(context)
    }

    companion object {
        const val HABIT_ID_KEY = "habitId"
    }
}
