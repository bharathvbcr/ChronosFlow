package com.chronosflow.core.data.privacy

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class PrivacyPreferencesTest {
    private val dataSource: ChronosPreferencesDataSource = mockk(relaxed = true)
    private lateinit var preferences: PrivacyPreferences

    @Before
    fun setUp() {
        preferences = PrivacyPreferences(dataSource)
    }

    @Test
    fun `notification redaction defaults to enabled`() {
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_NOTIFICATIONS, defaultValue = true) } returns true

        assertTrue(preferences.redactSensitiveNotifications())
    }

    @Test
    fun `command palette redaction setter persists choice`() {
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_COMMAND_SEARCH, true) } returns false
        every { dataSource.putBoolean(any(), any()) } just runs

        preferences.setRedactCommandPaletteHistory(false)

        verify {
            dataSource.putBoolean(PrivacyPreferences.KEY_REDACT_COMMAND_SEARCH, false)
        }
        assertFalse(preferences.redactCommandPaletteHistory())
    }

    @Test
    fun `widget medication redaction setter persists choice`() {
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION, true) } returns false
        every { dataSource.putBoolean(any(), any()) } just runs

        preferences.setRedactMedicationOnWidgets(false)

        verify {
            dataSource.putBoolean(PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION, false)
        }
        assertFalse(preferences.redactMedicationOnWidgets())
    }

    @Test
    fun `widget and command defaults can be read back separately`() {
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_COMMAND_SEARCH, defaultValue = true) } returns true
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION, defaultValue = true) } returns false

        assertTrue(preferences.redactCommandPaletteHistory())
        assertFalse(preferences.redactMedicationOnWidgets())
    }

    @Test
    fun `redaction keys are independent`() {
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_NOTIFICATIONS, defaultValue = true) } returns false
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_COMMAND_SEARCH, defaultValue = true) } returns true
        every { dataSource.getBoolean(PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION, defaultValue = true) } returns true

        assertFalse(preferences.redactSensitiveNotifications())
        assertEquals(PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION, PrivacyPreferences.KEY_REDACT_WIDGET_MEDICATION)
        assertEquals(PrivacyPreferences.KEY_REDACT_NOTIFICATIONS, PrivacyPreferences.KEY_REDACT_NOTIFICATIONS)
    }
}
