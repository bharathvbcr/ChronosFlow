package com.ChronosFlow.VBCR.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * Tests that [HealthConnectSleepDataSource] degrades gracefully when Health Connect is
 * not available on the device.
 *
 * The production class gates every HealthConnectClient call behind `isAvailable()` which
 * delegates to `HealthConnectClient.getSdkStatus(context)`. We use MockK's `mockkStatic`
 * to intercept that static call and return the NOT_SUPPORTED path, then call the real
 * production methods to verify they all short-circuit to empty/null/false rather than
 * throwing or calling into the unavailable client.
 *
 * This test covers the graceful-degradation contract on devices where Health Connect
 * is not installed or is disabled.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectSleepDataSourceUnavailableTest {

    private lateinit var context: Context
    private lateinit var dataSource: HealthConnectSleepDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Intercept the static HealthConnectClient.getSdkStatus() call so the real
        // production code in HealthConnectSleepDataSource.availability() returns NOT_SUPPORTED.
        mockkStatic(HealthConnectClient::class)
        every { HealthConnectClient.getSdkStatus(any()) } returns HealthConnectClient.SDK_UNAVAILABLE
        dataSource = HealthConnectSleepDataSource(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * When Health Connect is not installed / not supported, [isAvailable] must
     * report false so callers can skip the feature entirely without attempting to
     * obtain a client.
     */
    @Test
    fun `healthConnectSync whenNotAvailable isAvailableReturnsFalse`() {
        assertFalse(
            "isAvailable() must return false when SDK_UNAVAILABLE",
            dataSource.isAvailable()
        )
    }

    /**
     * [availability] must map SDK_UNAVAILABLE to [HealthConnectAvailability.NOT_SUPPORTED].
     */
    @Test
    fun `healthConnectSync whenNotAvailable availabilityIsNotSupported`() {
        assertEquals(
            HealthConnectAvailability.NOT_SUPPORTED,
            dataSource.availability()
        )
    }

    /**
     * [readSessions] must return an empty list rather than throwing when no
     * HealthConnectClient can be created. This is the primary safeguard against
     * crashing on devices without Health Connect.
     */
    @Test
    fun `healthConnectSync whenNotAvailable readSessionsReturnsEmpty`() = runTest {
        val result = dataSource.readSessions(
            start = Instant.parse("2026-06-19T22:00:00Z"),
            end = Instant.parse("2026-06-20T10:00:00Z")
        )

        assertEquals(
            "readSessions must return emptyList when Health Connect is unavailable",
            emptyList<Any>(),
            result
        )
    }

    /**
     * [grantedPermissions] must return an empty set rather than crashing when
     * Health Connect is unavailable (the client reference is null).
     */
    @Test
    fun `healthConnectSync whenNotAvailable grantedPermissionsReturnsEmpty`() = runTest {
        val perms = dataSource.grantedPermissions()

        assertEquals(
            "grantedPermissions must return emptySet when Health Connect is unavailable",
            emptySet<Any>(),
            perms
        )
    }

    /**
     * [hasSleepReadPermission] must return false rather than throwing when Health
     * Connect is not available (cannot retrieve granted permissions).
     */
    @Test
    fun `healthConnectSync whenNotAvailable hasSleepReadPermissionReturnsFalse`() = runTest {
        assertFalse(
            "hasSleepReadPermission must return false when Health Connect is unavailable",
            dataSource.hasSleepReadPermission()
        )
    }

    /**
     * [changesToken] must return null when Health Connect is unavailable.
     * Callers that check for null before persisting a changes token will skip
     * the incremental sync path safely.
     */
    @Test
    fun `healthConnectSync whenNotAvailable changesTokenReturnsNull`() = runTest {
        assertNull(
            "changesToken must return null when Health Connect is unavailable",
            dataSource.changesToken()
        )
    }

    /**
     * [changesSince] must return [SleepChangesResult.Expired] when Health Connect is
     * unavailable (clientOrNull() returns null), signalling the caller to re-seed
     * rather than partially applying changes from a potentially corrupt state.
     */
    @Test
    fun `healthConnectSync whenNotAvailable changesSinceReturnsExpired`() = runTest {
        val result = dataSource.changesSince(token = "stale-token")

        assertEquals(
            "changesSince must return Expired when Health Connect is unavailable",
            SleepChangesResult.Expired,
            result
        )
    }
}
