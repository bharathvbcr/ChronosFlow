package com.chronosflow.feature.daydial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronosflow.core.ui.components.ChronosSectionTitle
import com.chronosflow.core.ui.components.ChronosWarningBanner
import com.chronosflow.feature.daydial.delegate.AppLockSettingsState

@Composable
internal fun AppLockSettingsSection(
    settings: AppLockSettingsState,
    canAuthenticate: Boolean,
    onAppLockEnabledChanged: (Boolean) -> Unit,
    onLockOnResumeChanged: (Boolean) -> Unit,
    onRequireAuthMedicationChanged: (Boolean) -> Unit,
    onRequireAuthReviewChanged: (Boolean) -> Unit,
    onRequireAuthDataExportChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ChronosSectionTitle(title = "App lock", subtitle = "Biometric or device PIN")
        if (!canAuthenticate) {
            ChronosWarningBanner(
                title = "Screen lock required",
                message = "Set a PIN, pattern, or password in Android settings before enabling app lock."
            )
        }
        CheckboxSetting(
            label = "Lock app on open and resume",
            checked = settings.appLockEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !canAuthenticate) return@CheckboxSetting
                onAppLockEnabledChanged(enabled)
            }
        )
        if (settings.appLockEnabled) {
            CheckboxSetting(
                label = "Lock again when returning from background",
                checked = settings.lockOnResume,
                onCheckedChange = onLockOnResumeChanged
            )
        }
        ChronosSectionTitle(title = "Protected areas", subtitle = "Require unlock before opening")
        CheckboxSetting(
            label = "Require unlock for medications",
            checked = settings.requireAuthMedication,
            onCheckedChange = onRequireAuthMedicationChanged
        )
        CheckboxSetting(
            label = "Require unlock for insights and mood",
            checked = settings.requireAuthReview,
            onCheckedChange = onRequireAuthReviewChanged
        )
        CheckboxSetting(
            label = "Require unlock for data export",
            checked = settings.requireAuthDataExport,
            onCheckedChange = onRequireAuthDataExportChanged
        )
        Text(
            text = "Protected areas use your device screen lock even when full app lock is off.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText
        )
    }
}
