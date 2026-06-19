package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ai.AssistNarrative
import com.ChronosFlow.VBCR.core.ai.PrivacyMode
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ai.genai.GenAiRuntimeStatus

@Composable
fun MoodEnergyCheckInAssistCard(
    onSave: (mood: Int, stress: Int, energy: Int, focus: Int) -> Unit,
    privacyMode: PrivacyMode,
    genAiRuntimeStatus: GenAiRuntimeStatus,
    coaching: AssistNarrative? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChronosMoodEnergyCheckInCard(
            onSave = onSave,
            modifier = Modifier.fillMaxWidth()
        )
        coaching?.let { narrative ->
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = narrative.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = narrative.nextStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = GenAiAssistCopy.assistSourceLabel(narrative.source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
