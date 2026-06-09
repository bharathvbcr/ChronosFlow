package com.chronosflow.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
