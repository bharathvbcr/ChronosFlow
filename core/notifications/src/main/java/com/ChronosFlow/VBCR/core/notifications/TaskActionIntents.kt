package com.ChronosFlow.VBCR.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ChronosFlow.VBCR.core.domain.model.TaskAction
import com.ChronosFlow.VBCR.core.domain.model.TaskActionType
import java.net.URLEncoder

fun buildTaskActionIntent(action: TaskAction): Intent {
    if (action.type == TaskActionType.APP) {
        return buildAppLaunchIntent(action.value) ?: Intent(Intent.ACTION_VIEW, Uri.parse(action.value))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val intent = Intent(taskActionIntentAction(action.type), Uri.parse(taskActionIntentData(action)))
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun canLaunchTaskAction(context: Context, action: TaskAction): Boolean {
    if (action.type == TaskActionType.APP) {
        return canLaunchAppValue(context, action.value)
    }
    return buildTaskActionIntent(action).resolveActivity(context.packageManager) != null
}

fun launchTaskAction(context: Context, action: TaskAction): Boolean {
    return if (action.type == TaskActionType.APP) {
        launchAppValue(context, action.value)
    } else {
        if (!canLaunchTaskAction(context, action)) return false
        context.startActivity(buildTaskActionIntent(action))
        true
    }
}

fun buildTaskActionPendingIntent(
    context: Context,
    action: TaskAction,
    requestCode: Int
): PendingIntent {
    return PendingIntent.getActivity(
        context,
        requestCode,
        buildTaskActionIntent(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun primaryTaskActions(actions: List<TaskAction>, limit: Int = 3): List<TaskAction> {
    return actions
        .sortedWith(
            compareByDescending<TaskAction> { it.isPrimary }
                .thenBy { taskActionTypePriority(it.type) }
        )
        .distinctBy { it.id }
        .take(limit)
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

internal fun taskActionIntentAction(type: TaskActionType): String = when (type) {
    TaskActionType.WEBSITE,
    TaskActionType.DOCUMENT,
    TaskActionType.MAP,
    TaskActionType.CUSTOM_DEEP_LINK -> Intent.ACTION_VIEW
    TaskActionType.APP -> Intent.ACTION_MAIN
    TaskActionType.PHONE -> Intent.ACTION_DIAL
    TaskActionType.EMAIL -> Intent.ACTION_SENDTO
}

internal fun taskActionIntentData(action: TaskAction): String = when (action.type) {
    TaskActionType.WEBSITE,
    TaskActionType.DOCUMENT,
    TaskActionType.MAP,
    TaskActionType.CUSTOM_DEEP_LINK,
    TaskActionType.APP -> action.value
    TaskActionType.PHONE ->
        if (action.value.startsWith("tel:")) action.value else "tel:${urlEncode(action.value)}"
    TaskActionType.EMAIL ->
        if (action.value.startsWith("mailto:", ignoreCase = true)) action.value
        else "mailto:${urlEncode(action.value)}"
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.toString())
