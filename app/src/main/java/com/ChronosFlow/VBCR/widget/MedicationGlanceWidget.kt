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
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewMedication
import com.ChronosFlow.VBCR.core.notifications.PrivacyRedaction
import com.ChronosFlow.VBCR.core.notifications.SECTION_MEDICATION
import dagger.hilt.android.EntryPointAccessors

/**
 * "Medication" widget: today's doses with reminder times, pending-first, each with a Taken
 * button that records a real dose event. Names honour the privacy redaction preference, same
 * as medication notifications.
 */
class MedicationGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        val overview = runCatching { entryPoint.dayOverviewUseCase()() }
            .getOrDefault(ChronosDayOverview())
        val redactNames = runCatching {
            entryPoint.privacyPreferences().redactMedicationOnWidgets()
        }.getOrDefault(true)
        provideContent {
            GlanceTheme {
                MedicationContent(overview.medications, redactNames)
            }
        }
    }

    @Composable
    private fun MedicationContent(medications: List<DayOverviewMedication>, redactNames: Boolean) {
        val medicationLimit = if (LocalSize.current.height >= TALL.height) MEDICATION_LIMIT_TALL else MEDICATION_LIMIT
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background)
                .clickable(openSectionAction(LocalContext.current, SECTION_MEDICATION))
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = "Medication",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (medications.isNotEmpty()) {
                    Text(
                        text = "${medications.count { it.isTakenToday }}/${medications.size} taken",
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (medications.isEmpty()) {
                Text(
                    text = "No active medications",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                )
                return@Column
            }
            medications.take(medicationLimit).forEach { medication ->
                MedicationRow(medication, redactNames)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun MedicationRow(medication: DayOverviewMedication, redactNames: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = PrivacyRedaction.medicationWidgetLabel(medication.name, redactNames),
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = "${medication.doseLabel} · at ${formatMinuteOfDay(medication.reminderMinuteOfDay)}",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (medication.isTakenToday) {
                Text(
                    text = "Taken ✓",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            } else {
                Button(
                    text = "Taken",
                    onClick = actionRunCallback<DoseAcknowledgementAction>(
                        actionParametersOf(
                            ActionParameters.Key<String>(DoseAcknowledgementAction.PLAN_ID_KEY) to medication.id,
                            ActionParameters.Key<Boolean>(DoseAcknowledgementAction.TAKEN_KEY) to true
                        )
                    )
                )
            }
        }
    }

    private fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 1439)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    private companion object {
        const val MEDICATION_LIMIT = 3
        const val MEDICATION_LIMIT_TALL = 6
        val COMPACT = DpSize(180.dp, 110.dp)
        val TALL = DpSize(180.dp, 240.dp)
    }
}

class MedicationWidgetReceiver : ChronosWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedicationGlanceWidget()
}
