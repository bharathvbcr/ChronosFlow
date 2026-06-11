package com.chronosflow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
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
import com.chronosflow.core.domain.model.ChronosDayOverview
import com.chronosflow.core.domain.model.WidgetFocusState
import com.chronosflow.core.notifications.SECTION_FOCUS
import dagger.hilt.android.EntryPointAccessors

/**
 * "Focus" widget: one job — the focus session. A prominent countdown with state-appropriate
 * controls while a session is active; the current/next block plus a Start button while idle.
 * Schedule lists, tasks, habits, and medication each have their own dedicated widget.
 */
class ChronosGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetActionEntryPoint::class.java
        )
        val overview = runCatching { entryPoint.dayOverviewUseCase()() }
            .getOrDefault(ChronosDayOverview())
        // Cache-only read of the pre-generated daily digest (Gemini Nano fills it while the app
        // is foregrounded) — the widget never runs inference itself.
        val digest = runCatching {
            entryPoint.proactiveAssistGenerator()
                .cachedCopy(java.time.LocalDate.now().toString(), System.currentTimeMillis())
                ?.text
        }.getOrNull()
        provideContent {
            GlanceTheme {
                FocusContent(overview, digest)
            }
        }
    }

    @Composable
    private fun FocusContent(overview: ChronosDayOverview, digest: String?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = "Focus",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                modifier = GlanceModifier.clickable(openSectionAction(LocalContext.current, SECTION_FOCUS))
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            when (overview.focus.state) {
                WidgetFocusState.RUNNING -> ActiveSession(
                    statusLabel = "Focusing",
                    timeLeftSeconds = overview.focus.timeLeftSeconds,
                    primaryAction = "Pause" to FocusWidgetAction.ACTION_PAUSE
                )
                WidgetFocusState.PAUSED -> ActiveSession(
                    statusLabel = "Paused",
                    timeLeftSeconds = overview.focus.timeLeftSeconds,
                    primaryAction = "Resume" to FocusWidgetAction.ACTION_RESUME
                )
                WidgetFocusState.IDLE -> IdleSession(overview, digest)
            }
        }
    }

    @Composable
    private fun ActiveSession(
        statusLabel: String,
        timeLeftSeconds: Int,
        primaryAction: Pair<String, String>
    ) {
        Text(
            text = statusLabel,
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
        )
        Text(
            text = formatTimeLeft(timeLeftSeconds),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            focusButton(primaryAction.first, primaryAction.second)
            Spacer(modifier = GlanceModifier.width(8.dp))
            focusButton("Stop", FocusWidgetAction.ACTION_STOP)
        }
    }

    @Composable
    private fun IdleSession(overview: ChronosDayOverview, digest: String?) {
        Text(
            text = idleContextLine(overview),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            maxLines = 2
        )
        digest?.let { text ->
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 2
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        focusButton("Start focus", FocusWidgetAction.ACTION_START)
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

    private fun idleContextLine(overview: ChronosDayOverview): String {
        overview.currentBlock?.let {
            return "Now: ${it.title} · until ${formatMinuteOfDay(it.endMinuteOfDay)}"
        }
        overview.nextBlock?.let {
            return "Next: ${it.title} · ${formatMinuteOfDay(it.startMinuteOfDay)}"
        }
        return "No session running"
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
