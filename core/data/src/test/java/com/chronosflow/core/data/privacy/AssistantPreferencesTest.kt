package com.chronosflow.core.data.privacy

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPreferencesTest {
    private val dataSource: ChronosPreferencesDataSource = mockk()
    private val preferences = AssistantPreferences(dataSource)

    @Test
    fun `assistant privacy mode defaults to on-device only`() {
        every {
            dataSource.getString(AssistantPreferences.KEY_ASSISTANT_PRIVACY_MODE, AssistantPreferences.DEFAULT_PRIVACY_MODE)
        } returns AssistantPreferences.DEFAULT_PRIVACY_MODE

        assertEquals(AssistantPreferences.DEFAULT_PRIVACY_MODE, preferences.assistantPrivacyModeValue())
    }

    @Test
    fun `setting assistant privacy mode stores raw value`() {
        every { dataSource.putString(any(), any()) } just runs

        preferences.setAssistantPrivacyModeValue("CLOUD_ALLOWED")

        verify {
            dataSource.putString(AssistantPreferences.KEY_ASSISTANT_PRIVACY_MODE, "CLOUD_ALLOWED")
        }
    }

    @Test
    fun `preview nano flag defaults to disabled`() {
        every {
            dataSource.getBoolean(AssistantPreferences.KEY_PREFER_PREVIEW_NANO_MODEL, false)
        } returns false

        assertFalse(preferences.preferPreviewNanoModel())
    }

    @Test
    fun `setting preview nano flag persists choice`() {
        every { dataSource.putBoolean(any(), any()) } just runs
        every {
            dataSource.getBoolean(AssistantPreferences.KEY_PREFER_PREVIEW_NANO_MODEL, false)
        } returns true

        preferences.setPreferPreviewNanoModel(true)

        verify {
            dataSource.putBoolean(AssistantPreferences.KEY_PREFER_PREVIEW_NANO_MODEL, true)
        }
        assertTrue(preferences.preferPreviewNanoModel())
    }
}
