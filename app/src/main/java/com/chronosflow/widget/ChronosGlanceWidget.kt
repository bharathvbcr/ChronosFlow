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
import com.chronosflow.core.data.assist.CachedAssistLine
import com.chronosflow.core.domain.model.ChronosWidgetSummary
import com.chronosflow.core.notifications.PrivacyRedaction
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

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
        // Pre-generated in the foreground app; the widget never runs GenAI itself.
        val coachLine = runCatching {
            entryPoint.proactiveAssistCache().dailyCoachLine(LocalDate.now())
        }.getOrNull()
        provideContent {
            GlanceTheme {
                WidgetContent(
                    habitId = summary.habitId,
                    habitTitle = summary.habitTitle,
                    medicationId = summary.medicationId,
                    medicationLabel = PrivacyRedaction.medicationWidgetLabel(
                        summary.medicationName,
                        redactMedicationNames = redactMedication
                    ),
                    coachLine = coachLine
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        habitId: String?,
        habitTitle: String?,
        medicationId: String?,
        medicationLabel: String?,
        coachLine: CachedAssistLine?
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
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (coachLine != null) {
                Text(
                    text = coachLine.headline,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp),
                    maxLines = 2
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
            Text(
                text = "Focus session",
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp)
            )
            if (medicationLabel != null) {
                Text(
                    text = medicationLabel,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Button(
                    text = "Start",
                    onClick = actionRunCallback<FocusWidgetAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(FocusWidgetAction.ACTION_KEY) to FocusWidgetAction.ACTION_START
                        )
                    )
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Button(
                    text = "Pause",
                    onClick = actionRunCallback<FocusWidgetAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(FocusWidgetAction.ACTION_KEY) to FocusWidgetAction.ACTION_PAUSE
                        )
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
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
                    Spacer(modifier = GlanceModifier.width(8.dp))
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
            if (habitTitle != null) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = habitTitle,
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
                )
            }
        }
    }
}
