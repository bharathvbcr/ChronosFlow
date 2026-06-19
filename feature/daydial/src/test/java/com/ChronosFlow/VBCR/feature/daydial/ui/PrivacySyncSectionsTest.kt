package com.ChronosFlow.VBCR.feature.daydial.ui

import com.ChronosFlow.VBCR.core.domain.wear.WearLinkStatus
import com.ChronosFlow.VBCR.feature.daydial.delegate.AppLockSettingsState
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacySyncSectionsTest {

    @Test
    fun `privacyAppLockSummary reflects lock modes`() {
        val off = AppLockSettingsState(
            requireAuthMedication = false,
            requireAuthReview = false,
            requireAuthDataExport = false
        )
        assertEquals("Off", privacyAppLockSummary(off))

        val onOpen = off.copy(appLockEnabled = true, lockOnResume = false)
        assertEquals("Locked on open", privacyAppLockSummary(onOpen))

        val onResume = onOpen.copy(lockOnResume = true)
        assertEquals("Locked on open and resume", privacyAppLockSummary(onResume))

        val protectedOnly = off.copy(requireAuthMedication = true)
        assertEquals("Protected areas only", privacyAppLockSummary(protectedOnly))
    }

    @Test
    fun `privacySensitiveContentSummary reflects redaction`() {
        assertEquals("Titles visible", privacySensitiveContentSummary(false))
        assertEquals("Titles hidden on watch and notifications", privacySensitiveContentSummary(true))
    }

    @Test
    fun `privacyCloudSyncSummary reflects checkpoint state`() {
        assertEquals("Checkpoints off", privacyCloudSyncSummary(syncCloud = false, syncStatus = "Never"))
        assertEquals(
            "Checkpoints on · 2 hours ago",
            privacyCloudSyncSummary(syncCloud = true, syncStatus = "2 hours ago")
        )
    }

    @Test
    fun `wearLinkConnectionSummary reflects watch status`() {
        assertEquals("Checking watch…", wearLinkConnectionSummary(null))
        assertEquals(
            "Connected: Pixel Watch",
            wearLinkConnectionSummary(
                WearLinkStatus(
                    watchPaired = true,
                    watchConnected = true,
                    watchAppInstalled = true,
                    connectedNodeName = "Pixel Watch",
                    lastPublishedAtMillis = 0L
                )
            )
        )
    }

    @Test
    fun `privacyPermissionsSummary counts granted permissions`() {
        val allGranted = PrivacyPermissionStates(
            notificationsGranted = true,
            promotedGranted = true,
            includePromotedPermission = true,
            exactAlarmsGranted = true,
            includeExactAlarmsPermission = true,
            calendarReadGranted = true,
            calendarWriteGranted = true,
            healthConnectGranted = true,
            includeHealthConnectPermission = true,
            workoutsGranted = true,
            includeWorkoutsPermission = true,
            usageAccessGranted = true,
            contactsGranted = true
        )
        assertEquals("All access granted", privacyPermissionsSummary(allGranted))

        val noneGranted = allGranted.copy(
            notificationsGranted = false,
            promotedGranted = false,
            exactAlarmsGranted = false,
            calendarReadGranted = false,
            calendarWriteGranted = false,
            healthConnectGranted = false,
            workoutsGranted = false,
            usageAccessGranted = false,
            contactsGranted = false
        )
        assertEquals("No access granted", privacyPermissionsSummary(noneGranted))

        val partial = allGranted.copy(
            promotedGranted = false,
            includePromotedPermission = false,
            includeExactAlarmsPermission = false,
            includeHealthConnectPermission = false,
            includeWorkoutsPermission = false,
            calendarReadGranted = false,
            calendarWriteGranted = false,
            healthConnectGranted = false,
            workoutsGranted = false,
            usageAccessGranted = false,
            contactsGranted = false
        )
        assertEquals("1 of 5 granted", privacyPermissionsSummary(partial))
    }
}
