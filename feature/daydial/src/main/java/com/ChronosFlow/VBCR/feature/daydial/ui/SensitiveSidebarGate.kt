package com.ChronosFlow.VBCR.feature.daydial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ChronosFlow.VBCR.core.ui.security.SensitiveContentGate

@Composable
internal fun SensitiveSidebarGate(
    title: String,
    message: String,
    requiresAuth: Boolean,
    canAuthenticate: Boolean,
    errorMessage: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!requiresAuth) {
        content()
        return
    }
    Box(modifier = modifier.fillMaxWidth()) {
        SensitiveContentGate(
            title = title,
            message = message,
            canAuthenticate = canAuthenticate,
            errorMessage = errorMessage,
            onUnlock = onUnlock
        )
    }
}
