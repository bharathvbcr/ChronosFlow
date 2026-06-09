package com.chronosflow.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.chronosflow.core.domain.model.AlarmRequestType

const val EXTRA_INITIAL_SECTION = "com.chronosflow.extra.INITIAL_SECTION"
const val EXTRA_DAY_TARGET = "com.chronosflow.extra.DAY_TARGET"
const val EXTRA_FOCUS_BLOCK_ID = "com.chronosflow.extra.FOCUS_BLOCK_ID"
const val EXTRA_TASK_ID = "com.chronosflow.extra.TASK_ID"
const val EXTRA_TASK_TARGET = "com.chronosflow.extra.TASK_TARGET"

const val SECTION_DAY = "day"
const val SECTION_FOCUS = "focus"
const val SECTION_MEDICATION = "medication"
const val SECTION_REVIEW = "review"
const val SECTION_TASKS = "tasks"

const val DAY_TARGET_TODAY = "today"
const val TASK_LAUNCH_TARGET_CONTEXT = "context"

data class NotificationLaunch(
    val section: String,
    val dayTarget: String? = null,
    val focusBlockId: String? = null,
    val taskId: String? = null,
    val target: String? = null
)

internal fun resolveNotificationLaunch(
    requestId: String?,
    receiverClass: Class<*>
): NotificationLaunch {
    if (receiverClass == MedicationAlarmReceiver::class.java) {
        return NotificationLaunch(section = SECTION_MEDICATION)
    }
    if (requestId == null) {
        return NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY)
    }
    if (requestId.endsWith(":review")) {
        return NotificationLaunch(section = SECTION_REVIEW)
    }
    if (requestId.startsWith("task:")) {
        return NotificationLaunch(
            section = SECTION_TASKS,
            taskId = requestId.removePrefix("task:")
        )
    }
    if (requestId.startsWith("daydial:")) {
        val parts = requestId.split(":")
        val blockId = parts.getOrNull(2)?.takeUnless { it == "day" }
        return if (blockId != null) {
            NotificationLaunch(
                section = SECTION_FOCUS,
                focusBlockId = blockId
            )
        } else {
            NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY)
        }
    }
    return NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY)
}

internal fun resolveNotificationLaunch(
    requestId: String?,
    requestType: AlarmRequestType?
): NotificationLaunch {
    return when (requestType) {
        AlarmRequestType.MEDICATION -> NotificationLaunch(section = SECTION_MEDICATION)
        AlarmRequestType.DAILY_REVIEW -> NotificationLaunch(section = SECTION_REVIEW)
        AlarmRequestType.URGENT_TASK -> NotificationLaunch(
            section = SECTION_TASKS,
            taskId = requestId?.takeIf { it.startsWith("task:") }?.removePrefix("task:")
        )
        AlarmRequestType.FOCUS_BLOCK,
        AlarmRequestType.BLOCK_START -> {
            val blockId = requestId
                ?.takeIf { it.startsWith("daydial:") }
                ?.split(":")
                ?.getOrNull(2)
                ?.takeUnless { it == "day" }
            if (blockId != null) {
                NotificationLaunch(section = SECTION_FOCUS, focusBlockId = blockId)
            } else {
                NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_TODAY)
            }
        }
        null -> resolveNotificationLaunch(requestId, AlarmReceiver::class.java)
    }
}

fun buildNotificationContentIntent(
    context: Context,
    requestId: String?,
    receiverClass: Class<*>
): PendingIntent {
    val launch = resolveNotificationLaunch(requestId, receiverClass)
    return buildNotificationContentIntent(context, launch, requestId?.hashCode() ?: 0)
}

fun buildNotificationContentIntent(
    context: Context,
    launch: NotificationLaunch,
    requestCode: Int
): PendingIntent {
    val intent = Intent().apply {
        setClassName(context.packageName, "${context.packageName}.MainActivity")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_INITIAL_SECTION, launch.section)
        launch.dayTarget?.let { putExtra(EXTRA_DAY_TARGET, it) }
        launch.focusBlockId?.let { putExtra(EXTRA_FOCUS_BLOCK_ID, it) }
        launch.taskId?.let { putExtra(EXTRA_TASK_ID, it) }
        launch.target?.let { putExtra(EXTRA_TASK_TARGET, it) }
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun parseNotificationLaunch(intent: Intent?): NotificationLaunch? {
    if (intent == null) return null
    val section = intent.getStringExtra(EXTRA_INITIAL_SECTION) ?: return null
    return NotificationLaunch(
        section = section,
        dayTarget = intent.getStringExtra(EXTRA_DAY_TARGET),
        focusBlockId = intent.getStringExtra(EXTRA_FOCUS_BLOCK_ID),
        taskId = intent.getStringExtra(EXTRA_TASK_ID),
        target = intent.getStringExtra(EXTRA_TASK_TARGET)
    )
}

fun buildFocusNotificationContentIntent(
    context: Context,
    blockId: String? = null,
    requestCode: Int = FOCUS_NOTIFICATION_REQUEST_CODE
): PendingIntent {
    return buildNotificationContentIntent(
        context = context,
        launch = NotificationLaunch(
            section = SECTION_FOCUS,
            focusBlockId = blockId
        ),
        requestCode = requestCode
    )
}

fun consumeNotificationLaunchExtras(intent: Intent): Intent {
    return Intent(intent).apply {
        removeExtra(EXTRA_INITIAL_SECTION)
        removeExtra(EXTRA_DAY_TARGET)
        removeExtra(EXTRA_FOCUS_BLOCK_ID)
        removeExtra(EXTRA_TASK_ID)
        removeExtra(EXTRA_TASK_TARGET)
    }
}

const val FOCUS_NOTIFICATION_REQUEST_CODE = 4201
