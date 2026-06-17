package com.chronosflow.feature.daydial

import androidx.compose.ui.graphics.Color
import com.chronosflow.core.domain.model.ALL_DAY_CALENDAR_EVENT_CATEGORY
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.feature.daydial.model.DailyReview
import com.chronosflow.feature.daydial.model.SheetTarget
import com.chronosflow.feature.daydial.model.TimeBlockUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `focus settings reminder summary counts enabled reminders`() {
        assertEquals(
            "3 of 4 reminders on",
            focusSettingsReminderSummary(
                blockStartReminders = true,
                breakReminders = true,
                missedAlerts = false,
                endDayReviewReminder = true
            )
        )
        assertEquals(
            "0 of 4 reminders on",
            focusSettingsReminderSummary(
                blockStartReminders = false,
                breakReminders = false,
                missedAlerts = false,
                endDayReviewReminder = false
            )
        )
    }

    @Test
    fun `reminders need notification access only when enabled and notifications are off`() {
        // A reminder is on but notifications can't be delivered → needs access.
        assertTrue(
            focusRemindersNeedNotificationAccess(
                blockStartReminders = true,
                breakReminders = false,
                missedAlerts = false,
                endDayReviewReminder = false,
                notificationsReady = false
            )
        )
        // Notifications ready → no prompt even with reminders on.
        assertFalse(
            focusRemindersNeedNotificationAccess(
                blockStartReminders = true,
                breakReminders = true,
                missedAlerts = true,
                endDayReviewReminder = true,
                notificationsReady = true
            )
        )
        // No reminders enabled → nothing to alert, so no prompt.
        assertFalse(
            focusRemindersNeedNotificationAccess(
                blockStartReminders = false,
                breakReminders = false,
                missedAlerts = false,
                endDayReviewReminder = false,
                notificationsReady = false
            )
        )
    }

    @Test
    fun `daily goal label reports progress, completion, and none-set`() {
        assertEquals(
            "1h 30m of 2h daily goal (75%)",
            focusDailyGoalLabel(actualMinutes = 90, goalMinutes = 120)
        )
        assertEquals(
            "Daily goal reached — 2h 🎉",
            focusDailyGoalLabel(actualMinutes = 130, goalMinutes = 120)
        )
        assertEquals(null, focusDailyGoalLabel(actualMinutes = 90, goalMinutes = 0))
    }

    @Test
    fun `daily goal option label shows off or duration`() {
        assertEquals("Off", focusDailyGoalOptionLabel(0))
        assertEquals("1h", focusDailyGoalOptionLabel(60))
        assertEquals("3h", focusDailyGoalOptionLabel(180))
    }

    @Test
    fun `daily goal action label describes the target`() {
        assertEquals("Turn off the daily focus goal", sheetDailyGoalActionLabel(0))
        assertEquals("Set daily focus goal to 2h", sheetDailyGoalActionLabel(120))
    }

    @Test
    fun `focus session finish label projects wall-clock end time`() {
        // 2:30 PM + 25 min = 2:55 PM
        assertEquals("Ends 2:55 PM", focusSessionFinishLabel(currentMinuteOfDay = 14 * 60 + 30, remainingSeconds = 25 * 60))
        // 8:00 AM + 90 min = 9:30 AM
        assertEquals("Ends 9:30 AM", focusSessionFinishLabel(currentMinuteOfDay = 8 * 60, remainingSeconds = 90 * 60))
        // zero remaining ends at the current minute
        assertEquals("Ends 8:00 AM", focusSessionFinishLabel(currentMinuteOfDay = 8 * 60, remainingSeconds = 0))
    }

    @Test
    fun `focus session finish label wraps past midnight`() {
        // 11:50 PM + 20 min = 12:10 AM next day
        assertEquals("Ends 12:10 AM", focusSessionFinishLabel(currentMinuteOfDay = 23 * 60 + 50, remainingSeconds = 20 * 60))
        // noon boundary
        assertEquals("Ends 12:05 PM", focusSessionFinishLabel(currentMinuteOfDay = 11 * 60 + 55, remainingSeconds = 10 * 60))
    }

    @Test
    fun `today focus line summarizes logged time and completed blocks`() {
        assertEquals(
            "1h 30m focused · 2 blocks done",
            focusSettingsTodayFocusLine(
                DailyReview(plannedMinutes = 180, actualMinutes = 90, missedMinutes = 0, completedBlocks = 2)
            )
        )
        assertEquals(
            "0m focused · 1 block done",
            focusSettingsTodayFocusLine(
                DailyReview(plannedMinutes = 60, actualMinutes = 0, missedMinutes = 0, completedBlocks = 1)
            )
        )
    }

    @Test
    fun `today progress line reports percent of planned or none planned`() {
        assertEquals(
            "50% of 3h planned",
            focusSettingsTodayProgressLine(
                DailyReview(plannedMinutes = 180, actualMinutes = 90, missedMinutes = 0, completedBlocks = 2)
            )
        )
        assertEquals(
            "No focus blocks planned yet today.",
            focusSettingsTodayProgressLine(
                DailyReview(plannedMinutes = 0, actualMinutes = 0, missedMinutes = 0, completedBlocks = 0)
            )
        )
    }

    @Test
    fun `default break preset caption distinguishes no-breaks from split presets`() {
        assertEquals(
            "New focus sessions start as a single block with no breaks.",
            focusDefaultBreakPresetCaption(0)
        )
        assertEquals(
            "New focus sessions default to the 25 · 5 work·break split.",
            focusDefaultBreakPresetCaption(1)
        )
    }

    @Test
    fun `default break preset caption falls back to no-breaks for out-of-range index`() {
        assertEquals(
            "New focus sessions start as a single block with no breaks.",
            focusDefaultBreakPresetCaption(-1)
        )
        assertEquals(
            "New focus sessions start as a single block with no breaks.",
            focusDefaultBreakPresetCaption(99)
        )
    }

    @Test
    fun `default break preset action label names the preset`() {
        assertEquals("Set default break preset to 50 · 10", sheetDefaultBreakPresetActionLabel("50 · 10"))
    }

    @Test
    fun `block editor duration snaps five minutes short and fifteen minutes long`() {
        assertEquals(65, snapBlockEditorDuration(63f))
        assertEquals(5, snapBlockEditorDuration(3f))
        assertEquals(240, snapBlockEditorDuration(242f))
        assertEquals(195, snapBlockEditorDuration(190f))
        assertEquals(480, snapBlockEditorDuration(500f))
    }

    @Test
    fun `new block sheet initial duration honors prefilled target duration`() {
        assertEquals("45", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 45)))
        assertEquals("5", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 3)))
        assertEquals("400", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 400)))
        assertEquals("480", newBlockSheetInitialDurationText(SheetTarget.NewBlock(durationMinutes = 600)))
        assertEquals("25", newBlockSheetInitialDurationText(SheetTarget.NewBlock()))
    }

    @Test
    fun `block editor end picker derives a valid duration from start`() {
        assertEquals(75, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 10 * 60 + 15))
        assertEquals(90, blockEditorDurationFromEnd(startMinute = 23 * 60, endMinute = 30))
        assertEquals(5, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 9 * 60))
        assertEquals(360, blockEditorDurationFromEnd(startMinute = 9 * 60, endMinute = 15 * 60))
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
