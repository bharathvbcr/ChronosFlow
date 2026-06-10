package com.chronosflow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
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
import com.chronosflow.core.domain.model.ChronosWidgetSummary
import com.chronosflow.core.domain.model.WidgetFocusState
import com.chronosflow.core.notifications.PrivacyRedaction
import dagger.hilt.android.EntryPointAccessors

class ChronosGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        val summary = runCatching {
            entryPoint.widgetSummaryUseCase()()
        }.getOrDefault(ChronosWidgetSummary())
        val redactMedication = runCatching {
            entryPoint.privacyPreferences().redactMedicationOnWidgets()
        }.getOrDefault(true)
        provideContent {
            GlanceTheme {
                WidgetContent(
                    summary = summary,
                    medicationLabel = PrivacyRedaction.medicationWidgetLabel(
                        summary.medicationName,
                        redactMedicationNames = redactMedication
                    )
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        summary: ChronosWidgetSummary,
        medicationLabel: String?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = "ChronosFlow",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = focusStatusLine(summary),
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )
            scheduleHintLine(summary)?.let { hint ->
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = hint,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
                )
            }
            if (medicationLabel != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = medicationLabel,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            FocusControls(summary.focusState)
            if (medicationLabel != null || summary.habitId != null) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                LoggingControls(
                    medicationId = summary.medicationId,
                    habitId = summary.habitId
                )
            }
            if (summary.habitTitle != null) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = summary.habitTitle!!,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
                )
            }
        }
    }

    @Composable
    private fun FocusControls(focusState: WidgetFocusState) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            when (focusState) {
                WidgetFocusState.IDLE -> focusButton("Start", FocusWidgetAction.ACTION_START)
                WidgetFocusState.RUNNING -> {
                    focusButton("Pause", FocusWidgetAction.ACTION_PAUSE)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    focusButton("Stop", FocusWidgetAction.ACTION_STOP)
                }
                WidgetFocusState.PAUSED -> {
                    focusButton("Resume", FocusWidgetAction.ACTION_RESUME)
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    focusButton("Stop", FocusWidgetAction.ACTION_STOP)
                }
            }
        }
    }

    @Composable
    private fun focusButton(label: String, action: String) {
        Button(
            text = label,
            onClick = actionRunCallback<FocusWidgetAction>(
                actionParametersOf(
                    ActionParameters.Key<String>(FocusWidgetAction.ACTION_KEY) to action
                )
            )
        )
    }

    @Composable
    private fun LoggingControls(medicationId: String?, habitId: String?) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            if (medicationId != null) {
                Button(
                    text = "Taken",
                    onClick = actionRunCallback<DoseAcknowledgementAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(DoseAcknowledgementAction.PLAN_ID_KEY) to medicationId,
                            ActionParameters.Key<Boolean>(DoseAcknowledgementAction.TAKEN_KEY) to true
                        )
                    )
                )
            }
            if (habitId != null) {
                if (medicationId != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Button(
                    text = "Done",
                    onClick = actionRunCallback<HabitMarkAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(HabitMarkAction.HABIT_ID_KEY) to habitId
                        )
                    )
                )
            }
        }
    }

    private fun focusStatusLine(summary: ChronosWidgetSummary): String = when (summary.focusState) {
        WidgetFocusState.RUNNING -> "Focusing · ${formatTimeLeft(summary.focusTimeLeftSeconds)} left"
        WidgetFocusState.PAUSED -> "Paused · ${formatTimeLeft(summary.focusTimeLeftSeconds)} left"
        WidgetFocusState.IDLE -> summary.currentBlockTitle?.let { "Now: $it" } ?: "No focus session"
    }

    private fun scheduleHintLine(summary: ChronosWidgetSummary): String? {
        // While idle, surface what's coming up. During a session the timer already tells the story.
        if (summary.focusState != WidgetFocusState.IDLE) return null
        val title = summary.nextBlockTitle ?: return null
        val start = summary.nextBlockStartMinuteOfDay
        return if (start != null) "Next: $title · ${formatMinuteOfDay(start)}" else "Next: $title"
    }

    private fun formatTimeLeft(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return "%d:%02d".format(safe / 60, safe % 60)
    }

    private fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 1439)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }
}
