package com.chronosflow.widget

import androidx.glance.appwidget.GlanceAppWidget

class ChronosGlanceWidgetReceiver : ChronosWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChronosGlanceWidget()
}
