package com.chronosflow.feature.daydial

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute,
            journalRemindersEnabled = settings.featureFlags.journalEnabled,
            sleepJournalLogReminder = settings.sleepJournalLogReminder,
            sleepJournalRemindersEnabled = settings.featureFlags.sleepEnabled || settings.featureFlags.journalEnabled
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
            viewModel.checkFocusPhaseBoundary()
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
        settings.sleepScheduleEndMinute,
        settings.featureFlags.journalEnabled,
        settings.featureFlags.sleepEnabled,
        settings.sleepJournalLogReminder
    ) {
        viewModel.refreshReminderSchedule(
            blockStartReminders = settings.blockStartReminders,
            breakReminders = settings.breakReminders,
            missedAlerts = settings.missedAlerts,
            endDayReviewReminder = settings.endDayReviewReminder,
            sleepScheduleEnabled = settings.sleepScheduleEnabled,
            sleepScheduleStartMinute = settings.sleepScheduleStartMinute,
            sleepScheduleEndMinute = settings.sleepScheduleEndMinute,
            journalRemindersEnabled = settings.featureFlags.journalEnabled,
            sleepJournalLogReminder = settings.sleepJournalLogReminder,
            sleepJournalRemindersEnabled = settings.featureFlags.sleepEnabled || settings.featureFlags.journalEnabled
        )
    }

    val focusRestoredMessage by viewModel.focusRestoredMessage.collectAsStateWithLifecycle()
    val missedFromFocusMessage by viewModel.missedFromFocusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            val actionLabel = uiState.snackbarActionLabel
            val onAction = uiState.onSnackbarAction
            // showSnackbar defaults to Indefinite when an actionLabel is set; an Undo bar
            // should still auto-dismiss, so give it a bounded Long duration.
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
            uiState.snackbarMessage = null
            uiState.snackbarActionLabel = null
            uiState.onSnackbarAction = null
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
