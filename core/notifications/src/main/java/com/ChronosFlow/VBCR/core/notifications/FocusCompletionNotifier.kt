package com.ChronosFlow.VBCR.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Posts the split-session phase-boundary prompt and the session-completion celebration. Both reuse
 * the single live focus notification (id [FocusNotificationManager.FOCUS_NOTIFICATION_ID] on
 * [FocusNotificationManager.FOCUS_CHANNEL_ID]) that [com.ChronosFlow.VBCR.feature.focus.FocusService]
 * runs as its foreground notification — the live timer transitions *in place* into the boundary
 * prompt or the completion state, so the user only ever sees one focus notification on one channel
 * (no separate "nudge" notification, no second channel).
 */
object FocusCompletionNotifier {
    private val NOTIFICATION_ID = FocusNotificationManager.FOCUS_NOTIFICATION_ID
    private val CHANNEL_ID = FocusNotificationManager.FOCUS_CHANNEL_ID
    // Distinct PendingIntent request code for the "continue" tap so it doesn't collide with the
    // running notification's plain content intent (FOCUS_NOTIFICATION_REQUEST_CODE).
    private const val BOUNDARY_ADVANCE_REQUEST_CODE = 4203
    // The folded-in completion auto-clears after a readable beat so it doesn't linger in the shade.
    private const val COMPLETION_TIMEOUT_MS = 30_000L

    /**
     * Announces a split-session phase boundary while the app is backgrounded by transitioning the
     * live focus notification into a "tap to continue" prompt in place. [body] is a ready-made,
     * non-sensitive prompt (e.g. "Time for a 5m break — tap to continue") computed by the caller.
     * Tapping it advances the held session to its next phase (see [buildFocusNotificationContentIntent]).
     */
    fun showPhaseBoundary(
        context: Context,
        blockTitle: String?,
        body: String,
        redactSensitiveTitles: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        FocusNotificationManager.createFocusNotificationChannel(context, manager)

        val title = PrivacyRedaction.focusNotificationTitle(blockTitle, redactSensitiveTitles)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_focus_session)
                .setContentTitle(title)
                .setContentText(body)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setLargeIcon(focusBadge(context))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                // Alert once when the boundary is reached (the live timer's running updates were
                // silent via setOnlyAlertOnce); the user is backgrounded and should be nudged.
                .setOnlyAlertOnce(false)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(
                    buildFocusNotificationContentIntent(
                        context = context,
                        requestCode = BOUNDARY_ADVANCE_REQUEST_CODE,
                        // Tapping the prompt IS "continue": carry the advance flag so the app
                        // resumes into the next phase instead of just opening the Focus tab.
                        focusAdvance = true
                    )
                )
                .build()
        )
    }

    fun show(
        context: Context,
        blockTitle: String?,
        redactSensitiveTitles: Boolean,
        nextStepLine: String? = null
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        FocusNotificationManager.createFocusNotificationChannel(context, manager)

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
                // The running updates were silent (setOnlyAlertOnce on the live notification); alert
                // once now that the session is complete.
                .setOnlyAlertOnce(false)
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
}
