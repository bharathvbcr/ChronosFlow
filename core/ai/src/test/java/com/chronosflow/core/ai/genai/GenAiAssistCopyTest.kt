package com.chronosflow.core.ai.genai

import com.chronosflow.core.ai.FocusAssistSource
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.RoutineAssistSource
import com.chronosflow.core.ai.TaskAssistSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenAiAssistCopyTest {

    @Test
    fun `banner title maps each privacy mode branch and nano state`() {
        assertEquals("AI assist disabled", GenAiAssistCopy.bannerTitle(PrivacyMode.DISABLED, GenAiRuntimeStatus()))
        assertEquals("Cloud Gemini enabled", GenAiAssistCopy.bannerTitle(PrivacyMode.CLOUD_ALLOWED, GenAiRuntimeStatus(cloudConfigured = true)))
        assertEquals(
            "Cloud Gemini not configured",
            GenAiAssistCopy.bannerTitle(PrivacyMode.CLOUD_ALLOWED, GenAiRuntimeStatus(cloudConfigured = false))
        )
        assertEquals(
            "Gemini Nano ready",
            GenAiAssistCopy.bannerTitle(PrivacyMode.ON_DEVICE_ONLY, GenAiRuntimeStatus(nanoStatus = NanoModelStatus.AVAILABLE))
        )
        assertEquals(
            "Gemini Nano preview ready",
            GenAiAssistCopy.bannerTitle(
                PrivacyMode.ON_DEVICE_ONLY,
                GenAiRuntimeStatus(nanoStatus = NanoModelStatus.AVAILABLE, previewModelActive = true)
            )
        )
        assertEquals("Downloading Gemini Nano", GenAiAssistCopy.bannerTitle(PrivacyMode.ON_DEVICE_ONLY, GenAiRuntimeStatus(nanoStatus = NanoModelStatus.DOWNLOADING)))
        assertEquals(
            "Gemini Nano available to download",
            GenAiAssistCopy.bannerTitle(
                PrivacyMode.ON_DEVICE_ONLY,
                GenAiRuntimeStatus(nanoStatus = NanoModelStatus.DOWNLOADABLE)
            )
        )
        assertEquals(
            "Gemini Nano preview available to download",
            GenAiAssistCopy.bannerTitle(
                PrivacyMode.ON_DEVICE_ONLY,
                GenAiRuntimeStatus(
                    nanoStatus = NanoModelStatus.DOWNLOADABLE,
                    previewModelActive = true
                )
            )
        )
        assertEquals(
            "Gemini Nano unsupported on this device",
            GenAiAssistCopy.bannerTitle(
                PrivacyMode.ON_DEVICE_ONLY,
                GenAiRuntimeStatus(nanoStatus = NanoModelStatus.UNAVAILABLE)
            )
        )
        assertEquals(
            "Gemini Nano preview unavailable",
            GenAiAssistCopy.bannerTitle(
                PrivacyMode.ON_DEVICE_ONLY,
                GenAiRuntimeStatus(
                    nanoStatus = NanoModelStatus.UNAVAILABLE,
                    previewModelRequested = true
                )
            )
        )
    }

    @Test
    fun `banner message changes based on privacy mode and status details`() {
        val base = GenAiRuntimeStatus(selectedModelLabel = "mini", previewModelRequested = true)
        val disabledMessage = GenAiAssistCopy.bannerMessage(PrivacyMode.DISABLED, base)
        val onDeviceMessage = GenAiAssistCopy.bannerMessage(
            PrivacyMode.ON_DEVICE_ONLY,
            base.copy(previewModelActive = true)
        )
        val cloudAllowedMessage = GenAiAssistCopy.bannerMessage(
            PrivacyMode.CLOUD_ALLOWED,
            base.copy(cloudConfigured = false, statusMessage = "api key not set")
        )

        assertTrue(disabledMessage.contains("Assist uses local heuristics only"))
        assertFalse(disabledMessage.contains("Cloud Gemini first"))
        assertTrue(onDeviceMessage.contains("Suggestions use Gemini Nano through AICore"))
        assertTrue(cloudAllowedMessage.contains("Add GEMINI_API_KEY"))
    }

    @Test
    fun `assist source labels are returned for each domain type`() {
        assertEquals("Gemini Nano", GenAiAssistCopy.assistSourceLabel(com.chronosflow.core.ai.genai.AssistGenAiSource.GEMINI_NANO))
        assertEquals("Cloud Gemini", GenAiAssistCopy.assistSourceLabel(com.chronosflow.core.ai.genai.AssistGenAiSource.CLOUD_GEMINI))
        assertEquals("Local", GenAiAssistCopy.assistSourceLabel(com.chronosflow.core.ai.genai.AssistGenAiSource.LOCAL))

        assertEquals("Gemini Nano", GenAiAssistCopy.taskAssistSourceLabel(TaskAssistSource.GEMINI_NANO))
        assertEquals("Cloud Gemini", GenAiAssistCopy.taskAssistSourceLabel(TaskAssistSource.CLOUD_GEMINI))
        assertEquals("Local", GenAiAssistCopy.taskAssistSourceLabel(TaskAssistSource.LOCAL))

        assertEquals("Gemini Nano", GenAiAssistCopy.routineAssistSourceLabel(RoutineAssistSource.GEMINI_NANO))
        assertEquals("Cloud Gemini", GenAiAssistCopy.routineAssistSourceLabel(RoutineAssistSource.CLOUD_GEMINI))
        assertEquals("Local", GenAiAssistCopy.routineAssistSourceLabel(RoutineAssistSource.LOCAL))

        assertEquals("Gemini Nano", GenAiAssistCopy.focusAssistSourceLabel(FocusAssistSource.GEMINI_NANO))
        assertEquals("Cloud Gemini", GenAiAssistCopy.focusAssistSourceLabel(FocusAssistSource.CLOUD_GEMINI))
        assertEquals("Local", GenAiAssistCopy.focusAssistSourceLabel(FocusAssistSource.LOCAL))

        assertEquals("AI assist is off. Suggestions below use local heuristics only.", GenAiAssistCopy.disabledAssistMessage())
    }

    @Test
    fun `privacy mode labels are stable`() {
        assertEquals("Gemini Nano (on-device)", GenAiAssistCopy.privacyModeLabel(PrivacyMode.ON_DEVICE_ONLY))
        assertEquals("Cloud Gemini", GenAiAssistCopy.privacyModeLabel(PrivacyMode.CLOUD_ALLOWED))
        assertEquals("Disabled", GenAiAssistCopy.privacyModeLabel(PrivacyMode.DISABLED))
    }
}
