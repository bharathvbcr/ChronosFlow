package com.chronosflow.feature.daydial.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chronosflow.core.ai.AssistNarrative
import com.chronosflow.core.ai.PrivacyMode
import com.chronosflow.core.ai.genai.GenAiRuntimeStatus
import com.chronosflow.core.ui.components.MoodEnergyCheckInAssistCard

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
