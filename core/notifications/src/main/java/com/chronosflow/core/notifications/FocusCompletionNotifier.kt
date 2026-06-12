package com.chronosflow.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object FocusCompletionNotifier {
    private const val CHANNEL_ID = "chronos_focus_completion"
    private const val NOTIFICATION_ID = 4202
    private const val BOUNDARY_NOTIFICATION_ID = 4203

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
                        requestCode = BOUNDARY_NOTIFICATION_ID
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
                .setColor(ContextCompat.getColor(context, R.color.chronosflow_brand_accent))
                .setContentTitle(title)
                .setContentText(text)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setLargeIcon(focusBadge(context))
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
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
