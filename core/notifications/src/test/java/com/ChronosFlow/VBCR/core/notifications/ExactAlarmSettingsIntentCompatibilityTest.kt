package com.ChronosFlow.VBCR.core.notifications

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class ExactAlarmSettingsIntentCompatibilityTest {
    @Test
    fun `supported sdk uses documented exact alarm action first`() {
        val specs = buildExactAlarmSettingsIntentSpecs(
            packageName = "com.ChronosFlow.VBCR",
            sdkInt = 31
        )

        assertEquals(
            listOf(
                ExactAlarmSettingsIntentSpec(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                ExactAlarmSettingsIntentSpec(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:com.ChronosFlow.VBCR"
                )
            ),
            specs
        )
    }

    @Test
    fun `android 13 and newer keep the same exact alarm settings ordering`() {
        val specs = buildExactAlarmSettingsIntentSpecs(
            packageName = "com.ChronosFlow.VBCR",
            sdkInt = 33
        )

        assertEquals(
            listOf(
                ExactAlarmSettingsIntentSpec(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                ExactAlarmSettingsIntentSpec(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:com.ChronosFlow.VBCR"
                )
            ),
            specs
        )
    }

    @Test
    fun `unsupported sdk returns no exact alarm settings intents`() {
        val specs = buildExactAlarmSettingsIntentSpecs(
            packageName = "com.ChronosFlow.VBCR",
            sdkInt = 30
        )

        assertEquals(emptyList<ExactAlarmSettingsIntentSpec>(), specs)
    }
}
