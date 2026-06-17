package com.chronosflow.core.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object NotificationPermissions {
    const val POST_PROMOTED_NOTIFICATIONS = "android.permission.POST_PROMOTED_NOTIFICATIONS"

    fun requiredPermissions(): Array<String> = requiredPermissionsForSdk(Build.VERSION.SDK_INT)

    internal fun requiredPermissionsForSdk(sdkInt: Int): Array<String> {
        return when {
            sdkInt >= 37 -> arrayOf(Manifest.permission.POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS)
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            else -> emptyArray()
        }
    }

    fun hasStandardPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPromotedPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                POST_PROMOTED_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun areFocusNotificationsReady(context: Context): Boolean {
        return hasStandardPermission(context) && hasPromotedPermission(context)
    }

    /**
     * Whether to proactively nudge the user toward granting promoted ("Live Update") notifications.
     * True only in the gap that actually keeps the live notification off the status-bar chip /
     * always-on display: the platform supports promotion (API 37+), the standard post permission is
     * already granted (so the notification posts at all), yet the promoted permission is missing.
     * Below API 37 there is nothing to grant, and without the standard permission the standard
     * request comes first — so neither nudges.
     */
    internal fun shouldNudgeForPromotion(
        sdkInt: Int,
        hasStandardPermission: Boolean,
        hasPromotedPermission: Boolean
    ): Boolean = sdkInt >= 37 && hasStandardPermission && !hasPromotedPermission

    /** Context-aware [shouldNudgeForPromotion] using the live permission state of [context]. */
    fun shouldNudgeForPromotion(context: Context): Boolean = shouldNudgeForPromotion(
        sdkInt = Build.VERSION.SDK_INT,
        hasStandardPermission = hasStandardPermission(context),
        hasPromotedPermission = hasPromotedPermission(context)
    )

    /**
     * Intent to the system screen where the user grants promoted ("Live Update") notifications, so a
     * nudge can route there directly. Null below API 37, where that screen does not exist and the
     * caller should hide the nudge entirely.
     */
    fun promotionSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 37) return null
        return Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun missingPermissions(context: Context): Array<String> {
        return requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun resultMessage(context: Context, results: Map<String, Boolean>): String {
        val notificationsGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true ||
            hasStandardPermission(context)
        val promotedGranted = results[NotificationPermissions.POST_PROMOTED_NOTIFICATIONS] == true ||
            hasPromotedPermission(context)
        return when {
            notificationsGranted && promotedGranted -> context.getString(R.string.notification_permission_enabled)
            notificationsGranted -> context.getString(R.string.notification_permission_promoted_fallback)
            else -> context.getString(R.string.notification_permission_denied)
        }
    }
}
