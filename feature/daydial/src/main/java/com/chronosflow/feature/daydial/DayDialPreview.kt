package com.chronosflow.feature.daydial

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chronosflow.feature.daydial.ui.DailyReviewHeader
import com.chronosflow.feature.daydial.ui.DateNav
import com.chronosflow.feature.daydial.ui.TodayTab
import java.time.LocalDate

@Preview(showBackground = true)
@Composable
fun DayDialPreview() {
    MaterialTheme {
        val colors = MaterialTheme.colorScheme
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DateNav(
                selectedDate = LocalDate.now(),
                onPrev = {},
                onNext = {}
            )
            DailyReviewHeader(DailyReview(plannedMinutes = 260, actualMinutes = 135, missedMinutes = 30, completedBlocks = 2))
            TodayTab(
                timeBlocks = listOf(
                    TimeBlockUiModel(
                        id = "preview-focus",
                        title = "Deep Work",
                        startMinuteOfDay = 9 * 60,
                        durationMinutes = 90,
                        color = colors.primary
                    ),
                    TimeBlockUiModel(
                        id = "preview-break",
                        title = "Break",
                        startMinuteOfDay = 11 * 60,
                        durationMinutes = 15,
                        color = colors.secondary
                    )
                ),
                freeTime = listOf(TimeRangeUi(12 * 60, 13 * 60)),
                currentMinute = 9 * 60 + 20,
                selectedBlockId = "preview-focus",
                hapticCue = PlannerHapticCue.NONE,
                review = DailyReview(plannedMinutes = 260, actualMinutes = 135, missedMinutes = 30, completedBlocks = 2),
                activeBlock = TimeBlockUiModel(
                    id = "preview-focus",
                    title = "Deep Work",
                    startMinuteOfDay = 9 * 60,
                    durationMinutes = 90,
                    color = colors.primary
                ),
                nextBlock = null,
                missedBlocks = emptyList(),
                compactMode = false,
                compactWindowStart = 0,
                onBlockSelected = {},
                onBlockDragStarted = { _, _ -> },
                onBlockMoved = { _, _ -> },
                onBlockMoveCommitted = { _, _ -> },
                onBlockResize = { _, _, _ -> },
                onBlockResizeCommitted = { _, _, _ -> },
                onEmptyAreaSelected = {},
                onDragCancel = {},
                onStartFocus = {},
                onCompleteBlock = {},
                onShowMissed = {},
                onOpenPlanTab = {},
                onAiStripAction = {},
                onShowRingGuideChanged = {},
                onOpenPlanned = {},
                onOpenActual = {},
                onOpenMissedRecovery = {}
            )
        }
    }
}
