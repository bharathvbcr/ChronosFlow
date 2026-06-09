package com.chronosflow.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object FocusCompletionNotifier {
    private const val CHANNEL_ID = "chronos_focus_completion"
    private const val NOTIFICATION_ID = 4202

    fun show(
        context: Context,
        blockTitle: String?,
        redactSensitiveTitles: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val title = PrivacyRedaction.focusNotificationTitle(blockTitle, redactSensitiveTitles)
        val text = context.getString(R.string.focus_notification_completed_text)

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_focus_session)
                .setContentTitle(title)
                .setContentText(text)
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

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.focus_completion_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.focus_completion_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
