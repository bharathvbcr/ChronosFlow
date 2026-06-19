package com.ChronosFlow.VBCR.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.ChronosFlow.VBCR.core.domain.model.DayOverviewBlock
import com.ChronosFlow.VBCR.core.notifications.SECTION_DAY
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Today's Schedule" widget: the current block with its end time, then the next few upcoming
 * blocks. Read-only by design — tapping anywhere opens the app on the day view.
 */
class AgendaGlanceWidget : GlanceAppWidget() {
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
                AgendaContent(overview)
            }
        }
    }

    @Composable
    private fun AgendaContent(overview: ChronosDayOverview) {
        val upcomingLimit = if (LocalSize.current.height >= TALL.height) UPCOMING_LIMIT_TALL else UPCOMING_LIMIT
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.background)
                .clickable(openSectionAction(LocalContext.current, SECTION_DAY))
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = LocalDate.now().format(HEADER_DATE),
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 12.sp)
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            if (overview.blocks.isEmpty()) {
                Text(
                    text = "Nothing left on today's schedule",
                    style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp)
                )
                return@Column
            }
            overview.currentBlock?.let { block ->
                CurrentBlockRow(block)
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
            overview.blocks.filterNot { it.isCurrent }.take(upcomingLimit).forEach { block ->
                UpcomingBlockRow(block)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }

    @Composable
    private fun CurrentBlockRow(block: DayOverviewBlock) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "Now · ${block.title}",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1
            )
            Text(
                text = "until ${formatMinuteOfDay(block.endMinuteOfDay)}",
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun UpcomingBlockRow(block: DayOverviewBlock) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = formatMinuteOfDay(block.startMinuteOfDay),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = block.title,
                style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 13.sp),
                maxLines = 1
            )
        }
    }

    private fun formatMinuteOfDay(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, 1439)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    private companion object {
        const val UPCOMING_LIMIT = 3
        const val UPCOMING_LIMIT_TALL = 7
        val COMPACT = DpSize(180.dp, 110.dp)
        val TALL = DpSize(180.dp, 240.dp)
        val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }
}

class AgendaWidgetReceiver : ChronosWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaGlanceWidget()
}
