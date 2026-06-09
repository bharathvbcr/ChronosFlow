package com.chronosflow.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class ChronosGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChronosGlanceWidget()

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(ChronosGlanceWidget::class.java).forEach { id ->
                ChronosGlanceWidget().update(context, id)
            }
        }
    }
}
