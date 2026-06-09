package com.chronosflow.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DoseAcknowledgementAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val planId = parameters[ActionParameters.Key<String>(PLAN_ID_KEY)] ?: return
        val taken = parameters[ActionParameters.Key<Boolean>(TAKEN_KEY)] ?: true
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        withContext(Dispatchers.IO) {
            entryPoint.recordMedicationWidgetActionUseCase()(planId, taken)
        }
        ChronosGlanceWidgetReceiver.refreshAll(context)
    }

    companion object {
        const val PLAN_ID_KEY = "planId"
        const val TAKEN_KEY = "taken"
    }
}
