package com.ChronosFlow.VBCR.core.notifications

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ChronosFlow.VBCR.core.domain.model.ContactMethodKind
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskContactMethod
import java.io.File
import java.net.URLEncoder

enum class TaskContextCommandKind {
    CALL,
    EMAIL,
    LINK,
    MAP,
    APP,
    IMAGE,
    FILE,
    SCHEDULE,
    EDIT,
    COMPLETE
}

enum class TaskContextInternalAction {
    SCHEDULE,
    EDIT,
    COMPLETE
}

sealed interface TaskContextCommandTarget {
    data class Action(val action: TaskAction) : TaskContextCommandTarget
    data class ContactMethod(val method: TaskContactMethod) : TaskContextCommandTarget
    data class Attachment(val attachment: TaskAttachment) : TaskContextCommandTarget
    data class Internal(val action: TaskContextInternalAction) : TaskContextCommandTarget
}

data class TaskContextCommand(
    val id: String,
    val kind: TaskContextCommandKind,
    val label: String,
    val shortLabel: String,
    val target: TaskContextCommandTarget
) {
    val isExternal: Boolean
        get() = target !is TaskContextCommandTarget.Internal
}

data class TaskContextCommandSet(
    val taskId: String,
    val commands: List<TaskContextCommand>
) {
    val externalCommands: List<TaskContextCommand>
        get() = commands.filter { it.isExternal }

    val primaryExternalCommand: TaskContextCommand?
        get() = externalCommands.firstOrNull()

    val obviousExternalCommand: TaskContextCommand?
        get() = externalCommands.singleOrNull()
}

object TaskContextCommandResolver {
    fun resolve(
        task: Task,
        includeInternalCommands: Boolean = true,
        includeCompleteCommand: Boolean = true
    ): TaskContextCommandSet {
        val commands = mutableListOf<TaskContextCommand>()

        val contactMethods = task.linkedContact?.methods.orEmpty()
        contactMethods
            .filter { it.kind == ContactMethodKind.PHONE }
            .sortedWith(compareByDescending<TaskContactMethod> { it.isPrimary }.thenBy { it.label.orEmpty() })
            .forEach { method ->
                commands.add(
                    TaskContextCommand(
                        id = "contact-phone:${method.id}",
                        kind = TaskContextCommandKind.CALL,
                        label = task.linkedContact?.displayName?.let { "Call $it" } ?: "Call",
                        shortLabel = "Call",
                        target = TaskContextCommandTarget.ContactMethod(method)
                    )
                )
            }

        task.actions
            .filter { it.isPrimary }
            .sortedBy { taskActionTypePriority(it.type) }
            .forEach { action -> commands.add(action.toTaskContextCommand()) }

        task.attachments
            .filter { it.kind == TaskAttachmentKind.IMAGE && it.isFeaturedImage }
            .forEach { attachment -> commands.add(attachment.toTaskContextCommand(featured = true)) }

        task.attachments
            .firstOrNull { featured ->
                commands.none { command ->
                    (command.target as? TaskContextCommandTarget.Attachment)?.attachment?.id == featured.id
                }
            }
            ?.let { attachment -> commands.add(attachment.toTaskContextCommand(featured = false)) }

        contactMethods
            .filter { it.kind == ContactMethodKind.EMAIL }
            .sortedWith(compareByDescending<TaskContactMethod> { it.isPrimary }.thenBy { it.label.orEmpty() })
            .forEach { method ->
                commands.add(
                    TaskContextCommand(
                        id = "contact-email:${method.id}",
                        kind = TaskContextCommandKind.EMAIL,
                        label = task.linkedContact?.displayName?.let { "Email $it" } ?: "Email",
                        shortLabel = "Email",
                        target = TaskContextCommandTarget.ContactMethod(method)
                    )
                )
            }

        task.actions
            .filterNot { it.isPrimary }
            .sortedBy { taskActionTypePriority(it.type) }
            .forEach { action -> commands.add(action.toTaskContextCommand()) }

        task.attachments
            .filter { attachment ->
                commands.none { command ->
                    (command.target as? TaskContextCommandTarget.Attachment)?.attachment?.id == attachment.id
                }
            }
            .forEach { attachment -> commands.add(attachment.toTaskContextCommand(featured = false)) }

        if (includeInternalCommands) {
            if (!task.isCompleted) {
                commands.add(
                    TaskContextCommand(
                        id = "schedule:${task.id}",
                        kind = TaskContextCommandKind.SCHEDULE,
                        label = "Schedule on DayDial",
                        shortLabel = "Schedule",
                        target = TaskContextCommandTarget.Internal(TaskContextInternalAction.SCHEDULE)
                    )
                )
            }
            commands.add(
                TaskContextCommand(
                    id = "edit:${task.id}",
                    kind = TaskContextCommandKind.EDIT,
                    label = "Edit task",
                    shortLabel = "Edit",
                    target = TaskContextCommandTarget.Internal(TaskContextInternalAction.EDIT)
                )
            )
            if (includeCompleteCommand) {
                commands.add(
                    TaskContextCommand(
                        id = "complete:${task.id}",
                        kind = TaskContextCommandKind.COMPLETE,
                        label = if (task.isCompleted) "Mark task open" else "Mark task complete",
                        shortLabel = if (task.isCompleted) "Reopen" else "Complete",
                        target = TaskContextCommandTarget.Internal(TaskContextInternalAction.COMPLETE)
                    )
                )
            }
        }

        return TaskContextCommandSet(
            taskId = task.id,
            commands = commands.distinctBy(::taskContextCommandKey)
        )
    }

    private fun taskContextCommandKey(command: TaskContextCommand): String {
        return when (val target = command.target) {
            is TaskContextCommandTarget.Action -> "action:${target.action.type}:${target.action.value}"
            is TaskContextCommandTarget.Attachment -> "attachment:${target.attachment.id}"
            is TaskContextCommandTarget.ContactMethod -> "contact:${target.method.kind}:${target.method.normalizedValue ?: target.method.value}"
            is TaskContextCommandTarget.Internal -> "internal:${target.action}"
        }
    }
}

fun buildTaskContextCommandIntent(context: Context, command: TaskContextCommand): Intent? {
    return when (val target = command.target) {
        is TaskContextCommandTarget.Action -> buildTaskActionIntent(target.action)
        is TaskContextCommandTarget.ContactMethod -> buildTaskContactMethodIntent(target.method)
        is TaskContextCommandTarget.Attachment -> buildTaskAttachmentIntent(context, target.attachment)
        is TaskContextCommandTarget.Internal -> null
    }
}

fun canLaunchTaskContextCommand(context: Context, command: TaskContextCommand): Boolean {
    val action = (command.target as? TaskContextCommandTarget.Action)?.action
    if (action?.type == TaskActionType.APP) {
        return canLaunchTaskAction(context, action)
    }
    val intent = buildTaskContextCommandIntent(context, command) ?: return false
    return intent.resolveActivity(context.packageManager) != null
}

fun launchTaskContextCommand(context: Context, command: TaskContextCommand): Boolean {
    val action = (command.target as? TaskContextCommandTarget.Action)?.action
    if (action?.type == TaskActionType.APP) {
        return launchTaskAction(context, action)
    }
    val intent = buildTaskContextCommandIntent(context, command) ?: return false
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching {
        context.startActivity(intent)
        true
    }.recoverCatching {
        if (it is ActivityNotFoundException) false else throw it
    }.getOrDefault(false)
}

fun safeExternalTaskContextCommands(
    context: Context,
    task: Task,
    limit: Int = Int.MAX_VALUE
): List<TaskContextCommand> {
    return TaskContextCommandResolver.resolve(
        task = task,
        includeInternalCommands = false,
        includeCompleteCommand = false
    ).externalCommands
        .filter { canLaunchTaskContextCommand(context, it) }
        .take(limit)
}

fun buildTaskContextCommandPendingIntent(
    context: Context,
    command: TaskContextCommand,
    requestCode: Int
): PendingIntent? {
    val intent = buildTaskContextCommandIntent(context, command) ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal fun resolveTaskAttachmentUri(context: Context, attachment: TaskAttachment): Uri? {
    return when (attachment.storageMode) {
        TaskAttachmentStorageMode.LINKED -> attachment.reference.takeIf { it.isNotBlank() }?.let(Uri::parse)
        TaskAttachmentStorageMode.IMPORTED -> {
            val file = File(context.filesDir, attachment.reference)
            if (!file.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}

private fun TaskAction.toTaskContextCommand(): TaskContextCommand {
    return TaskContextCommand(
        id = "action:$id",
        kind = when (type) {
            TaskActionType.PHONE -> TaskContextCommandKind.CALL
            TaskActionType.EMAIL -> TaskContextCommandKind.EMAIL
            TaskActionType.MAP -> TaskContextCommandKind.MAP
            TaskActionType.APP -> TaskContextCommandKind.APP
            TaskActionType.WEBSITE,
            TaskActionType.DOCUMENT,
            TaskActionType.CUSTOM_DEEP_LINK -> TaskContextCommandKind.LINK
        },
        label = label,
        shortLabel = when (type) {
            TaskActionType.PHONE -> "Call"
            TaskActionType.EMAIL -> "Email"
            TaskActionType.MAP -> "Map"
            TaskActionType.APP -> "Open app"
            TaskActionType.WEBSITE -> "Visit link"
            TaskActionType.DOCUMENT -> "Open file"
            TaskActionType.CUSTOM_DEEP_LINK -> "Open"
        },
        target = TaskContextCommandTarget.Action(this)
    )
}

private fun TaskAttachment.toTaskContextCommand(featured: Boolean): TaskContextCommand {
    val isImage = kind == TaskAttachmentKind.IMAGE
    return TaskContextCommand(
        id = "attachment:$id",
        kind = if (isImage) TaskContextCommandKind.IMAGE else TaskContextCommandKind.FILE,
        label = if (featured && isImage) "Open featured photo" else "Open $displayName",
        shortLabel = if (isImage) "Open photo" else "Open file",
        target = TaskContextCommandTarget.Attachment(this)
    )
}

private fun buildTaskContactMethodIntent(method: TaskContactMethod): Intent {
    val value = method.normalizedValue ?: method.value
    return when (method.kind) {
        ContactMethodKind.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${urlEncode(value)}"))
        ContactMethodKind.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${urlEncode(value)}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun buildTaskAttachmentIntent(context: Context, attachment: TaskAttachment): Intent? {
    val uri = resolveTaskAttachmentUri(context, attachment) ?: return null
    return Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun taskActionTypePriority(type: TaskActionType): Int = when (type) {
    TaskActionType.PHONE -> 0
    TaskActionType.EMAIL -> 1
    TaskActionType.WEBSITE -> 2
    TaskActionType.DOCUMENT -> 3
    TaskActionType.MAP -> 4
    TaskActionType.APP -> 5
    TaskActionType.CUSTOM_DEEP_LINK -> 6
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.toString())
