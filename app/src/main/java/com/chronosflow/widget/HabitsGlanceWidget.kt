package com.chronosflow.widget

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
import com.chronosflow.core.domain.model.ChronosDayOverview
import com.chronosflow.core.domain.model.DayOverviewHabit
import com.chronosflow.navigation.SECTION_HABITS
import dagger.hilt.android.EntryPointAccessors

/**
 * "Habits" widget: every active habit with its streak, undone-first, each with a one-tap Done
 * button (reusing [HabitMarkAction], the same callback the main widget uses).
 */
class HabitsGlanceWidget : GlanceAppWidget() {
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
                HabitsContent(overview.habits, overview.habitsDoneToday)
            }
        }
    }

    @Composable
    private fun HabitsContent(habits: List<DayOverviewHabit>, doneToday: Int) {
        val habitLimit = if (LocalSize.current.height >= TALL.height) HABIT_LIMIT_TALL else HABIT_LIMIT
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(openSectionAction(LocalContext.current, SECTION_HABITS))
            ) {
                Text(
                    text = "Habits",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (habits.isNotEmpty()) {
                    Text(
                        text = "$doneToday/${habits.size} done",
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (habits.isEmpty()) {
                Text(
                    text = "No active habits yet",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                )
                return@Column
            }
            habits.take(habitLimit).forEach { habit ->
                HabitRow(habit)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun HabitRow(habit: DayOverviewHabit) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = habit.title,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            if (habit.streakCount > 0) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "${habit.streakCount}🔥",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (habit.isDoneToday) {
                Text(
                    text = "Done ✓",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            } else {
                Button(
                    text = "✓",
                    onClick = actionRunCallback<HabitMarkAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(HabitMarkAction.HABIT_ID_KEY) to habit.id
                        )
                    )
                )
            }
        }
    }

    private companion object {
        const val HABIT_LIMIT = 4
        const val HABIT_LIMIT_TALL = 8
        val COMPACT = DpSize(180.dp, 110.dp)
        val TALL = DpSize(180.dp, 240.dp)
    }
}

class HabitsWidgetReceiver : ChronosWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsGlanceWidget()
}
