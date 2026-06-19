package com.ChronosFlow.VBCR.feature.daydial

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDialCalendarPermissionsTest {

    @Test
    fun `resolveCalendarPermissionStatus marks granted when both permissions are available`() {
        val status = resolveCalendarPermissionStatus(
            readGranted = true,
            writeGranted = true,
            readShouldShowRationale = false,
            writeShouldShowRationale = false,
            requestedBefore = true
        )

        assertTrue(status.allGranted)
        assertFalse(status.shouldShowRationale)
        assertFalse(status.permanentlyDenied)
    }

    @Test
    fun `resolveCalendarPermissionStatus prefers rationale over permanent denial`() {
        val status = resolveCalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            readShouldShowRationale = true,
            writeShouldShowRationale = false,
            requestedBefore = true
        )

        assertFalse(status.allGranted)
        assertTrue(status.shouldShowRationale)
        assertFalse(status.permanentlyDenied)
    }

    @Test
    fun `resolveCalendarPermissionStatus detects permanent denial after prior request without rationale`() {
        val status = resolveCalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            readShouldShowRationale = false,
            writeShouldShowRationale = false,
            requestedBefore = true
        )

        assertFalse(status.allGranted)
        assertFalse(status.shouldShowRationale)
        assertTrue(status.permanentlyDenied)
    }

    @Test
    fun `calendarPermissionsForSync only requests read access`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_CALENDAR),
            calendarPermissionsForSync()
        )
    }

    @Test
    fun `sync device events only requires calendar read access`() {
        assertTrue(PendingCalendarAction.SyncDeviceEvents.hasRequiredPermissions(readGranted = true, writeGranted = false))
    }

    @Test
    fun `sync action is ready when read permission is granted and write is missing`() {
        val status = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        )

        assertEquals(
            CalendarPermissionResolution.READY,
            PendingCalendarAction.SyncDeviceEvents.resolvePermission(status, rationaleVisible = false)
        )
    }

    @Test
    fun `pending calendar action is kept only while user decision is still active`() {
        assertTrue(CalendarPermissionResolution.SHOW_RATIONALE.keepsPendingCalendarAction())
        assertTrue(CalendarPermissionResolution.REQUEST_PERMISSIONS.keepsPendingCalendarAction())
        assertFalse(CalendarPermissionResolution.READY.keepsPendingCalendarAction())
        assertFalse(CalendarPermissionResolution.OPEN_SETTINGS.keepsPendingCalendarAction())
    }

    @Test
    fun `manual sync action waits until calendar read permission is connected`() {
        val disconnected = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        )
        val importOnly = disconnected.copy(readGranted = true)

        assertEquals(
            CalendarPermissionResolution.REQUEST_PERMISSIONS,
            PendingCalendarAction.SyncDeviceEvents.resolvePermission(disconnected, rationaleVisible = false)
        )
        assertEquals(
            CalendarPermissionResolution.READY,
            PendingCalendarAction.SyncDeviceEvents.resolvePermission(importOnly, rationaleVisible = false)
        )
    }

    @Test
    fun `export action opens settings when write permission is permanently denied`() {
        val status = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )

        assertEquals(
            CalendarPermissionResolution.OPEN_SETTINGS,
            PendingCalendarAction.ExportBlock("block-1").resolvePermission(status, rationaleVisible = false)
        )
    }

    @Test
    fun `export actions still require calendar write access`() {
        assertFalse(PendingCalendarAction.ExportBlock("block-1").hasRequiredPermissions(readGranted = true, writeGranted = false))
    }

    @Test
    fun `enable exports requests calendar read and write access`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            PendingCalendarAction.EnableExports.requiredCalendarPermissions()
        )
    }

    @Test
    fun `enable exports is ready only when calendar write permission is granted`() {
        val importOnly = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        )
        val exportReady = importOnly.copy(writeGranted = true)

        assertEquals(
            CalendarPermissionResolution.REQUEST_PERMISSIONS,
            PendingCalendarAction.EnableExports.resolvePermission(importOnly, rationaleVisible = false)
        )
        assertEquals(
            CalendarPermissionResolution.READY,
            PendingCalendarAction.EnableExports.resolvePermission(exportReady, rationaleVisible = false)
        )
    }

    @Test
    fun `calendar permission refresh key advances for grant result recomposition`() {
        assertEquals(1, nextCalendarPermissionRefreshKey(0))
        assertEquals(0, nextCalendarPermissionRefreshKey(Int.MAX_VALUE))
    }

    @Test
    fun `calendar connection status reflects import only permission when state is still default`() {
        val status = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        )

        assertEquals(
            "Calendar imports are connected; export access needs write permission",
            calendarConnectionStatusMessage(status, CalendarConnectionState())
        )
    }

    @Test
    fun `calendar connection status reflects full permission when state is still default`() {
        val status = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = true,
            shouldShowRationale = false,
            permanentlyDenied = false
        )

        assertEquals(
            "Calendar imports and exports are connected",
            calendarConnectionStatusMessage(status, CalendarConnectionState())
        )
    }

    @Test
    fun `calendar connection status keeps active repository messages`() {
        val status = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = true,
            shouldShowRationale = false,
            permanentlyDenied = false
        )
        val connectionState = CalendarConnectionState(statusMessage = "Device calendar refreshed for 2026-05-26")

        assertEquals(
            "Device calendar refreshed for 2026-05-26",
            calendarConnectionStatusMessage(status, connectionState)
        )
    }

    @Test
    fun `calendar connection status does not keep stale success after permission is revoked`() {
        val disconnected = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = false
        )
        val previousSuccess = CalendarConnectionState(
            statusMessage = "Device calendar refreshed for 2026-05-26",
            lastSuccessMessage = "Device calendar refreshed for 2026-05-26"
        )

        assertEquals(
            "Calendar not connected yet",
            calendarConnectionStatusMessage(disconnected, previousSuccess)
        )
    }

    @Test
    fun `calendar connection status names permanent denial when default state has no permission`() {
        val denied = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )

        assertEquals(
            "Calendar access is off",
            calendarConnectionStatusMessage(denied, CalendarConnectionState())
        )
    }

    @Test
    fun `calendar connection status names permanent denial over stale success`() {
        val denied = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )
        val previousSuccess = CalendarConnectionState(
            statusMessage = "Device calendar refreshed for 2026-05-26",
            lastSuccessMessage = "Device calendar refreshed for 2026-05-26"
        )

        assertEquals(
            "Calendar access is off",
            calendarConnectionStatusMessage(denied, previousSuccess)
        )
    }

    @Test
    fun `calendar connection status keeps import connected when export access is permanently denied`() {
        val exportDenied = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )
        val previousSuccess = CalendarConnectionState(
            statusMessage = "Device calendar refreshed for 2026-05-26",
            lastSuccessMessage = "Device calendar refreshed for 2026-05-26"
        )

        assertEquals(
            "Calendar imports are connected; export access is off",
            calendarConnectionStatusMessage(exportDenied, previousSuccess)
        )
    }

    @Test
    fun `calendar connection status shows active work when export access is permanently denied`() {
        val exportDenied = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )
        val workingState = CalendarConnectionState(
            isWorking = true,
            statusMessage = "Refreshing device calendar"
        )

        assertEquals(
            "Refreshing device calendar",
            calendarConnectionStatusMessage(exportDenied, workingState)
        )
    }

    @Test
    fun `calendar permission snackbar names export denial when imports stay connected`() {
        val exportDenied = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = true,
            permanentlyDenied = false
        )

        assertEquals(
            "Calendar export permission denied",
            calendarPermissionSnackbarMessage(exportDenied)
        )
    }

    @Test
    fun `calendar permission snackbar sends export denial to settings without disconnecting imports`() {
        val exportDenied = CalendarPermissionStatus(
            readGranted = true,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )

        assertEquals(
            "Calendar export access is off. Open settings to enable linked exports.",
            calendarPermissionSnackbarMessage(exportDenied)
        )
    }

    @Test
    fun `calendar permission snackbar keeps full access settings copy when read is denied`() {
        val denied = CalendarPermissionStatus(
            readGranted = false,
            writeGranted = false,
            shouldShowRationale = false,
            permanentlyDenied = true
        )

        assertEquals(
            "Calendar access is off. Open settings to enable imports and exports.",
            calendarPermissionSnackbarMessage(denied)
        )
    }
}
