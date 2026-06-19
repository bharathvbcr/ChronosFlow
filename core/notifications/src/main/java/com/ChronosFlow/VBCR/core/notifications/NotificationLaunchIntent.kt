package com.ChronosFlow.VBCR.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType

const val EXTRA_INITIAL_SECTION = "com.ChronosFlow.VBCR.extra.INITIAL_SECTION"
const val EXTRA_DAY_TARGET = "com.ChronosFlow.VBCR.extra.DAY_TARGET"
const val EXTRA_FOCUS_BLOCK_ID = "com.ChronosFlow.VBCR.extra.FOCUS_BLOCK_ID"
const val EXTRA_TASK_ID = "com.ChronosFlow.VBCR.extra.TASK_ID"
const val EXTRA_TASK_TARGET = "com.ChronosFlow.VBCR.extra.TASK_TARGET"
const val EXTRA_TASK_CAPTURE = "com.ChronosFlow.VBCR.extra.TASK_CAPTURE"

const val SECTION_DAY = "day"
const val SECTION_FOCUS = "focus"
const val SECTION_MEDICATION = "medication"
const val SECTION_REVIEW = "review"
const val SECTION_TASKS = "tasks"

const val DAY_TARGET_TODAY = "today"
const val DAY_TARGET_JOURNAL = "journal"
const val DAY_TARGET_SLEEP = "sleep"
const val TASK_LAUNCH_TARGET_CONTEXT = "context"

/**
 * Opens the task capture/add sheet. Mirrors the app module's `ChronosRoute.TARGET_ADD`; kept here
 * so the notifications module (which cannot depend on :app) can route share-sheet captures.
 */
const val TASK_LAUNCH_TARGET_ADD = "add"

/** Upper bound on text pulled from another app's share/process-text intent before it becomes a task capture. */
const val SHARED_CAPTURE_MAX_LENGTH = 2000

/** Upper bound on bytes read from a shared file (50 KB); keeps huge/binary shares from OOM-ing the parser. */
const val SHARED_IMPORT_MAX_CHARS = 50_000

/** Maximum number of task titles accepted from a single bulk import. */
private const val MAX_BULK_IMPORT_TASKS = 200

data class NotificationLaunch(
    val section: String,
    val dayTarget: String? = null,
    val focusBlockId: String? = null,
    val taskId: String? = null,
    val target: String? = null,
    /** Free text to pre-fill an add sheet (e.g. shared from another app). Smart-filled on arrival. */
    val capture: String? = null,
    /** Task titles for a bulk-import review sheet (multi-line share or file share). */
    val bulkCapture: List<String>? = null
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
    // Sleep & journal log nudge — open the sleep log sheet for the day.
    if (requestId.endsWith(":logsleep")) {
        return NotificationLaunch(section = SECTION_DAY, dayTarget = DAY_TARGET_SLEEP)
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
        // Evening companion: the daily-review tap lands on the journal sheet.
        AlarmRequestType.DAILY_REVIEW -> NotificationLaunch(
            section = SECTION_DAY,
            dayTarget = DAY_TARGET_JOURNAL
        )
        // The sleep & journal log nudge lands on the sleep log sheet.
        AlarmRequestType.LOG_REMINDER -> NotificationLaunch(
            section = SECTION_DAY,
            dayTarget = DAY_TARGET_SLEEP
        )
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
        launch.capture?.let { putExtra(EXTRA_TASK_CAPTURE, it) }
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
        target = intent.getStringExtra(EXTRA_TASK_TARGET),
        capture = intent.getStringExtra(EXTRA_TASK_CAPTURE)
    )
}

/**
 * Maps an Android **Share** (`ACTION_SEND`) or text-selection **Process text**
 * (`ACTION_PROCESS_TEXT`) intent originating from another app into a launch that opens the task
 * capture sheet pre-filled with the shared text. Returns null for any other intent so callers can
 * fall back to [parseNotificationLaunch].
 *
 * This is how a user adds a task created elsewhere — e.g. sharing a note from Keep, a link from
 * Chrome, or selected text from any app straight into ChronosFlow.
 */
fun parseSharedTextLaunch(intent: Intent?): NotificationLaunch? {
    if (intent == null) return null
    val shared = when (intent.action) {
        Intent.ACTION_SEND -> {
            if (intent.type?.startsWith("text/") != true) return null
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            sharedTaskCapture(text, subject)
        }
        Intent.ACTION_PROCESS_TEXT -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            val readonly = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)?.toString()
            sharedTaskCapture(text, readonly)
        }
        else -> null
    } ?: return null
    return NotificationLaunch(
        section = SECTION_TASKS,
        target = TASK_LAUNCH_TARGET_ADD,
        capture = shared
    )
}

/**
 * Splits a text blob (from a shared file or multi-line clipboard) into individual task title
 * candidates. Handles two formats automatically:
 * - **ICS/VCALENDAR**: extracts SUMMARY lines from VTODO blocks only (ignores VEVENTs).
 * - **Plain text**: one candidate per non-blank line; strips common markdown/list bullet prefixes
 *   ("- ", "* ", "+ ") so a task list copied from a notes app arrives clean.
 * Results are trimmed and capped at [MAX_BULK_IMPORT_TASKS].
 */
fun splitSharedTaskLines(text: String): List<String> {
    if (text.contains("BEGIN:VCALENDAR")) return extractVtodoSummaries(text)
    return text.lines()
        .map { line ->
            line.trim()
                .removePrefix("- ").removePrefix("* ").removePrefix("+ ")
                .trim()
        }
        .filter { it.isNotBlank() }
        .take(MAX_BULK_IMPORT_TASKS)
}

/** Extracts SUMMARY values from VTODO blocks in an ICS text. VEVENTs are ignored. */
private fun extractVtodoSummaries(icsText: String): List<String> {
    val summaries = mutableListOf<String>()
    var inVtodo = false
    for (line in icsText.lines()) {
        val trimmed = line.trim()
        when {
            trimmed == "BEGIN:VTODO" -> inVtodo = true
            trimmed == "END:VTODO" -> inVtodo = false
            inVtodo && trimmed.startsWith("SUMMARY") -> {
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx >= 0) summaries.add(trimmed.substring(colonIdx + 1).trim())
            }
        }
    }
    return summaries.take(MAX_BULK_IMPORT_TASKS)
}

/**
 * Converts task title candidates (from [splitSharedTaskLines]) into a launch:
 * - a single candidate opens the add sheet pre-filled via [NotificationLaunch.capture];
 * - multiple candidates open the bulk-import review sheet via [NotificationLaunch.bulkCapture].
 * Returns null when the list is empty.
 */
fun sharedTaskLaunchFromLines(lines: List<String>): NotificationLaunch? {
    if (lines.isEmpty()) return null
    return if (lines.size == 1) {
        NotificationLaunch(
            section = SECTION_TASKS,
            target = TASK_LAUNCH_TARGET_ADD,
            capture = lines.first()
        )
    } else {
        NotificationLaunch(
            section = SECTION_TASKS,
            target = TASK_LAUNCH_TARGET_ADD,
            bulkCapture = lines
        )
    }
}

/**
 * Normalizes the text carried by a share/process-text intent into a task capture string: prefers
 * the primary [text], falls back to [fallback] (the subject / read-only copy), trims, drops blanks,
 * and caps the length. Pure so it is unit-testable without an Android [Intent].
 */
internal fun sharedTaskCapture(text: String?, fallback: String?): String? {
    val chosen = text?.takeIf { it.isNotBlank() } ?: fallback
    return chosen?.trim()?.takeIf { it.isNotEmpty() }?.take(SHARED_CAPTURE_MAX_LENGTH)
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
        removeExtra(EXTRA_TASK_CAPTURE)
        // Neutralize a share / process-text launch so a later recomposition can't re-open the
        // capture sheet from the same intent (the launch is keyed to the intent, not a generation).
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_PROCESS_TEXT) {
            action = Intent.ACTION_MAIN
            removeExtra(Intent.EXTRA_TEXT)
            removeExtra(Intent.EXTRA_SUBJECT)
            removeExtra(Intent.EXTRA_PROCESS_TEXT)
            removeExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)
        }
    }
}

const val FOCUS_NOTIFICATION_REQUEST_CODE = 4201
