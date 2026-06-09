package com.chronosflow.feature.tasks

import com.chronosflow.core.domain.model.Task
import com.chronosflow.core.domain.model.ContactMethodKind
import com.chronosflow.core.domain.model.TaskAction
import com.chronosflow.core.domain.model.TaskActionType
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import com.chronosflow.core.domain.model.TaskContactMethod
import com.chronosflow.core.domain.model.TaskContactSnapshot
import com.chronosflow.core.domain.model.TaskRecurrenceRule
import com.chronosflow.core.domain.model.TaskSchedule
import com.chronosflow.core.notifications.TaskContextCommandKind
import com.chronosflow.core.notifications.TaskContextCommandTarget
import com.chronosflow.core.notifications.TaskContextInternalAction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TaskItemStateTest {
    @Test
    fun `task row context label names the task action target`() {
        val task = task(title = "Prepare launch brief")

        assertEquals("Open actions for Prepare launch brief", taskContextActionLabel(task))
    }

    @Test
    fun `task row context label names the direct action when one external command is obvious`() {
        val task = task(
            title = "Prepare launch brief",
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = true
                )
            )
        )

        assertEquals("Launch doc for Prepare launch brief", taskContextActionLabel(task))
    }

    @Test
    fun `task row direct launch is limited to one obvious external command`() {
        val singleActionTask = task(
            title = "Prepare launch brief",
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = true
                )
            )
        )
        val multiActionTask = singleActionTask.copy(
            actions = singleActionTask.actions + TaskAction(
                id = "action-2",
                type = TaskActionType.EMAIL,
                label = "Email Alex",
                value = "alex@example.com",
                isPrimary = false
            )
        )

        assertEquals(true, taskShouldLaunchObviousExternalCommandDirectly(singleActionTask))
        assertEquals(false, taskShouldLaunchObviousExternalCommandDirectly(multiActionTask))
        assertEquals(false, taskShouldLaunchObviousExternalCommandDirectly(task(title = "Prepare launch brief")))
    }

    @Test
    fun `task row cue names the primary contextual action`() {
        val task = task(
            title = "Prepare launch brief",
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = true
                )
            )
        )

        assertEquals("Tap to Launch doc", taskContextCue(task))
        assertEquals(
            "Tap for actions · Edit and schedule",
            taskContextCue(task(title = "Prepare launch brief"), schedule = null)
        )
    }

    @Test
    fun `task row cue keeps action sheet copy when multiple actions exist`() {
        val task = task(
            title = "Prepare launch brief",
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = true
                ),
                TaskAction(
                    id = "action-2",
                    type = TaskActionType.EMAIL,
                    label = "Email Alex",
                    value = "alex@example.com",
                    isPrimary = false
                )
            )
        )

        assertEquals("Tap for actions · Visit link", taskContextCue(task))
    }

    @Test
    fun `task row cue reflects completed and scheduled internal actions`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val completedTask = openTask.copy(isCompleted = true)

        assertEquals(
            "Tap for actions · Edit and add occurrence",
            taskContextCue(openTask, schedule = schedule(openTask.id))
        )
        assertEquals(
            "Tap for actions · Reopen",
            taskContextCue(completedTask, schedule = null)
        )
    }

    @Test
    fun `completion switch label names the concrete completion action`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val completedTask = openTask.copy(isCompleted = true)

        assertEquals("Mark Prepare launch brief complete", taskCompletionToggleLabel(openTask))
        assertEquals("Mark Prepare launch brief incomplete", taskCompletionToggleLabel(completedTask))
    }

    @Test
    fun `schedule action label reflects task schedule state`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val completedTask = openTask.copy(isCompleted = true)
        val schedule = schedule(taskId = openTask.id)

        assertEquals("Schedule", taskScheduleActionLabel(openTask, schedule = null))
        assertEquals("Add occurrence", taskScheduleActionLabel(openTask, schedule = schedule))
        assertEquals("Completed", taskScheduleActionLabel(completedTask, schedule = schedule))
    }

    @Test
    fun `schedule action content label names task and DayDial outcome`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val targetedTask = openTask.copy(targetDate = LocalDate.parse("2026-05-26"))
        val completedTask = openTask.copy(isCompleted = true)
        val schedule = schedule(taskId = openTask.id)

        assertEquals(
            "Schedule Prepare launch brief on today's DayDial",
            taskScheduleActionContentLabel(openTask, schedule = null)
        )
        assertEquals(
            "Schedule Prepare launch brief on target DayDial",
            taskScheduleActionContentLabel(targetedTask, schedule = null)
        )
        assertEquals(
            "Add another DayDial occurrence for Prepare launch brief",
            taskScheduleActionContentLabel(openTask, schedule = schedule)
        )
        assertEquals(
            "Prepare launch brief is completed",
            taskScheduleActionContentLabel(completedTask, schedule = schedule)
        )
    }

    @Test
    fun `dial gap label is shown for any open unscheduled task`() {
        val normalTask = task(title = "Prepare launch brief", isCompleted = false)
        val urgentTask = normalTask.copy(priority = 2)
        val targetedTask = normalTask.copy(targetDate = LocalDate.parse("2026-05-26"))
        val completedTask = normalTask.copy(isCompleted = true)

        assertEquals("Not on today's dial", taskDialGapLabel(normalTask, schedule = null))
        assertEquals("Not on today's dial", taskDialGapLabel(urgentTask, schedule = null))
        assertEquals("Not on target dial", taskDialGapLabel(targetedTask, schedule = null))
        assertEquals(null, taskDialGapLabel(normalTask, schedule = schedule(normalTask.id)))
        assertEquals(null, taskDialGapLabel(completedTask, schedule = null))
    }

    @Test
    fun `context sheet schedule command reflects existing schedule state`() {
        val task = task(title = "Prepare launch brief", isCompleted = false)
        val targetedTask = task.copy(targetDate = LocalDate.parse("2026-05-26"))
        val unscheduledCommand = taskContextCommands(task, schedule = null)
            .first { it.kind == TaskContextCommandKind.SCHEDULE }
        val targetedCommand = taskContextCommands(targetedTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.SCHEDULE }
        val scheduledCommand = taskContextCommands(task, schedule = schedule(task.id))
            .first { it.kind == TaskContextCommandKind.SCHEDULE }

        assertEquals("Schedule on DayDial", unscheduledCommand.label)
        assertEquals("Schedule", unscheduledCommand.shortLabel)
        assertEquals("Schedule on target DayDial", targetedCommand.label)
        assertEquals("Schedule", targetedCommand.shortLabel)
        assertEquals("Add DayDial occurrence", scheduledCommand.label)
        assertEquals("Add occurrence", scheduledCommand.shortLabel)
    }

    @Test
    fun `task schedule summary names recurring next occurrence without raw date copy`() {
        val task = task(title = "Prepare launch brief")
        val schedule = schedule(task.id)

        assertEquals(
            "Daily • Next today",
            taskScheduleSummary(task, schedule, today = LocalDate.parse("2026-05-25"))
        )
    }

    @Test
    fun `task schedule summary names target date without raw date copy`() {
        val today = LocalDate.parse("2026-05-25")
        val task = task(title = "Prepare launch brief").copy(
            targetDate = today,
            preferredDurationMinutes = 30,
            preferredStartMinuteOfDay = 9 * 60
        )

        assertEquals(
            "Target today • 30m block • Around 9:00 AM",
            taskScheduleSummary(task, schedule = null, today = today)
        )
    }

    @Test
    fun `task schedule summary formats hour length blocks readably`() {
        val today = LocalDate.parse("2026-05-25")
        val task = task(title = "Prepare launch brief").copy(
            targetDate = today,
            preferredDurationMinutes = 90
        )

        assertEquals(
            "Target today • 1h 30m block",
            taskScheduleSummary(task, schedule = null, today = today)
        )
    }

    @Test
    fun `context sheet commands include completion control for open and completed tasks`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val completedTask = openTask.copy(isCompleted = true)
        val openCompleteCommand = taskContextCommands(openTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.COMPLETE }
        val completedCommand = taskContextCommands(completedTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.COMPLETE }

        assertEquals("Mark task complete", openCompleteCommand.label)
        assertEquals("Complete", openCompleteCommand.shortLabel)
        assertEquals(
            TaskContextInternalAction.COMPLETE,
            (openCompleteCommand.target as TaskContextCommandTarget.Internal).action
        )
        assertEquals("Mark task open", completedCommand.label)
        assertEquals("Reopen", completedCommand.shortLabel)
    }

    @Test
    fun `context sheet command descriptions name the internal action outcome`() {
        val openTask = task(title = "Prepare launch brief", isCompleted = false)
        val completedTask = openTask.copy(isCompleted = true)
        val scheduleCommand = taskContextCommands(openTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.SCHEDULE }
        val targetedScheduleCommand = taskContextCommands(
            openTask.copy(targetDate = LocalDate.parse("2026-05-26")),
            schedule = null
        ).first { it.kind == TaskContextCommandKind.SCHEDULE }
        val addOccurrenceCommand = taskContextCommands(openTask, schedule = schedule(openTask.id))
            .first { it.kind == TaskContextCommandKind.SCHEDULE }
        val editCommand = taskContextCommands(openTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.EDIT }
        val completeCommand = taskContextCommands(openTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.COMPLETE }
        val reopenCommand = taskContextCommands(completedTask, schedule = null)
            .first { it.kind == TaskContextCommandKind.COMPLETE }

        assertEquals("Protect time on today's DayDial", commandDescription(scheduleCommand))
        assertEquals("Protect time on target DayDial", commandDescription(targetedScheduleCommand))
        assertEquals("Add another protected DayDial slot", commandDescription(addOccurrenceCommand))
        assertEquals("Change title, timing, connections, or files", commandDescription(editCommand))
        assertEquals("Move this task to Done", commandDescription(completeCommand))
        assertEquals("Move this task back to Open", commandDescription(reopenCommand))
    }

    @Test
    fun `context sheet command descriptions name attachment outcomes`() {
        val task = task(title = "Prepare launch brief").copy(
            attachments = listOf(
                TaskAttachment(
                    id = "image-1",
                    displayName = "whiteboard.png",
                    kind = TaskAttachmentKind.IMAGE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    reference = "content://whiteboard",
                    isFeaturedImage = true
                ),
                TaskAttachment(
                    id = "file-1",
                    displayName = "brief.pdf",
                    kind = TaskAttachmentKind.FILE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    reference = "content://brief"
                )
            )
        )
        val commands = taskContextCommands(task, schedule = null)
        val imageCommand = commands.first { it.kind == TaskContextCommandKind.IMAGE }
        val fileCommand = commands.first { it.kind == TaskContextCommandKind.FILE }

        assertEquals("Open attached photo", commandDescription(imageCommand))
        assertEquals("Open attached file", commandDescription(fileCommand))
    }

    @Test
    fun `context sheet command descriptions name contact and action outcomes`() {
        val task = task(title = "Prepare launch brief").copy(
            linkedContact = TaskContactSnapshot(
                displayName = "Alex",
                methods = listOf(
                    TaskContactMethod(
                        id = "phone-1",
                        kind = ContactMethodKind.PHONE,
                        label = "Mobile",
                        value = "555-0100",
                        isPrimary = true
                    ),
                    TaskContactMethod(
                        id = "email-1",
                        kind = ContactMethodKind.EMAIL,
                        label = "Work",
                        value = "alex@example.com",
                        isPrimary = true
                    )
                )
            ),
            actions = listOf(
                TaskAction(
                    id = "action-link",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = true
                ),
                TaskAction(
                    id = "action-map",
                    type = TaskActionType.MAP,
                    label = "Open studio",
                    value = "geo:0,0?q=studio"
                ),
                TaskAction(
                    id = "action-app",
                    type = TaskActionType.APP,
                    label = "Open Journal",
                    value = "com.example.journal"
                )
            )
        )
        val commands = taskContextCommands(task, schedule = null)

        assertEquals("Call linked contact", commandDescription(commands.first { it.kind == TaskContextCommandKind.CALL }))
        assertEquals("Email linked contact", commandDescription(commands.first { it.kind == TaskContextCommandKind.EMAIL }))
        assertEquals("Open linked action", commandDescription(commands.first { it.kind == TaskContextCommandKind.LINK }))
        assertEquals("Open mapped location", commandDescription(commands.first { it.kind == TaskContextCommandKind.MAP }))
        assertEquals("Open app shortcut", commandDescription(commands.first { it.kind == TaskContextCommandKind.APP }))
    }

    @Test
    fun `task connection summary names primary action and featured attachment`() {
        val task = task(title = "Prepare launch brief").copy(
            linkedContact = TaskContactSnapshot(displayName = "Alex"),
            actions = listOf(
                TaskAction(
                    id = "action-1",
                    type = TaskActionType.WEBSITE,
                    label = "Launch doc",
                    value = "https://example.com/brief",
                    isPrimary = false
                ),
                TaskAction(
                    id = "action-2",
                    type = TaskActionType.EMAIL,
                    label = "Email Alex",
                    value = "alex@example.com",
                    isPrimary = true
                )
            ),
            attachments = listOf(
                TaskAttachment(
                    id = "file-1",
                    displayName = "notes.txt",
                    kind = TaskAttachmentKind.FILE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    reference = "content://notes"
                ),
                TaskAttachment(
                    id = "file-2",
                    displayName = "brief.pdf",
                    kind = TaskAttachmentKind.FILE,
                    storageMode = TaskAttachmentStorageMode.LINKED,
                    reference = "content://brief",
                    isFeaturedImage = true
                )
            )
        )

        assertEquals(
            "Contact: Alex • Action: Email Alex + 1 more • File: brief.pdf + 1 more",
            taskConnectionSummary(task)
        )
    }

    @Test
    fun `icon action labels include the task title`() {
        val task = task(title = "Prepare launch brief")

        assertEquals("Duplicate Prepare launch brief", taskDuplicateActionLabel(task))
        assertEquals("Edit Prepare launch brief", taskEditActionLabel(task))
        assertEquals("Delete Prepare launch brief", taskDeleteActionLabel(task))
    }

    @Test
    fun `attention and context dismiss labels name their scope`() {
        val task = task(title = "Prepare launch brief")

        assertEquals("Dismiss Notifications are off alert", taskAttentionDismissActionLabel("Notifications are off"))
        assertEquals("Close actions for Prepare launch brief", taskContextSheetDismissActionLabel(task))
    }

    private fun task(
        title: String,
        isCompleted: Boolean = false,
        actions: List<TaskAction> = emptyList()
    ): Task {
        return Task(
            id = "task-1",
            title = title,
            description = null,
            isCompleted = isCompleted,
            priority = 1,
            dueDate = null,
            createdAt = Instant.parse("2026-05-25T09:00:00Z"),
            updatedAt = Instant.parse("2026-05-25T09:00:00Z"),
            actions = actions
        )
    }

    private fun schedule(taskId: String): TaskSchedule {
        val now = Instant.parse("2026-05-25T09:00:00Z")
        return TaskSchedule(
            id = "schedule-1",
            taskId = taskId,
            recurrenceRule = TaskRecurrenceRule.Daily(
                startsOn = LocalDate.parse("2026-05-25")
            ),
            nextOccurrenceDate = LocalDate.parse("2026-05-25"),
            createdAt = now,
            updatedAt = now
        )
    }

}
