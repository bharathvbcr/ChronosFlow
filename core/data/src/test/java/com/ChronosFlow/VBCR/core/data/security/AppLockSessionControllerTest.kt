package com.ChronosFlow.VBCR.core.data.security

import androidx.fragment.app.FragmentActivity
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLockSessionControllerTest {

    private lateinit var values: MutableMap<String, Boolean>
    private lateinit var dataSource: ChronosPreferencesDataSource
    private lateinit var preferences: AppLockPreferences
    private lateinit var appLockManager: AppLockManager
    private lateinit var authenticator: AppLockAuthenticator
    private lateinit var controller: AppLockSessionController

    @Before
    fun setUp() {
        values = mutableMapOf()
        dataSource = mockk(relaxed = true)
        every { dataSource.getBoolean(any(), any()) } answers {
            values[firstArg()] ?: secondArg()
        }
        every { dataSource.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            Unit
        }
        preferences = AppLockPreferences(dataSource)
        appLockManager = AppLockManager(preferences)
        authenticator = mockk()
        controller = AppLockSessionController(preferences, appLockManager, authenticator)
    }

    @Test
    fun `enabling app lock updates preferences and locks app`() = runTest {
        controller.setAppLockEnabled(true)

        val state = controller.uiState.first()

        assertTrue(state.appLockEnabled)
        assertTrue(state.isAppLocked)
        assertTrue(values.getValue(AppLockPreferences.KEY_APP_LOCK_ENABLED))
    }

    @Test
    fun `successful app unlock clears locked state`() = runTest {
        val activity = mockk<FragmentActivity>()
        controller.setAppLockEnabled(true)
        every { authenticator.authenticateForAppUnlock(activity, any()) } answers {
            secondArg<(AppLockAuthResult) -> Unit>().invoke(AppLockAuthResult.Success)
        }

        controller.unlockApp(activity)

        assertFalse(controller.uiState.first().isAppLocked)
    }

    @Test
    fun `unavailable auth surfaces operator guidance`() = runTest {
        val activity = mockk<FragmentActivity>()
        every { authenticator.authenticateForAppUnlock(activity, any()) } answers {
            secondArg<(AppLockAuthResult) -> Unit>().invoke(AppLockAuthResult.Unavailable)
        }

        controller.unlockApp(activity)

        assertEquals(
            "Set a screen lock (PIN, pattern, or password) in Android settings.",
            controller.uiState.first().authError
        )
    }

    @Test
    fun `disabling sensitive area auth unlocks that area for current session`() {
        values[AppLockPreferences.KEY_REQUIRE_AUTH_MEDICATION] = true

        controller.setRequireAuthFor(SensitiveArea.MEDICATION, false)

        assertFalse(controller.requiresSensitiveAuth(SensitiveArea.MEDICATION))
    }

    @Test
    fun `device auth capability refreshes ui state`() = runTest {
        val activity = mockk<FragmentActivity>()
        every { authenticator.canAuthenticate(activity) } returns false

        controller.refreshDeviceAuth(activity)

        assertFalse(controller.uiState.first().canAuthenticate)
    }
}
