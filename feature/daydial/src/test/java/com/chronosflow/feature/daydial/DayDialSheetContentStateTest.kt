package com.chronosflow.feature.daydial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDialSheetContentStateTest {

    @Test
    fun `block editor action labels name the affected block`() {
        val block = sampleBlock(title = "Draft proposal")

        assertEquals("Export Draft proposal to calendar", sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Export))
        assertEquals("Update calendar export for Draft proposal", sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Update))
        assertEquals("Remove calendar export for Draft proposal", sheetBlockCalendarActionLabel(block, SheetBlockCalendarAction.Remove))
        assertEquals("Start focus for Draft proposal", sheetBlockStartFocusActionLabel(block))
        assertEquals("Complete Draft proposal", sheetBlockCompleteActionLabel(block))
        assertEquals("Mark Draft proposal missed", sheetBlockMissedActionLabel(block))
        assertEquals("Undo missed mark for Draft proposal", sheetBlockUndoMissedActionLabel(block))
        assertEquals("Duplicate Draft proposal block", sheetBlockDuplicateActionLabel(block))
        assertEquals("Save changes to Draft proposal", sheetBlockSaveActionLabel(block))
        assertEquals("Delete Draft proposal block", sheetBlockDeleteActionLabel(block))
    }

    @Test
    fun `missed and review recovery labels name the affected block`() {
        val block = sampleBlock(title = "Review launch plan")

        assertEquals("Undo missed mark for Review launch plan", sheetMissedListUndoActionLabel(block))
        assertEquals("Reschedule Review launch plan", sheetMissedListRescheduleActionLabel(block))
        assertEquals("Copy Review launch plan from planned breakdown", sheetReviewCopyActionLabel(block))
        assertEquals("Recover Review launch plan from missed recovery", sheetReviewRecoverActionLabel(block))
    }

    @Test
    fun `focus adjustment labels describe the duration change`() {
        assertEquals("Add 5 minutes to focus time", sheetFocusAdjustmentActionLabel(5))
        assertEquals("Remove 10 minutes from focus time", sheetFocusAdjustmentActionLabel(-10))
        assertEquals("Keep focus time unchanged", sheetFocusAdjustmentActionLabel(0))
    }

    @Test
    fun `block editor duration slider uses five minute stops`() {
        assertEquals(46, blockEditorDurationSliderSteps())
        assertEquals(65, snapBlockEditorDuration(63f))
        assertEquals(5, snapBlockEditorDuration(3f))
        assertEquals(240, snapBlockEditorDuration(242f))
    }

    @Test
    fun `new block sheet initial duration honors prefilled target duration`() {
        assertEquals("45", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 45)))
        assertEquals("5", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 3)))
        assertEquals("240", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 400)))
        assertEquals("25", newBlockSheetInitialDurationText(SheetTarget.NewBlock()))
    }

    @Test
    fun `block editor end picker derives a valid duration from start`() {
        assertEquals(75, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 10 * 60 + 15))
        assertEquals(90, blockEditorDurationFromEnd(startMinute = 23 * 60, endMinute = 30))
        assertEquals(5, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 9 * 60))
        assertEquals(240, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 15 * 60))
    }

    @Test
    fun `block editor end picker snaps derived duration to five minutes`() {
        assertEquals(75, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 10 * 60 + 13))
        assertEquals(70, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 10 * 60 + 12))
    }

    @Test
    fun `sheet close label names the sheet`() {
        val block = sampleBlock(title = "Draft proposal")

        assertEquals("Close Draft proposal editor", sheetCloseActionLabel(SheetTarget.BlockEditor(block.id), block))
        assertEquals("Close quick add", sheetCloseActionLabel(SheetTarget.QuickAdd, null))
    }

    @Test
    fun `all day calendar note labels describe non blocking behavior`() {
        val block = sampleAllDayCalendarNote(title = "Festival")

        assertEquals("Remove Festival calendar note from DayDial", sheetBlockDeleteActionLabel(block))
        assertEquals(
            "Festival stays visible all day without blocking schedule time, reminders, or focus suggestions.",
            sheetBlockAllDayCalendarNoteMessage(block)
        )
    }

    private fun sampleBlock(title: String): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "block-1",
            title = title,
            startMinuteOfDay = 10 * 60,
            durationMinutes = 45,
            color = Color.Blue
        )
    }

    private fun sampleAllDayCalendarNote(title: String): TimeBlockUiModel {
        return TimeBlockUiModel(
            id = "calendar-note",
            title = title,
            startMinuteOfDay = 0,
            durationMinutes = 15,
            color = Color.Gray,
            provenance = BlockProvenance.CALENDAR_IMPORTED.name,
            calendarEventId = 42L,
            category = ALL_DAY_CALENDAR_EVENT_CATEGORY
        )
    }
}
