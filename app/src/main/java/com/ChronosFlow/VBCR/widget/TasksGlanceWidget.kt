package com.ChronosFlow.VBCR.widget


import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ChronosFlow.VBCR.core.domain.model.ChronosDayOverview
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewTask
import com.ChronosFlow.VBCR.core.notifications.SECTION_TASKS
import dagger.hilt.android.EntryPointAccessors

/**
 * "Tasks" widget: the most urgent open tasks with a one-tap Done button per row. Completing a
 * task refreshes every widget and syncs the watch via [ChronosWidgetHub].
 */
class TasksGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        val overview = runCatching { entryPoint.dayOverviewUseCase()() }
            .getOrDefault(ChronosDayOverview())
        provideContent {
            GlanceTheme {
                TasksContent(overview.openTasks)
            }
        }
    }

    @Composable
    private fun TasksContent(openTasks: List<DayOverviewTask>) {
        val taskLimit = if (LocalSize.current.height >= TALL.height) TASK_LIMIT_TALL else TASK_LIMIT
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background)
                .clickable(openSectionAction(LocalContext.current, SECTION_TASKS))
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tasks",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = if (openTasks.isEmpty()) "all done" else "${openTasks.size} open",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (openTasks.isEmpty()) {
                Text(
                    text = "All clear — no open tasks",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                )
                return@Column
            }
            openTasks.take(taskLimit).forEach { task ->
                TaskRow(task)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun TaskRow(task: DayOverviewTask) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (task.priority >= 2) "! ${task.title}" else task.title,
                style = TextStyle(
                    color = if (task.priority >= 2) GlanceTheme.colors.error else GlanceTheme.colors.onBackground,
                    fontWeight = if (task.priority >= 1) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Button(
                text = "✓",
                onClick = actionRunCallback<TaskCompleteAction>(
                    actionParametersOf(
                        ActionParameters.Key<String>(TaskCompleteAction.TASK_ID_KEY) to task.id
                    )
                )
            )
        }
    }

    private companion object {
        const val TASK_LIMIT = 4
        const val TASK_LIMIT_TALL = 8
        val COMPACT = DpSize(180.dp, 110.dp)
        val TALL = DpSize(180.dp, 240.dp)
    }
}

class TasksWidgetReceiver : ChronosWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksGlanceWidget()
}
