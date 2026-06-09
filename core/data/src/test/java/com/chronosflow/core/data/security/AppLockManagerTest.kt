package com.chronosflow.core.data.security

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLockManagerTest {

    private lateinit var dataSource: ChronosPreferencesDataSource
    private lateinit var preferences: AppLockPreferences
    private lateinit var manager: AppLockManager

    @Before
    fun setUp() {
        dataSource = mockk(relaxed = true)
        preferences = AppLockPreferences(dataSource)
        manager = AppLockManager(preferences)
    }

    @Test
    fun `cold start locks when app lock enabled`() {
        every { dataSource.getBoolean(AppLockPreferences.KEY_APP_LOCK_ENABLED, false) } returns true
        val lockedManager = AppLockManager(AppLockPreferences(dataSource))

        assertTrue(lockedManager.requiresAppUnlock())
    }

    @Test
    fun `unlock clears app lock requirement`() {
        every { dataSource.getBoolean(AppLockPreferences.KEY_APP_LOCK_ENABLED, false) } returns true
        val lockedManager = AppLockManager(AppLockPreferences(dataSource))

        lockedManager.unlockApp()

        assertFalse(lockedManager.requiresAppUnlock())
    }

    @Test
    fun `medication requires auth when preference enabled`() {
        every { dataSource.getBoolean(AppLockPreferences.KEY_REQUIRE_AUTH_MEDICATION, true) } returns true

        assertTrue(manager.requiresSensitiveAuth(SensitiveArea.MEDICATION))
    }

    @Test
    fun `unlocking medication area clears requirement for session`() {
        every { dataSource.getBoolean(AppLockPreferences.KEY_REQUIRE_AUTH_MEDICATION, true) } returns true

        manager.unlockSensitiveArea(SensitiveArea.MEDICATION)

        assertFalse(manager.requiresSensitiveAuth(SensitiveArea.MEDICATION))
    }

    @Test
    fun `resume from background relocks app`() {
        every { dataSource.getBoolean(AppLockPreferences.KEY_APP_LOCK_ENABLED, false) } returns true
        every { dataSource.getBoolean(AppLockPreferences.KEY_LOCK_ON_RESUME, true) } returns true
        val lockedManager = AppLockManager(AppLockPreferences(dataSource))
        lockedManager.unlockApp()

        lockedManager.onAppForegrounded(fromBackground = true)

        assertTrue(lockedManager.requiresAppUnlock())
    }

    @Test
    fun `enabling app lock sets sensible defaults`() {
        preferences.setAppLockEnabled(true)

        verify { dataSource.putBoolean(AppLockPreferences.KEY_APP_LOCK_ENABLED, true) }
    }
}
