package com.chronosflow.feature.daydial

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.notifications.NotificationPermissions
import com.chronosflow.feature.daydial.model.DayDialTab
import com.chronosflow.feature.daydial.model.SheetTarget
import kotlinx.coroutines.delay

@Composable
internal fun rememberNotificationPermissionRequester(
    context: Context,
    viewModel: DayDialViewModel,
    settings: DayDialSettingsState,
    uiState: DayDialScreenUiState
): () -> Unit {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        uiState.snackbarMessage = NotificationPermissions.resultMessage(context, results)
        viewModel.refreshReminderSchedule(
            blockStartReminders = settings.blockStartReminders,
            breakReminders = settings.breakReminders,
            missedAlerts = settings.missedAlerts,
            endDayReviewReminder = settings.endDayReviewReminder,
            sleepScheduleEnabled = settings.sleepScheduleEnabled,
            sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute
        )
    }

    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            uiState.snackbarMessage = "Notifications are available on this Android version"
        } else if (NotificationPermissions.areFocusNotificationsReady(context)) {
            uiState.snackbarMessage = "Notifications are already enabled"
        } else {
            notificationPermissionLauncher.launch(NotificationPermissions.requiredPermissions())
        }
    }
}

@Composable
internal fun DayDialScreenEffects(
    viewModel: DayDialViewModel,
    vmState: DayDialViewModelState,
    uiState: DayDialScreenUiState,
    settings: DayDialSettingsState,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(vmState.selectedBlock, uiState.activeSheet) {
        if (vmState.selectedBlock != null &&
            uiState.activeSheet !is SheetTarget.BlockEditor &&
            uiState.activeSheet !is SheetTarget.NewBlock
        ) {
            uiState.activeSheet = SheetTarget.BlockEditor(vmState.selectedBlock.id)
        }
    }

    LaunchedEffect(vmState.focusSession.status, uiState.currentTab) {
        while (vmState.focusSession.status == FocusExecutionStatus.RUNNING && uiState.currentTab == DayDialTab.FOCUS) {
            delay(1_000)
            uiState.focusTick++
        }
    }

    LaunchedEffect(
        vmState.selectedDate,
        vmState.timeBlocks,
        settings.blockStartReminders,
        settings.breakReminders,
        settings.missedAlerts,
        settings.endDayReviewReminder,
        settings.sleepScheduleEnabled,
        settings.sleepScheduleStartMinute,
        settings.sleepScheduleEndMinute
    ) {
        viewModel.refreshReminderSchedule(
            blockStartReminders = settings.blockStartReminders,
            breakReminders = settings.breakReminders,
            missedAlerts = settings.missedAlerts,
            endDayReviewReminder = settings.endDayReviewReminder,
            sleepScheduleEnabled = settings.sleepScheduleEnabled,
            sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute
        )
    }

    val focusRestoredMessage by viewModel.focusRestoredMessage.collectAsStateWithLifecycle()
    val missedFromFocusMessage by viewModel.missedFromFocusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            uiState.snackbarMessage = null
        }
    }

    LaunchedEffect(focusRestoredMessage) {
        focusRestoredMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearFocusRestoredMessage()
        }
    }

    LaunchedEffect(missedFromFocusMessage) {
        missedFromFocusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMissedFromFocusMessage()
        }
    }
}
