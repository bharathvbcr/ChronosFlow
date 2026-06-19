package com.ChronosFlow.VBCR.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Shared lifecycle for all ChronosFlow widget receivers: every widget type keeps the single
 * [WidgetRefreshWorker] loop armed, and the loop is only cancelled once the last widget of
 * any type has been removed *and* no recently active watch still needs it.
 */
abstract class ChronosWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Re-arm after reboot / process death; KEEP avoids resetting an active tick.
        WidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Keep the loop alive if a watch still relies on it; otherwise stop it.
        if (!WidgetRefreshWorker.shouldKeepRunning(context)) {
            WidgetRefreshWorker.cancel(context)
        }
    }
}
