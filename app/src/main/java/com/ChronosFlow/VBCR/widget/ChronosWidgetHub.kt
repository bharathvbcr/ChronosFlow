package com.ChronosFlow.VBCR.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.EntryPointAccessors

/**
 * Single refresh entry point for every ChronosFlow glanceable surface: it re-renders all
 * home-screen widgets and pushes the day summary to the paired watch in one pass. Widget
 * actions and the refresh worker call this so the phone and watch never drift apart.
 */
object ChronosWidgetHub {

    private val widgets: List<GlanceAppWidget>
        get() = listOf(
            ChronosGlanceWidget(),
            AgendaGlanceWidget(),
            TasksGlanceWidget(),
            HabitsGlanceWidget(),
            MedicationGlanceWidget()
        )

    private val receivers = listOf(
        ChronosGlanceWidgetReceiver::class.java,
        AgendaWidgetReceiver::class.java,
        TasksWidgetReceiver::class.java,
        HabitsWidgetReceiver::class.java,
        MedicationWidgetReceiver::class.java
    )

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        widgets.forEach { widget ->
            runCatching {
                manager.getGlanceIds(widget.javaClass).forEach { id ->
                    widget.update(appContext, id)
                }
            }
        }
        publishWearSummary(appContext)
    }

    /**
     * Pushes the day summary to the paired watch without re-rendering widgets. Used when folded
     * reminder state changes on the live notification surface (fold toggle, boundary refresh).
     */
    suspend fun publishWearSummary(context: Context) {
        publishWearDaySummary(context.applicationContext)
    }

    /** True while at least one instance of any ChronosFlow widget is placed on a home screen. */
    fun hasAnyWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return receivers.any { receiver ->
            manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
        }
    }

    private suspend fun publishWearDaySummary(context: Context) {
        runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                WidgetActionEntryPoint::class.java
            )
            val overview = entryPoint.dayOverviewUseCase()()
            // Same preference the focus mirror honours: keep free-text titles off the watch.
            val redactTitles = runCatching {
                entryPoint.privacyPreferences().redactSensitiveNotifications()
            }.getOrDefault(true)
            // Cache-only read of the pre-generated daily digest; never triggers inference here.
            val digest = runCatching {
                entryPoint.proactiveAssistGenerator()
                    .cachedCopy(java.time.LocalDate.now().toString(), System.currentTimeMillis())
                    ?.text
            }.getOrNull()
            entryPoint.wearDaySummaryBridge().publish(overview, redactTitles, digest)
            // Theme rides the same refresh pass; the Data Layer de-dups unchanged palettes.
            entryPoint.wearThemeBridge().publish()
        }
    }
}
