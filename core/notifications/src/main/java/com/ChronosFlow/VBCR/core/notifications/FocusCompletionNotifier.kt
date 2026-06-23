package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object FocusCompletionNotifier {
    private const val CHANNEL_ID = "chronos_focus_completion"
    // Completion folds into the live focus notification's slot (id 4201): the bar you were watching
    // fills to 100% and reads "complete" in place, rather than a separate notification popping up.
    // FocusService detaches (not removes) the foreground notification on completion so this survives.
    private const val NOTIFICATION_ID = FocusNotificationManager.FOCUS_NOTIFICATION_ID
    private const val BOUNDARY_NOTIFICATION_ID = 4203
    // The folded-in completion auto-clears after a readable beat so it doesn't linger in the shade.
    private const val COMPLETION_TIMEOUT_MS = 30_000L

    /**
     * Announces a split-session phase boundary while the app is backgrounded.
     * [body] is a ready-made, non-sensitive prompt (e.g. "Time for a 5m break —
     * tap to continue") computed by the caller.
     */
    fun showPhaseBoundary(
        context: Context,
        blockTitle: String?,
        body: String,
        redactSensitiveTitles: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val title = PrivacyRedaction.focusNotificationTitle(blockTitle, redactSensitiveTitles)
        manager.notify(
            BOUNDARY_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_focus_session)
                .setContentTitle(title)
                .setContentText(body)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setLargeIcon(focusBadge(context))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(
                    buildFocusNotificationContentIntent(
                        context = context,
                        requestCode = BOUNDARY_NOTIFICATION_ID,
                        // Tapping the nudge IS "continue": carry the advance flag so the app
                        // resumes into the next phase instead of just opening the Focus tab.
                        focusAdvance = true
                    )
                )
                .build()
        )
    }

    /** Dismisses any pending phase-boundary notification (e.g. once the next phase begins). */
    fun cancelPhaseBoundary(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(BOUNDARY_NOTIFICATION_ID)
    }

    fun show(
        context: Context,
        blockTitle: String?,
        redactSensitiveTitles: Boolean,
        nextStepLine: String? = null
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val title = PrivacyRedaction.focusNotificationTitle(blockTitle, redactSensitiveTitles)
        val text = context.getString(R.string.focus_notification_completed_text)
        val completedBigText = if (redactSensitiveTitles) {
            text
        } else {
            context.getString(R.string.focus_notification_completed_big_text)
        }
        // Next-step lines carry block titles, so they are dropped under redaction.
        val bigText = listOfNotNull(
            completedBigText,
            nextStepLine.takeUnless { redactSensitiveTitles }
        ).joinToString("\n\n")

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chronosflow_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setLargeIcon(focusBadge(context))
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                // The live bar reaching 100% — the visual "the session you were watching is done".
                .setProgress(1, 1, false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(COMPLETION_TIMEOUT_MS)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(
                    buildFocusNotificationContentIntent(
                        context = context,
                        requestCode = NOTIFICATION_ID
                    )
                )
                .build()
        )
    }

    private fun focusBadge(context: Context) = NotificationIcons.categoryBadge(
        context = context,
        iconRes = R.drawable.ic_focus_session,
        backgroundColor = ContextCompat.getColor(context, R.color.notification_accent)
    )

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationChannelGroups.ensureCreated(manager)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.focus_completion_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.focus_completion_channel_description)
            group = NotificationChannelGroups.FOCUS
        }
        manager.createNotificationChannel(channel)
    }
}
