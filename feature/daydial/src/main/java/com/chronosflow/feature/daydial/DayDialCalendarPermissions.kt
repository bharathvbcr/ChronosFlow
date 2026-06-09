package com.chronosflow.feature.daydial

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal data class CalendarPermissionStatus(
    val readGranted: Boolean,
    val writeGranted: Boolean,
    val shouldShowRationale: Boolean,
    val permanentlyDenied: Boolean
) {
    val allGranted: Boolean
        get() = readGranted && writeGranted
}

internal sealed interface PendingCalendarAction {
    data object SyncDeviceEvents : PendingCalendarAction
    data object EnableExports : PendingCalendarAction
    data class ExportBlock(val blockId: String) : PendingCalendarAction
    data class RefreshBlock(val blockId: String) : PendingCalendarAction
    data class RemoveBlock(val blockId: String) : PendingCalendarAction
}

internal enum class CalendarPermissionResolution {
    READY,
    SHOW_RATIONALE,
    REQUEST_PERMISSIONS,
    OPEN_SETTINGS
}

internal fun CalendarPermissionResolution.keepsPendingCalendarAction(): Boolean = when (this) {
    CalendarPermissionResolution.SHOW_RATIONALE,
    CalendarPermissionResolution.REQUEST_PERMISSIONS -> true
    CalendarPermissionResolution.READY,
    CalendarPermissionResolution.OPEN_SETTINGS -> false
}

internal fun calendarPermissionsForSync(): Array<String> =
    arrayOf(Manifest.permission.READ_CALENDAR)

internal fun calendarPermissionsForExport(): Array<String> =
    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

internal fun PendingCalendarAction.requiredCalendarPermissions(): Array<String> = when (this) {
    PendingCalendarAction.SyncDeviceEvents -> calendarPermissionsForSync()
    PendingCalendarAction.EnableExports,
    is PendingCalendarAction.ExportBlock,
    is PendingCalendarAction.RefreshBlock,
    is PendingCalendarAction.RemoveBlock -> calendarPermissionsForExport()
}

internal fun PendingCalendarAction.hasRequiredPermissions(
    readGranted: Boolean,
    writeGranted: Boolean
): Boolean = when (this) {
    PendingCalendarAction.SyncDeviceEvents -> readGranted
    PendingCalendarAction.EnableExports,
    is PendingCalendarAction.ExportBlock,
    is PendingCalendarAction.RefreshBlock,
    is PendingCalendarAction.RemoveBlock -> readGranted && writeGranted
}

internal fun PendingCalendarAction.resolvePermission(
    status: CalendarPermissionStatus,
    rationaleVisible: Boolean
): CalendarPermissionResolution = when {
    hasRequiredPermissions(status.readGranted, status.writeGranted) -> CalendarPermissionResolution.READY
    status.permanentlyDenied -> CalendarPermissionResolution.OPEN_SETTINGS
    status.shouldShowRationale && !rationaleVisible -> CalendarPermissionResolution.SHOW_RATIONALE
    else -> CalendarPermissionResolution.REQUEST_PERMISSIONS
}

internal fun resolveCalendarPermissionStatus(
    readGranted: Boolean,
    writeGranted: Boolean,
    readShouldShowRationale: Boolean,
    writeShouldShowRationale: Boolean,
    requestedBefore: Boolean
): CalendarPermissionStatus {
    val allGranted = readGranted && writeGranted
    val shouldShowRationale = !allGranted && (readShouldShowRationale || writeShouldShowRationale)
    val permanentlyDenied = !allGranted && requestedBefore && !shouldShowRationale
    return CalendarPermissionStatus(
        readGranted = readGranted,
        writeGranted = writeGranted,
        shouldShowRationale = shouldShowRationale,
        permanentlyDenied = permanentlyDenied
    )
}

internal fun nextCalendarPermissionRefreshKey(current: Int): Int =
    if (current == Int.MAX_VALUE) 0 else current + 1

internal fun calendarConnectionStatusMessage(
    permissionStatus: CalendarPermissionStatus,
    connectionState: CalendarConnectionState
): String {
    val defaultMessage = CalendarConnectionState().statusMessage
    if (connectionState.isWorking) {
        return connectionState.statusMessage
    }
    if (permissionStatus.permanentlyDenied) {
        if (permissionStatus.readGranted) {
            return "Calendar imports are connected; export access is off"
        }
        return "Calendar access is off"
    }
    if (!permissionStatus.readGranted && connectionState.lastSuccessMessage != null) {
        return defaultMessage
    }
    if (connectionState.statusMessage != defaultMessage) {
        return connectionState.statusMessage
    }
    return when {
        permissionStatus.allGranted -> "Calendar imports and exports are connected"
        permissionStatus.readGranted -> "Calendar imports are connected; export access needs write permission"
        else -> defaultMessage
    }
}

internal fun calendarPermissionSnackbarMessage(permissionStatus: CalendarPermissionStatus): String = when {
    permissionStatus.permanentlyDenied && permissionStatus.readGranted ->
        "Calendar export access is off. Open settings to enable linked exports."
    permissionStatus.permanentlyDenied ->
        "Calendar access is off. Open settings to enable imports and exports."
    permissionStatus.readGranted && !permissionStatus.writeGranted ->
        "Calendar export permission denied"
    else -> "Calendar permission denied"
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun openCalendarPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
