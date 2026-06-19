package com.ChronosFlow.VBCR.core.ui.security

import com.ChronosFlow.VBCR.core.ui.components.ChronosButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.ui.components.ChronosBackground
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.settings.resolveChronosDarkTheme

@Composable
fun AppLockOverlay(
    title: String,
    subtitle: String,
    canAuthenticate: Boolean,
    errorMessage: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var didAutoUnlock by remember { mutableStateOf(false) }
    val uiSettings = rememberChronosUiSettings()
    val darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode)

    LaunchedEffect(canAuthenticate) {
        if (!canAuthenticate) {
            didAutoUnlock = false
            return@LaunchedEffect
        }
        if (didAutoUnlock) return@LaunchedEffect
        didAutoUnlock = true
        onUnlock()
    }

    ChronosBackground(
        modifier = modifier.fillMaxSize(),
        darkTheme = darkTheme,
        highContrast = uiSettings.highContrastEnabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (canAuthenticate) Icons.Default.Fingerprint else Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            ChronosButton(
                onClick = onUnlock,
                enabled = canAuthenticate
            ) {
                Text(if (canAuthenticate) "Unlock" else "Set up device lock")
            }
        }
    }
}
