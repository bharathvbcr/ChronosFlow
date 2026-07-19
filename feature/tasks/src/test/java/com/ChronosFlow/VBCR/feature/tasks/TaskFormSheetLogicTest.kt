package com.ChronosFlow.VBCR.feature.tasks

import com.ChronosFlow.VBCR.core.ai.TaskAssistActionDraftPayload
import com.ChronosFlow.VBCR.core.ai.TaskAssistSource
import com.ChronosFlow.VBCR.core.ai.TaskAssistSuggestion
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskContactSnapshot
import com.ChronosFlow.VBCR.core.domain.model.TaskReminderTrigger
import com.ChronosFlow.VBCR.core.domain.usecase.ResolveNextTaskOccurrenceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskFormSheetLogicTest {

    @Test
    fun taskContextDraftSummaryUsesEmptyStateCopy() {
        val summary = taskContextDraftSummary(
            linkedContact = null,
            actionCount = 0,
            attachmentCount = 0
        )

        assertEquals(
            "People, photos, files, links, calls, emails, maps, and app shortcuts.",
            summary
        )
    }

    @Test
    fun taskContextDraftSummaryCombinesContactActionsAndAttachments() {
        val summary = taskContextDraftSummary(
            linkedContact = TaskContactSnapshot(displayName = "Alex"),
            actionCount = 2,
            attachmentCount = 1
        )

        assertEquals("Contact: Alex · 2 actions · 1 attachment", summary)
    }

    @Test
    fun taskContextDraftSummaryNamesPrimaryActionAndAttachment() {
        val summary = taskContextDraftSummary(
            linkedContact = TaskContactSnapshot(displayName = "Alex"),
            actionCount = 2,
            attachmentCount = 2,
            primaryActionLabel = "Email Alex",
            primaryAttachmentName = "brief.pdf"
        )

        assertEquals("Contact: Alex · Action: Email Alex + 1 more · File: brief.pdf + 1 more", summary)
    }

    @Test
    fun taskAssistActionDraftFromSuggestionBuildsValidPrimaryAction() {
        val suggestion = TaskAssistSuggestion.ActionDraft(
            id = "suggestion-1",
            label = "Email Alex",
            reason = "The title asks for an email follow-up.",
            source = TaskAssistSource.GEMINI_NANO,
            payload = TaskAssistActionDraftPayload(
                type = TaskActionType.EMAIL,
                label = "Email Alex",
                value = "alex@example.com"
            )
        )

        val draft = taskAssistActionDraftFromSuggestion(
            suggestion = suggestion,
            isPrimary = true,
            id = "draft-1"
        )

        assertEquals("draft-1", draft?.id)
        assertEquals(TaskActionType.EMAIL, draft?.type)
        assertEquals(true, draft?.isPrimary)
        assertEquals("alex@example.com", normalizeTaskActionDraft(draft!!)?.value)
    }

    @Test
    fun taskAssistActionDraftFromSuggestionRejectsIncompleteAction() {
        val suggestion = TaskAssistSuggestion.ActionDraft(
            id = "suggestion-1",
            label = "Call Alex",
            reason = "The title asks for a call.",
            source = TaskAssistSource.LOCAL,
            payload = TaskAssistActionDraftPayload(
                type = TaskActionType.PHONE,
                label = "Call Alex",
                value = ""
            )
        )

        assertNull(
            taskAssistActionDraftFromSuggestion(
                suggestion = suggestion,
                isPrimary = false,
                id = "draft-1"
            )
        )
    }

    @Test
    fun taskContextDetailsStayHiddenWhenCollapsedEvenWithSavedContext() {
        assertFalse(
            shouldShowTaskContextDetails(
                connectExpanded = false,
                hasContext = true
            )
        )
        assertTrue(
            shouldShowTaskContextDetails(
                connectExpanded = true,
                hasContext = false
            )
        )
    }

    @Test
    fun connectedFilesSectionStaysHiddenWhenContextIsCollapsed() {
        assertFalse(
            shouldShowConnectedFilesSection(
                connectExpanded = false,
                attachmentCount = 2
            )
        )
        assertTrue(
            shouldShowConnectedFilesSection(
                connectExpanded = true,
                attachmentCount = 0
            )
        )
    }

    @Test
    fun scheduleDetailsStayHiddenWhenCollapsedEvenWithSavedPreferences() {
        assertFalse(
            shouldShowTaskScheduleDetails(
                scheduleExpanded = false,
                hasSchedulePreferences = true
            )
        )
        assertTrue(
            shouldShowTaskScheduleDetails(
                scheduleExpanded = true,
                hasSchedulePreferences = false
            )
        )
    }

    @Test
    fun taskScheduleDraftSummaryUsesEmptyStateCopy() {
        val summary = taskScheduleDraftSummary(
            targetDate = null,
            preferredDurationMinutes = null,
            preferredStartMinuteOfDay = null,
            recurringConfig = TaskRecurringConfig(enabled = false),
            today = LocalDate.of(2026, 5, 25)
        )

        assertEquals(
            "Target day, duration, preferred start, recurrence, and reminders.",
            summary
        )
    }

    @Test
    fun taskScheduleDraftSummaryNamesSavedTimingAndRecurrence() {
        val summary = taskScheduleDraftSummary(
            targetDate = LocalDate.of(2026, 5, 26),
            preferredDurationMinutes = 45,
            preferredStartMinuteOfDay = 9 * 60,
            recurringConfig = TaskRecurringConfig(
                enabled = true,
                cadence = TaskRecurringCadence.WEEKLY,
                interval = 1,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                startsOn = LocalDate.of(2026, 5, 25)
            ),
            today = LocalDate.of(2026, 5, 25)
        )

        assertEquals(
            "Target tomorrow · 45m block · Around 9:00 AM · Repeats Mon + Thu",
            summary
        )
    }

    @Test
    fun taskScheduleDraftSummaryFormatsHourLengthBlocksReadably() {
        val summary = taskScheduleDraftSummary(
            targetDate = LocalDate.of(2026, 5, 25),
            preferredDurationMinutes = 90,
            preferredStartMinuteOfDay = null,
            recurringConfig = TaskRecurringConfig(enabled = false),
            today = LocalDate.of(2026, 5, 25)
        )

        assertEquals(
            "Target today · 1h 30m block",
            summary
        )
    }

    @Test
    fun taskDurationPickerOptionsUseReadableHourCopy() {
        assertEquals(
            listOf("15m", "30m", "45m", "1h", "1h 30m", "2h", "Any length"),
            taskDurationPickerOptions()
        )
        assertEquals("1h 30m", taskDurationPickerLabel(90))
        assertEquals(90, taskDurationPickerMinutes("1h 30m"))
        assertEquals(null, taskDurationPickerMinutes("Any length"))
    }

    @Test
    fun taskDurationPickerMinutesRoundTripsSliderTunedLabels() {
        // Non-preset values written by the estimated-duration slider.
        assertEquals(80, taskDurationPickerMinutes(taskDurationPickerLabel(80)))
        assertEquals(25, taskDurationPickerMinutes("25m"))
        assertEquals(180, taskDurationPickerMinutes("3h"))
        assertEquals(140, taskDurationPickerMinutes("2h 20m"))
        assertEquals(null, taskDurationPickerMinutes("garbage"))
        assertEquals(null, taskDurationPickerMinutes(""))
    }

    @Test
    fun taskPreferredStartPickerOptionsUseExplicitMeridiemCopy() {
        assertEquals(
            listOf("Any time", "Morning 9:00 AM", "Noon 12:00 PM", "Afternoon 1:00 PM", "Evening 6:00 PM", "Custom"),
            taskPreferredStartPickerOptions()
        )
        assertEquals("Morning 9:00 AM", taskPreferredStartPickerLabel(9 * 60))
        assertEquals("Noon 12:00 PM", taskPreferredStartPickerLabel(12 * 60))
        assertEquals(12 * 60, taskPreferredStartPickerMinutes("Noon 12:00 PM"))
        assertEquals("2:00 PM", taskPreferredStartPickerLabel(14 * 60))
        assertEquals(14 * 60, taskPreferredStartPickerMinutes("2:00 PM"))
        assertEquals(13 * 60, taskPreferredStartPickerMinutes("Afternoon 1:00 PM"))
        assertEquals(null, taskPreferredStartPickerMinutes("Any time"))
    }

    @Test
    fun checklistDetailsStayHiddenWhenCollapsedEvenWithSavedSteps() {
        assertFalse(
            shouldShowTaskChecklistDetails(
                checklistExpanded = false,
                checklistCount = 3
            )
        )
        assertTrue(
            shouldShowTaskChecklistDetails(
                checklistExpanded = true,
                checklistCount = 0
            )
        )
    }

    @Test
    fun taskChecklistSummaryNamesStepProgress() {
        assertEquals("No steps yet.", taskChecklistSummary(totalCount = 0, completedCount = 0))
        assertEquals("1 step", taskChecklistSummary(totalCount = 1, completedCount = 0))
        assertEquals("3 steps · 2 complete", taskChecklistSummary(totalCount = 3, completedCount = 2))
    }

    @Test
    fun priorityDetailsStayHiddenWhenCollapsedEvenWithSavedPriority() {
        assertFalse(
            shouldShowTaskPriorityDetails(
                priorityExpanded = false,
                hasPrioritySettings = true
            )
        )
        assertTrue(
            shouldShowTaskPriorityDetails(
                priorityExpanded = true,
                hasPrioritySettings = false
            )
        )
    }

    @Test
    fun taskPrioritySummaryNamesPriorityAndAlarmState() {
        assertEquals("Normal priority", taskPrioritySummary(priority = 0, alarmEnabled = false))
        assertEquals("High priority", taskPrioritySummary(priority = 1, alarmEnabled = false))
        assertEquals("Urgent priority · alarm off", taskPrioritySummary(priority = 2, alarmEnabled = false))
        assertEquals("Urgent priority · exact alarm on", taskPrioritySummary(priority = 2, alarmEnabled = true))
    }

    @Test
    fun taskPrioritySummaryNamesResolvedAlarmTime() {
        // Build dueDate in the JVM default zone so 6:00 PM stays stable across CI/dev timezones.
        val today = LocalDate.of(2026, 5, 25)
        val dueDate = today.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(
            "Urgent priority · alarm today at 6:00 PM",
            taskPrioritySummary(
                priority = 2,
                alarmEnabled = true,
                dueDate = dueDate,
                today = today
            )
        )
    }

    @Test
    fun taskUrgentReminderPickerOptionsUseFullTimeCopy() {
        assertEquals(
            listOf("In 30 min", "In 1 hour", "Today 6:00 PM", "Tonight 9:00 PM", "Custom"),
            taskUrgentReminderPickerOptions()
        )
        assertEquals("Today 6:00 PM", resolveUrgentReminderPreset(18 * 60))
        assertEquals(18 * 60, taskUrgentReminderPickerMinute("Today 6:00 PM"))
        assertEquals(null, taskUrgentReminderPickerMinute("Custom"))
    }

    @Test
    fun taskRecurringReminderTriggerPickerNamesBeforeOccurrence() {
        assertEquals(
            listOf("At time", "Before occurrence"),
            taskRecurringReminderTriggerPickerOptions()
        )
        assertEquals("Before occurrence", taskRecurringReminderTriggerLabel(TaskReminderTrigger.BEFORE_OCCURRENCE))
        assertEquals(TaskReminderTrigger.BEFORE_OCCURRENCE, taskRecurringReminderTriggerFromLabel("Before occurrence"))
    }

    @Test
    fun taskRecurringWeekdayPickerUsesTitleCaseShortLabels() {
        assertEquals("Mon", taskRecurringWeekdayPickerLabel(DayOfWeek.MONDAY))
        assertEquals("Tue", taskRecurringWeekdayPickerLabel(DayOfWeek.TUESDAY))
        assertEquals("Sun", taskRecurringWeekdayPickerLabel(DayOfWeek.SUNDAY))
    }

    @Test
    fun taskRecurringIntervalUnitLabelMatchesIntervalCount() {
        assertEquals("day", taskRecurringIntervalUnitLabel(TaskRecurringCadence.DAILY, 1))
        assertEquals("days", taskRecurringIntervalUnitLabel(TaskRecurringCadence.DAILY, 2))
        assertEquals("week", taskRecurringIntervalUnitLabel(TaskRecurringCadence.WEEKLY, 1))
        assertEquals("weeks", taskRecurringIntervalUnitLabel(TaskRecurringCadence.WEEKLY, 3))
        assertEquals("month", taskRecurringIntervalUnitLabel(TaskRecurringCadence.MONTHLY_DAY_OF_MONTH, 1))
        assertEquals("months", taskRecurringIntervalUnitLabel(TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY, 4))
    }

    @Test
    fun formatTaskDueInstantUsesSentenceCaseNearTermDays() {
        val todayAtSix = LocalDate.now()
            .atTime(18, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val tomorrowAtSix = LocalDate.now()
            .plusDays(1)
            .atTime(18, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()

        assertEquals("today at 6:00 PM", formatTaskDueInstant(todayAtSix))
        assertEquals("tomorrow at 6:00 PM", formatTaskDueInstant(tomorrowAtSix))
    }

    @Test
    fun resolveScheduleDateOptionKeepsQuickPresetLabels() {
        val today = LocalDate.of(2026, 5, 25)

        assertEquals("Any day", resolveScheduleDateOption(null, today))
        assertEquals("Today", resolveScheduleDateOption(today, today))
        assertEquals("Tomorrow", resolveScheduleDateOption(today.plusDays(1), today))
    }

    @Test
    fun resolveScheduleDateOptionReturnsCustomForPickedDate() {
        val today = LocalDate.of(2026, 5, 25)

        assertEquals("Custom", resolveScheduleDateOption(today.plusDays(4), today))
    }

    @Test
    fun normalizeTaskActionDraftMarksBlankLabelInvalid() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.WEBSITE,
            label = "",
            value = "https://example.com",
            isPrimary = false
        )

        assertNull(normalizeTaskActionDraft(draft))
    }

    @Test
    fun normalizeTaskActionDraftBuildsWebsiteAction() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.WEBSITE,
            label = "Docs",
            value = "example.com",
            isPrimary = true
        )

        val action = normalizeTaskActionDraft(draft)

        assertEquals("https://example.com", action?.value)
        assertEquals("Docs", action?.label)
        assertEquals(true, action?.isPrimary)
    }

    @Test
    fun normalizeTaskActionDraftBuildsAppAction() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.APP,
            label = "Open Journal",
            value = "package:com.example.journal",
            isPrimary = true
        )

        val action = normalizeTaskActionDraft(draft)

        assertEquals("com.example.journal", action?.value)
        assertEquals(TaskActionType.APP, action?.type)
    }

    @Test
    fun normalizeTaskActionDraftBuildsComponentAppAction() {
        val draft = TaskActionDraft(
            id = "a1",
            type = TaskActionType.APP,
            label = "Open Journal",
            value = "component:com.example.journal/.MainActivity",
            isPrimary = true
        )

        val action = normalizeTaskActionDraft(draft)

        assertEquals("component:com.example.journal/com.example.journal.MainActivity", action?.value)
        assertEquals(TaskActionType.APP, action?.type)
    }

    @Test
    fun normalizeTaskAttachmentDraftsPromotesFirstImageWhenNoneFeatured() {
        val drafts = listOf(
            TaskAttachmentDraft(
                id = "a1",
                displayName = "brief.pdf",
                mimeType = "application/pdf",
                sizeBytes = 1024,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                sourceUri = "content://docs/brief.pdf"
            ),
            TaskAttachmentDraft(
                id = "a2",
                displayName = "cover.png",
                mimeType = "image/png",
                sizeBytes = 2048,
                kind = TaskAttachmentKind.IMAGE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                sourceUri = "content://docs/cover.png"
            )
        )

        val normalized = normalizeTaskAttachmentDrafts(drafts)

        assertFalse(normalized.first().isFeaturedImage)
        assertTrue(normalized.last().isFeaturedImage)
    }

    @Test
    fun hasInvalidTaskAttachmentDraftsRejectsMissingLinkedSource() {
        val invalid = hasInvalidTaskAttachmentDrafts(
            listOf(
                TaskAttachmentDraft(
                    id = "a1",
                    displayName = "brief.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 1024,
                    kind = TaskAttachmentKind.FILE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    sourceUri = null
                )
            )
        )

        assertTrue(invalid)
    }

    @Test
    fun buildTaskScheduleFromConfigResolvesNextWeeklyOccurrence() {
        val config = TaskRecurringConfig(
            enabled = true,
            cadence = TaskRecurringCadence.WEEKLY,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            startsOn = LocalDate.of(2026, 5, 27),
            reminderDrafts = listOf(
                TaskReminderDraft(
                    id = "r1",
                    trigger = TaskReminderTrigger.BEFORE_OCCURRENCE,
                    offsetMinutesBefore = 30
                )
            )
        )

        val schedule = buildTaskScheduleFromConfig(
            taskId = "task-1",
            existingSchedule = null,
            config = config,
            occurrenceMinuteOfDay = 9 * 60,
            resolver = ResolveNextTaskOccurrenceUseCase(),
            now = Instant.parse("2026-05-25T15:00:00Z")
        )

        assertEquals(LocalDate.of(2026, 5, 28), schedule?.nextOccurrenceDate)
        assertEquals(9 * 60, schedule?.occurrenceMinuteOfDay)
        assertEquals(1, schedule?.reminderRules?.size)
    }

    @Test
    fun recurringSummaryFormatsWeeklyRuleWithTimeAndReminders() {
        val summary = recurringSummary(
            TaskRecurringConfig(
                enabled = true,
                cadence = TaskRecurringCadence.WEEKLY,
                interval = 1,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                startsOn = LocalDate.of(2026, 5, 25),
                reminderDrafts = listOf(
                    TaskReminderDraft(id = "r1", trigger = TaskReminderTrigger.BEFORE_OCCURRENCE, offsetMinutesBefore = 30),
                    TaskReminderDraft(id = "r2", trigger = TaskReminderTrigger.AT_TIME, minuteOfDay = 9 * 60)
                )
            ),
            occurrenceMinuteOfDay = 9 * 60
        )

        assertEquals(
            "Mon + Thu at 9:00 AM, repeats weekly, 2 reminders",
            summary
        )
    }
}
