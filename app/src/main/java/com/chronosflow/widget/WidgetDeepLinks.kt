package com.chronosflow.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.chronosflow.MainActivity
import com.chronosflow.core.notifications.EXTRA_INITIAL_SECTION

/**
 * Glance action that opens the app on a specific section, reusing the notification deep-link
 * path (EXTRA_INITIAL_SECTION → ChronosRoute.routeForNotificationLaunch), so each widget lands
 * on its own screen instead of the default day view.
 *
 * The per-section `data` URI keeps each widget's PendingIntent distinct: `Intent.filterEquals`
 * ignores extras, so without it every widget's open action would collapse into one cached
 * PendingIntent and land on whichever section was registered last.
 */
internal fun openSectionAction(context: Context, section: String): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("chronosflow://open/$section")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INITIAL_SECTION, section)
        }
    )
