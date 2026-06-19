package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ChronosFlow.VBCR.core.ai.AssistNarrative
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.GenAiRuntimeStatus
import com.ChronosFlow.VBCR.core.ui.components.MoodEnergyCheckInAssistCard

@Composable
internal fun MoodEnergyCheckInCard(
    onSave: (mood: Int, stress: Int, energy: Int, focus: Int) -> Unit,
    privacyMode: PrivacyMode,
    genAiRuntimeStatus: GenAiRuntimeStatus,
    coaching: AssistNarrative? = null,
    modifier: Modifier = Modifier
) {
    MoodEnergyCheckInAssistCard(
        onSave = onSave,
        privacyMode = privacyMode,
        genAiRuntimeStatus = genAiRuntimeStatus,
        coaching = coaching,
        modifier = modifier
    )
}
