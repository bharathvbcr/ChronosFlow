package com.chronosflow.feature.tasks

import com.chronosflow.core.ai.TaskAssistActionDraftPayload
import com.chronosflow.core.ai.TaskAssistSchedulePayload
import com.chronosflow.core.ai.TaskAssistSource
import com.chronosflow.core.ai.TaskAssistSuggestion
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskFormSheetStateTest {

    @Test
    fun `attachment row action labels name target attachment`() {
        val fileDraft = TaskAttachmentDraft(
            id = "file-1",
            displayName = "brief.pdf",
            mimeType = "application/pdf",
            sizeBytes = 2048,
            kind = TaskAttachmentKind.FILE,
            storageMode = TaskAttachmentStorageMode.LINKED,
            sourceUri = "content://docs/brief.pdf"
        )
        val imageDraft = TaskAttachmentDraft(
            id = "image-1",
            displayName = "cover.png",
            mimeType = "image/png",
            sizeBytes = 4096,
            kind = TaskAttachmentKind.IMAGE,
            storageMode = TaskAttachmentStorageMode.LINKED,
            sourceUri = "content://docs/cover.png"
        )

        assertEquals("Open brief.pdf attachment", taskAttachmentOpenActionLabel(fileDraft))
        assertEquals("Remove brief.pdf attachment", taskAttachmentRemoveActionLabel(fileDraft))
        assertEquals("Set cover.png as featured image", taskAttachmentFeaturedActionLabel(imageDraft))
        assertEquals(
            "cover.png is the featured image",
            taskAttachmentFeaturedActionLabel(imageDraft.copy(isFeaturedImage = true))
        )
    }

    @Test
    fun `collapsed action card label names section summary action and state`() {
        assertEquals(
            "Add schedule for Timing and recurrence. Current value: Today at 9:00 AM. Collapsed",
            taskCollapsedActionCardContentDescription(
                title = "Timing and recurrence",
                summary = "Today at 9:00 AM",
                actionLabel = "Add schedule",
                expanded = false
            )
        )

        assertEquals(
            "Hide schedule for Schedule details open. Current value: Today at 9:00 AM. Expanded",
            taskCollapsedActionCardContentDescription(
                title = "Schedule details open",
                summary = "Today at 9:00 AM",
                actionLabel = "Hide schedule",
                expanded = true
            )
        )
    }

    @Test
    fun `context action labels name the target action and attachment`() {
        assertEquals(
            "Make Email Alex the primary task action",
            taskActionPrimaryControlLabel(
                label = "Email Alex",
                typeLabel = "Email",
                isPrimary = false
            )
        )
        assertEquals(
            "Email Alex is the primary task action",
            taskActionPrimaryControlLabel(
                label = "Email Alex",
                typeLabel = "Email",
                isPrimary = true
            )
        )
        assertEquals(
            "Remove task action Email",
            taskActionRemoveControlLabel(label = "", typeLabel = "Email")
        )
    }

    @Test
    fun `checklist remove action label names the affected step`() {
        assertEquals("Remove checklist step Draft outline", taskChecklistRemoveActionLabel("Draft outline", index = 0))
        assertEquals("Remove checklist step 2", taskChecklistRemoveActionLabel("", index = 1))
    }

    @Test
    fun `contextual schedule date options prioritize capture and suggestions`() {
        assertEquals(
            "Tomorrow",
            contextualTaskScheduleDateOptions(
                title = "Call mom tomorrow",
                description = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "Today",
            contextualTaskScheduleDateOptions(
                title = "Call mom",
                description = "",
                suggestions = listOf(taskScheduleSuggestion(targetDate = LocalDate.now()))
            ).first()
        )
    }

    @Test
    fun `contextual preferred start options prioritize capture and suggestions`() {
        assertEquals(
            taskPreferredStartPickerLabel(18 * 60),
            contextualTaskPreferredStartPickerOptions(
                title = "Call mom tonight",
                description = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            taskPreferredStartPickerLabel(12 * 60),
            contextualTaskPreferredStartPickerOptions(
                title = "Call mom at noon",
                description = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            taskPreferredStartPickerLabel(14 * 60),
            contextualTaskPreferredStartPickerOptions(
                title = "Call mom at 2 PM",
                description = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            taskPreferredStartPickerLabel(14 * 60 + 30),
            contextualTaskPreferredStartPickerOptions(
                title = "Review draft",
                description = "Start at 14:30 after lunch",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            taskPreferredStartPickerLabel(13 * 60),
            contextualTaskPreferredStartPickerOptions(
                title = "Review notes",
                description = "",
                suggestions = listOf(taskScheduleSuggestion(preferredStartMinuteOfDay = 13 * 60))
            ).first()
        )
    }

    @Test
    fun `contextual duration options prioritize capture and suggestions`() {
        assertEquals(
            taskDurationPickerLabel(15),
            contextualTaskDurationPickerOptions(
                title = "Call mom for 15 minutes",
                description = "",
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            taskDurationPickerLabel(60),
            contextualTaskDurationPickerOptions(
                title = "Review launch notes",
                description = "",
                suggestions = listOf(taskScheduleSuggestion(preferredDurationMinutes = 60))
            ).first()
        )
    }

    @Test
    fun `contextual duration options parse hour length captures`() {
        assertEquals(
            taskDurationPickerLabel(120),
            contextualTaskDurationPickerOptions(
                title = "Review proposal for 2 hours",
                description = "",
                suggestions = emptyList()
            ).first()
        )
        assertEquals(
            taskDurationPickerLabel(90),
            contextualTaskDurationPickerOptions(
                title = "Draft outline for 1 hour 30 minutes",
                description = "",
                suggestions = emptyList()
            ).first()
        )
        assertEquals(
            taskDurationPickerLabel(30),
            contextualTaskDurationPickerOptions(
                title = "Call mom for half an hour",
                description = "",
                suggestions = emptyList()
            ).first()
        )
    }

    @Test
    fun `contextual priority options prioritize capture and suggestions`() {
        assertEquals(
            "Urgent",
            contextualTaskPriorityOptions(
                title = "Fix critical sync issue",
                description = "",
                selectedPriority = 0,
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            "High",
            contextualTaskPriorityOptions(
                title = "Review notes",
                description = "",
                selectedPriority = 0,
                suggestions = listOf(taskPrioritySuggestion(priority = 1))
            ).first()
        )
    }

    @Test
    fun `contextual checklist step options prioritize suggestions and task type`() {
        assertEquals(
            "Make the call",
            contextualTaskChecklistStepOptions(
                title = "Call mom tomorrow",
                description = "",
                suggestions = listOf(taskChecklistSuggestion("Make the call", "Capture follow-up"))
            ).first()
        )

        assertEquals(
            "Confirm amount and due date",
            contextualTaskChecklistStepOptions(
                title = "Pay utility bill",
                description = "",
                suggestions = emptyList()
            ).first()
        )
    }

    @Test
    fun `contextual task action type options prioritize capture and suggestions`() {
        assertEquals(
            TaskActionType.PHONE.name,
            contextualTaskActionTypeOptions(
                contextText = "Call mom tomorrow",
                selectedTypeName = TaskActionType.WEBSITE.name,
                suggestions = emptyList()
            ).first()
        )

        assertEquals(
            TaskActionType.APP.name,
            contextualTaskActionTypeOptions(
                contextText = "Review journal",
                selectedTypeName = TaskActionType.WEBSITE.name,
                suggestions = listOf(taskActionSuggestion(TaskActionType.APP))
            ).first()
        )
    }

    @Test
    fun `contextual task repeat options prioritize typed recurrence`() {
        assertEquals(
            "One-time",
            taskRepeatOptionLabel(
                contextualTaskRepeatOptions(
                    contextText = "call mom tomorrow",
                    recurringConfig = TaskRecurringConfig()
                ).first()
            )
        )

        assertEquals(
            TaskRecurringCadence.WEEKLY.name,
            contextualTaskRepeatOptions(
                contextText = "call mom every Monday",
                recurringConfig = TaskRecurringConfig()
            ).first()
        )

        assertEquals(
            TaskRecurringCadence.WEEKLY.name,
            contextualTaskRepeatOptions(
                contextText = "send status update every other week",
                recurringConfig = TaskRecurringConfig()
            ).first()
        )

        assertEquals(
            TaskRecurringCadence.DAILY.name,
            contextualTaskRepeatOptions(
                contextText = "water plants every 3 days",
                recurringConfig = TaskRecurringConfig()
            ).first()
        )

        assertEquals(
            TaskRecurringCadence.MONTHLY_DAY_OF_MONTH.name,
            contextualTaskRepeatOptions(
                contextText = "pay insurance every 2 months",
                recurringConfig = TaskRecurringConfig()
            ).first()
        )

        assertEquals(
            TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY.name,
            contextualTaskRecurringCadenceOptions(
                contextText = "review report last Friday",
                selectedCadence = TaskRecurringCadence.DAILY
            ).first()
        )
    }

    @Test
    fun `contextual task recurring interval captures explicit two week cadence`() {
        assertEquals(
            2,
            contextualTaskRecurringInterval(
                contextText = "send status update every other week",
                cadence = TaskRecurringCadence.WEEKLY
            )
        )
        assertEquals(
            2,
            contextualTaskRecurringInterval(
                contextText = "submit payroll every 2 weeks",
                cadence = TaskRecurringCadence.WEEKLY
            )
        )
        assertNull(
            contextualTaskRecurringInterval(
                contextText = "send status update every other week",
                cadence = TaskRecurringCadence.DAILY
            )
        )
    }

    @Test
    fun `contextual task recurring interval captures daily and monthly intervals`() {
        assertEquals(
            2,
            contextualTaskRecurringInterval(
                contextText = "take vitamins every other day",
                cadence = TaskRecurringCadence.DAILY
            )
        )
        assertEquals(
            3,
            contextualTaskRecurringInterval(
                contextText = "water plants every 3 days",
                cadence = TaskRecurringCadence.DAILY
            )
        )
        assertEquals(
            4,
            contextualTaskRecurringInterval(
                contextText = "send newsletter every 4 weeks",
                cadence = TaskRecurringCadence.WEEKLY
            )
        )
        assertEquals(
            2,
            contextualTaskRecurringInterval(
                contextText = "pay insurance every 2 months",
                cadence = TaskRecurringCadence.MONTHLY_DAY_OF_MONTH
            )
        )
        assertEquals(
            3,
            contextualTaskRecurringInterval(
                contextText = "review OKRs every 3 months",
                cadence = TaskRecurringCadence.MONTHLY_ORDINAL_WEEKDAY
            )
        )
    }

    @Test
    fun `task repeat option helpers map one time and cadence options`() {
        assertEquals("One-time", taskRepeatOptionLabel(taskRepeatOptionValue(TaskRecurringConfig())))
        assertEquals(
            TaskRecurringCadence.WEEKLY,
            taskRepeatOptionCadence(TaskRecurringCadence.WEEKLY.name)
        )
    }

    @Test
    fun `contextual task weekly days extract weekday captures`() {
        assertEquals(
            setOf(DayOfWeek.MONDAY),
            contextualTaskWeeklyDays("call mom every Monday")
        )
        assertEquals(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            contextualTaskWeeklyDays("clean patio weekends")
        )
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            contextualTaskWeeklyDays("send report weekdays")
        )
    }

    @Test
    fun `auto assist capture requires meaningful task context`() {
        assertEquals(false, shouldAutoRequestAssistForCapture("task"))
        assertEquals(true, shouldAutoRequestAssistForCapture("Call mom tomorrow"))
        assertEquals(true, shouldAutoRequestAssistForCapture("urgent bill"))
    }

    @Test
    fun `applying a title suggestion supersedes the sibling title alternatives only`() {
        val titleA = taskTitleSuggestion(id = "title:a", title = "Call back Alex")
        val titleB = taskTitleSuggestion(id = "title:b", title = "Phone Alex")
        val checklist = taskChecklistSuggestion("Prep notes")
        val suggestions = listOf(titleA, titleB, checklist)

        assertEquals(
            setOf("title:a", "title:b"),
            supersededTaskAssistSuggestionIds(titleA, suggestions)
        )
    }

    @Test
    fun `applying schedule or additive suggestions keeps their siblings offered`() {
        val scheduleA = taskScheduleSuggestion(preferredDurationMinutes = 30).copy(id = "schedule:a")
        val scheduleB = taskScheduleSuggestion(preferredStartMinuteOfDay = 9 * 60).copy(id = "schedule:b")
        val checklistA = taskChecklistSuggestion("Step one").copy(id = "checklist:a")
        val checklistB = taskChecklistSuggestion("Step two").copy(id = "checklist:b")

        assertEquals(
            setOf("schedule:a"),
            supersededTaskAssistSuggestionIds(scheduleA, listOf(scheduleA, scheduleB))
        )
        assertEquals(
            setOf("checklist:a"),
            supersededTaskAssistSuggestionIds(checklistA, listOf(checklistA, checklistB))
        )
    }

    @Test
    fun `suggested checklist step chips disappear once their step exists`() {
        assertEquals(
            listOf("Make the call", "Capture follow-up"),
            availableTaskChecklistStepOptions(
                options = listOf("Confirm the right contact", "Make the call", "Capture follow-up"),
                existingLabels = listOf("  confirm the right contact ")
            )
        )
        assertEquals(
            listOf("Make the call"),
            availableTaskChecklistStepOptions(
                options = listOf("Make the call"),
                existingLabels = emptyList()
            )
        )
    }

    @Test
    fun `new action label placeholder follows the pending action type`() {
        assertEquals("Email Alex", taskActionLabelPlaceholder(TaskActionType.EMAIL))
        assertEquals("Call Alex", taskActionLabelPlaceholder(TaskActionType.PHONE))
        assertEquals("Client website", taskActionLabelPlaceholder(TaskActionType.WEBSITE))
    }

    @Test
    fun `pending action type default follows the task context`() {
        assertEquals(
            TaskActionType.EMAIL.name,
            contextualTaskDefaultActionTypeName("Email follow-up", emptyList())
        )
        assertEquals(
            TaskActionType.PHONE.name,
            contextualTaskDefaultActionTypeName("Call back Alex", emptyList())
        )
        assertEquals(
            TaskActionType.MAP.name,
            contextualTaskDefaultActionTypeName("Drop off the package at the venue", emptyList())
        )
        assertEquals(
            TaskActionType.WEBSITE.name,
            contextualTaskDefaultActionTypeName("Water the plants", emptyList())
        )
        assertEquals(
            TaskActionType.MAP.name,
            contextualTaskDefaultActionTypeName(
                "anything",
                listOf(taskActionSuggestion(TaskActionType.MAP))
            )
        )
    }

    @Test
    fun `auto apply picks the first suggestion per kind only for untouched fields`() {
        val firstTitle = taskTitleSuggestion(id = "title:a", title = "Call back Alex")
        val secondTitle = taskTitleSuggestion(id = "title:b", title = "Phone Alex")
        val schedule = taskScheduleSuggestion(preferredDurationMinutes = 30).copy(id = "schedule:a")
        val priority = taskPrioritySuggestion(priority = 2).copy(id = "priority:a")
        val checklist = taskChecklistSuggestion("Prep notes").copy(id = "checklist:a")
        val action = taskActionSuggestion(TaskActionType.PHONE).copy(id = "action:a")

        val ids = autoApplicableTaskAssistSuggestionIds(
            suggestions = listOf(firstTitle, secondTitle, schedule, priority, checklist, action),
            titleBlank = true,
            priorityUnset = true,
            scheduleUnset = false,
            checklistEmpty = true
        )

        assertEquals(setOf("title:a", "priority:a", "checklist:a"), ids)
    }

    @Test
    fun `suggestions already satisfied by the form are reported redundant`() {
        val matchingTitle = taskTitleSuggestion(id = "title:match", title = "Call back Alex")
        val matchingPriority = taskPrioritySuggestion(priority = 2).copy(id = "priority:match")
        val matchingSchedule = taskScheduleSuggestion(preferredDurationMinutes = 30).copy(id = "schedule:match")
        val matchingChecklist = taskChecklistSuggestion("Prep notes").copy(id = "checklist:match")
        val freshTitle = taskTitleSuggestion(id = "title:new", title = "Phone Alex")

        val redundant = redundantTaskAssistSuggestionIds(
            suggestions = listOf(matchingTitle, matchingPriority, matchingSchedule, matchingChecklist, freshTitle),
            currentTitle = "call back alex",
            currentPriority = 2,
            currentTargetDate = null,
            currentDurationMinutes = 30,
            currentStartMinuteOfDay = null,
            currentChecklistLabels = listOf("Prep Notes"),
            currentActionKeys = emptySet()
        )

        assertEquals(
            setOf("title:match", "priority:match", "schedule:match", "checklist:match"),
            redundant
        )
    }

    @Test
    fun `suggestions that still change the form stay visible`() {
        val newSchedule = taskScheduleSuggestion(preferredDurationMinutes = 45).copy(id = "schedule:new")
        val newChecklist = taskChecklistSuggestion("Prep notes", "Send recap").copy(id = "checklist:new")
        val newAction = taskActionSuggestion(TaskActionType.PHONE).copy(id = "action:new")

        val redundant = redundantTaskAssistSuggestionIds(
            suggestions = listOf(newSchedule, newChecklist, newAction),
            currentTitle = "Call back Alex",
            currentPriority = 0,
            currentTargetDate = null,
            currentDurationMinutes = 30,
            currentStartMinuteOfDay = null,
            currentChecklistLabels = listOf("Prep notes"),
            currentActionKeys = emptySet()
        )

        assertEquals(emptySet<String>(), redundant)
    }

    @Test
    fun `applying a priority suggestion supersedes priority alternatives`() {
        val priorityA = taskPrioritySuggestion(priority = 2).copy(id = "priority:a")
        val priorityB = taskPrioritySuggestion(priority = 1).copy(id = "priority:b")
        val titleC = taskTitleSuggestion(id = "title:c", title = "Pay bill")

        assertEquals(
            setOf("priority:a", "priority:b"),
            supersededTaskAssistSuggestionIds(priorityA, listOf(priorityA, priorityB, titleC))
        )
    }

    private fun taskTitleSuggestion(id: String, title: String): TaskAssistSuggestion.Title =
        TaskAssistSuggestion.Title(
            id = id,
            label = title,
            reason = "Test",
            source = TaskAssistSource.LOCAL,
            title = title
        )

    private fun taskScheduleSuggestion(
        targetDate: LocalDate? = null,
        preferredStartMinuteOfDay: Int? = null,
        preferredDurationMinutes: Int? = null
    ): TaskAssistSuggestion.Schedule = TaskAssistSuggestion.Schedule(
        id = "test:schedule",
        label = "Schedule",
        reason = "Test",
        source = TaskAssistSource.LOCAL,
        payload = TaskAssistSchedulePayload(
            targetDate = targetDate,
            preferredStartMinuteOfDay = preferredStartMinuteOfDay,
            preferredDurationMinutes = preferredDurationMinutes
        )
    )

    private fun taskPrioritySuggestion(priority: Int): TaskAssistSuggestion.Priority = TaskAssistSuggestion.Priority(
        id = "test:priority",
        label = "Priority",
        reason = "Test",
        source = TaskAssistSource.LOCAL,
        priority = priority
    )

    private fun taskChecklistSuggestion(vararg items: String): TaskAssistSuggestion.Checklist =
        TaskAssistSuggestion.Checklist(
            id = "test:checklist",
            label = "Checklist",
            reason = "Test",
            source = TaskAssistSource.LOCAL,
            items = items.toList()
        )

    private fun taskActionSuggestion(type: TaskActionType): TaskAssistSuggestion.ActionDraft =
        TaskAssistSuggestion.ActionDraft(
            id = "test:action",
            label = "Action",
            reason = "Test",
            source = TaskAssistSource.LOCAL,
            payload = TaskAssistActionDraftPayload(
                type = type,
                label = "Action",
                value = ""
            )
        )
}
