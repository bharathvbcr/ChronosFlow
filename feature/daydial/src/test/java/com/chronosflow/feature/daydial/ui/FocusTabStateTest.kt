package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTabStateTest {
    @Test
    fun `only running and paused states render active session controls`() {
        assertTrue(isFocusSessionActiveForTab(FocusExecutionState(status = FocusExecutionStatus.RUNNING)))
        assertTrue(isFocusSessionActiveForTab(FocusExecutionState(status = FocusExecutionStatus.PAUSED)))

        assertFalse(isFocusSessionActiveForTab(FocusExecutionState()))
        assertFalse(isFocusSessionActiveForTab(FocusExecutionState(status = FocusExecutionStatus.FINISHED)))
        assertFalse(isFocusSessionActiveForTab(FocusExecutionState(status = FocusExecutionStatus.SKIPPED)))
    }

    @Test
    fun `skip action only appears for linked active sessions`() {
        assertTrue(
            shouldShowFocusTabSkipAction(
                FocusExecutionState(blockId = "block-1", status = FocusExecutionStatus.RUNNING)
            )
        )
        assertTrue(
            shouldShowFocusTabSkipAction(
                FocusExecutionState(blockId = "block-1", status = FocusExecutionStatus.PAUSED)
            )
        )

        assertFalse(
            shouldShowFocusTabSkipAction(
                FocusExecutionState(blockId = null, status = FocusExecutionStatus.RUNNING)
            )
        )
        assertFalse(
            shouldShowFocusTabSkipAction(
                FocusExecutionState(blockId = "block-1", status = FocusExecutionStatus.FINISHED)
            )
        )
    }

    @Test
    fun `active title falls back to restored session title before generic copy`() {
        assertEquals(
            "Selected block",
            focusTabActiveTitle("Selected block", FocusExecutionState(blockTitle = "Restored block"))
        )
        assertEquals(
            "Restored block",
            focusTabActiveTitle(null, FocusExecutionState(blockTitle = "Restored block"))
        )
        assertEquals(
            "Focus session",
            focusTabActiveTitle("", FocusExecutionState(blockTitle = ""))
        )
    }

    @Test
    fun `primary control description names the concrete focus action`() {
        assertEquals(
            "Pause session",
            focusTabPrimaryControlContentDescription(FocusExecutionStatus.RUNNING)
        )
        assertEquals(
            "Resume session",
            focusTabPrimaryControlContentDescription(FocusExecutionStatus.PAUSED)
        )
        assertEquals(
            "Start session",
            focusTabPrimaryControlContentDescription(FocusExecutionStatus.IDLE)
        )
    }

    @Test
    fun `adjustment action label names the time change`() {
        assertEquals("Extend session by 5 minutes", focusSessionAdjustmentActionLabel(5))
        assertEquals("Shorten session by 5 minutes", focusSessionAdjustmentActionLabel(-5))
        assertEquals("Keep session duration unchanged", focusSessionAdjustmentActionLabel(0))
    }

    @Test
    fun `session resumed dismiss label names the notice`() {
        assertEquals("Dismiss session resumed notice", focusSessionResumedDismissActionLabel())
    }

    @Test
    fun `all day calendar imports are not focus start candidates`() {
        val calendarNote = allDayCalendarNote()
        val focusBlock = focusBlock()

        assertNull(focusTabReadyBlock(calendarNote, sessionActive = false))
        assertEquals(calendarNote, focusTabAllDayCalendarNoteBlock(calendarNote, sessionActive = false))
        assertNull(focusTabAllDayCalendarNoteBlock(calendarNote, sessionActive = true))
        assertNull(focusTabNextFocusableBlock(calendarNote))
        assertEquals(focusBlock, focusTabReadyBlock(focusBlock, sessionActive = false))
        assertEquals(focusBlock, focusTabNextFocusableBlock(focusBlock))
        assertEquals(
            "Birthday stays visible all day without blocking your schedule, reminders, or focus suggestions.",
            focusTabAllDayCalendarNoteMessage(calendarNote)
        )
    }

    @Test
    fun `completed blocks are not surfaced as ready to focus`() {
        val completed = focusBlock().copy(
            actualStartMinuteOfDay = 10 * 60,
            actualEndMinuteOfDay = 10 * 60 + 45
        )
        assertNull(focusTabReadyBlock(completed, sessionActive = false))
        // An incomplete block is still offered as a focus-start candidate.
        assertEquals(focusBlock(), focusTabReadyBlock(focusBlock(), sessionActive = false))
    }

    @Test
    fun `focus capture draft is shown only when no ready session is taking priority`() {
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "launch brief · 45m"
            ),
            focusTabDraftUiState(
                capture = "  focus 45 minutes on launch brief  ",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )

        assertNull(
            focusTabDraftUiState(
                capture = "focus 45 minutes on launch brief",
                selectedReadyBlock = focusBlock(),
                sessionActive = false
            )
        )
        assertNull(
            focusTabDraftUiState(
                capture = "focus 45 minutes on launch brief",
                selectedReadyBlock = null,
                sessionActive = true
            )
        )
        assertNull(
            focusTabDraftUiState(
                capture = " ",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
    }

    @Test
    fun `focus capture draft summarizes parsed block details`() {
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "launch brief · 2:00 PM · 45m"
            ),
            focusTabDraftUiState(
                capture = "focus at 2pm for 45 minutes on launch brief",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "quarterly memo · 9:30 AM"
            ),
            focusTabDraftUiState(
                capture = "deep work from 09:30 on quarterly memo",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "launch brief · 1:00 PM · 1h 30m"
            ),
            focusTabDraftUiState(
                capture = "focus at 1pm for 1 hour 30 minutes on launch brief",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "launch brief · 10:00 AM · 30m"
            ),
            focusTabDraftUiState(
                capture = "focus at 10am for half an hour on launch brief",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "launch brief · 12:00 PM · 30m"
            ),
            focusTabDraftUiState(
                capture = "focus at noon for half an hour on launch brief",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
        assertEquals(
            FocusTabDraftUiState(
                title = "Focus draft ready",
                message = "review launch brief"
            ),
            focusTabDraftUiState(
                capture = "review launch brief",
                selectedReadyBlock = null,
                sessionActive = false
            )
        )
    }

    @Test
    fun `focus capture block title removes command and duration language`() {
        assertEquals("launch brief", focusCaptureBlockTitle("focus 45 minutes on launch brief"))
        assertEquals("launch brief", focusCaptureBlockTitle("focus on a 45-minute launch brief"))
        assertEquals("launch brief", focusCaptureBlockTitle("focus at 2pm for 45 minutes on launch brief"))
        assertEquals("launch brief", focusCaptureBlockTitle("focus at noon for 45 minutes on launch brief"))
        assertEquals("migration window", focusCaptureBlockTitle("deep work from midnight on migration window"))
        assertEquals("launch brief", focusCaptureBlockTitle("focus for half an hour on launch brief"))
        assertEquals("launch brief", focusCaptureBlockTitle("focus for 1 hour 30 minutes on launch brief"))
        assertEquals("quarterly memo", focusCaptureBlockTitle("deep work from 09:30 on quarterly memo"))
        assertEquals("quarterly memo", focusCaptureBlockTitle("Deep work: quarterly memo"))
        assertEquals("review launch brief", focusCaptureBlockTitle("review launch brief"))
        assertEquals("Focus Block", focusCaptureBlockTitle("  "))
    }

    @Test
    fun `focus capture block duration reads minutes and hours`() {
        assertEquals(45, focusCaptureBlockDurationMinutes("focus 45 minutes on launch brief"))
        assertEquals(90, focusCaptureBlockDurationMinutes("deep work 1.5 hours on quarterly memo"))
        assertEquals(120, focusCaptureBlockDurationMinutes("focus 2h on roadmap"))
        assertEquals(90, focusCaptureBlockDurationMinutes("focus 1 hour 30 minutes on launch brief"))
        assertEquals(90, focusCaptureBlockDurationMinutes("focus 1h 30m on launch brief"))
        assertEquals(75, focusCaptureBlockDurationMinutes("focus for 1 hr 15 min on roadmap"))
        assertEquals(45, focusCaptureBlockDurationMinutes("focus on a 45-minute launch brief"))
        assertEquals(60, focusCaptureBlockDurationMinutes("focus for an hour on roadmap"))
        assertEquals(30, focusCaptureBlockDurationMinutes("focus half an hour on launch brief"))
        assertEquals(5, focusCaptureBlockDurationMinutes("focus 3 minutes on triage"))
        assertEquals(240, focusCaptureBlockDurationMinutes("focus 10 hours on strategy"))
        assertNull(focusCaptureBlockDurationMinutes("review launch brief"))
    }

    @Test
    fun `focus capture block start reads explicit clock times`() {
        assertEquals(14 * 60, focusCaptureBlockStartMinute("focus at 2pm for 45 minutes on launch brief"))
        assertEquals(9 * 60 + 30, focusCaptureBlockStartMinute("deep work from 09:30 on quarterly memo"))
        assertEquals(14 * 60 + 15, focusCaptureBlockStartMinute("focus at 2:15 PM on roadmap"))
        assertEquals(0, focusCaptureBlockStartMinute("focus at 12am on triage"))
        assertEquals(12 * 60, focusCaptureBlockStartMinute("focus at 12pm on planning"))
        assertEquals(12 * 60, focusCaptureBlockStartMinute("focus at noon on launch brief"))
        assertEquals(12 * 60, focusCaptureBlockStartMinute("deep work start at noon on launch brief"))
        assertEquals(0, focusCaptureBlockStartMinute("deep work from midnight on migration window"))
        assertNull(focusCaptureBlockStartMinute("focus at 2 on launch brief"))
        assertNull(focusCaptureBlockStartMinute("review launch brief"))
    }

    @Test
    fun `session briefing summarizes ready focus block and next handoff`() {
        val briefing = focusTabBriefingUiState(
            currentBlock = focusBlock(),
            nextBlock = nextBlock(),
            sessionActive = false,
            remainingSeconds = 0L,
            elapsedSeconds = 0L
        )

        assertEquals(
            FocusTabBriefingUiState(
                window = "10:00 AM - 10:45 AM · 45m",
                pace = "Ready when you are",
                nextStep = FocusTabNextStepUiState(
                    label = "15m reset before Review notes",
                    urgent = false
                )
            ),
            briefing
        )
    }

    @Test
    fun `session briefing shows active pace and urgent overlaps`() {
        val briefing = focusTabBriefingUiState(
            currentBlock = focusBlock(),
            nextBlock = nextBlock(startMinuteOfDay = 10 * 60 + 30),
            sessionActive = true,
            remainingSeconds = 30 * 60L,
            elapsedSeconds = 15 * 60L
        )

        assertEquals(
            FocusTabBriefingUiState(
                window = "10:00 AM - 10:45 AM · 45m",
                pace = "15m elapsed · 30m left",
                nextStep = FocusTabNextStepUiState(
                    label = "Overlaps Review notes by 15m",
                    urgent = true
                )
            ),
            briefing
        )
    }

    @Test
    fun `session briefing uses restored session metadata when block is not loaded`() {
        val briefing = focusTabBriefingUiState(
            currentBlock = null,
            nextBlock = nextBlock(startMinuteOfDay = 15 * 60),
            focusSession = FocusExecutionState(
                blockId = "restored-block",
                blockTitle = "Restored planning",
                blockStartMinute = 14 * 60,
                plannedDurationMinutes = 50,
                status = FocusExecutionStatus.RUNNING
            ),
            sessionActive = true,
            remainingSeconds = 25 * 60L,
            elapsedSeconds = 25 * 60L
        )

        assertEquals(
            FocusTabBriefingUiState(
                window = "2:00 PM - 2:50 PM · 50m",
                pace = "25m elapsed · 25m left",
                nextStep = FocusTabNextStepUiState(
                    label = "10m tight handoff to Review notes",
                    urgent = true
                )
            ),
            briefing
        )
    }

    @Test
    fun `session briefing is hidden without a real focus block`() {
        assertNull(
            focusTabBriefingUiState(
                currentBlock = allDayCalendarNote(),
                nextBlock = nextBlock(),
                sessionActive = false,
                remainingSeconds = 0L,
                elapsedSeconds = 0L
            )
        )
        assertNull(
            focusTabBriefingUiState(
                currentBlock = null,
                nextBlock = nextBlock(),
                sessionActive = false,
                remainingSeconds = 0L,
                elapsedSeconds = 0L
            )
        )
    }

    private fun focusBlock(): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "focus-block",
            title = "Draft brief",
            startMinuteOfDay = 10 * 60,
            durationMinutes = 45,
            color = Color.Blue,
            category = "WORK"
        )
    }

    private fun allDayCalendarNote(): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "birthday",
            title = "Birthday",
            startMinuteOfDay = 0,
            durationMinutes = 15,
            color = Color.Gray,
            provenance = BlockProvenance.CALENDAR_IMPORTED.name,
            calendarEventId = 42L,
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY
        )
    }

    private fun nextBlock(startMinuteOfDay: Int = 11 * 60): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "next-block",
            title = "Review notes",
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = 30,
            color = Color.Green,
            category = "ADMIN"
        )
    }
}
