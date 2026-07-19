package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/**
 * Shows a snackbar with an Undo action and invokes [onUndo] when the user taps it.
 * Shared by Tasks / Habits / Goals / Medication delete-archive flows.
 */
suspend fun SnackbarHostState.showChronosUndoSnackbar(
    message: String,
    onUndo: suspend () -> Unit,
    actionLabel: String = "Undo",
    duration: SnackbarDuration = SnackbarDuration.Long,
) {
    val result = showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = duration,
    )
    if (result == SnackbarResult.ActionPerformed) {
        onUndo()
    }
}
