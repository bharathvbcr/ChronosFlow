package com.chronosflow.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.chronosflow.MainActivity
import com.chronosflow.core.notifications.EXTRA_INITIAL_SECTION

/**
 * Glance action that opens the app on a specific section, reusing the notification deep-link
 * path (EXTRA_INITIAL_SECTION → ChronosRoute.routeForNotificationLaunch), so each widget lands
 * on its own screen instead of the default day view.
 */
internal fun openSectionAction(context: Context, section: String): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INITIAL_SECTION, section)
        }
    )
