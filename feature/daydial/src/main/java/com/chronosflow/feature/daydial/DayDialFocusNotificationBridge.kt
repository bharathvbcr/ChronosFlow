package com.chronosflow.feature.daydial

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chronosflow.feature.daydial.model.FocusPhase
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import com.chronosflow.feature.focus.FocusService
import com.chronosflow.feature.focus.sendFocusServiceCommand
import java.util.UUID

/** Backgrounded "tap to continue" prompt describing the phase about to begin. */
internal fun focusPhaseBoundaryText(next: FocusPhase): String = when (next.kind) {
    FocusPhaseKind.BREAK -> "Time for a ${next.durationMinutes}m break — tap to continue"
    FocusPhaseKind.FOCUS -> "Back to focus for ${next.durationMinutes}m — tap to continue"
}

/**
 * Keeps [FocusService] aligned with the in-tab [FocusExecutionState] so Focus tab sessions
 * get live notifications and recovery behavior.
 */
@Composable
internal fun DayDialFocusNotificationBridge(
    context: Context,
    focusSession: FocusExecutionState,
    remainingSeconds: Long,
    recoverableServiceSessionId: String? = null
) {
    var serviceSessionId by remember(recoverableServiceSessionId) {
        mutableStateOf(recoverableServiceSessionId)
    }
    var previousStatus by remember { mutableStateOf(FocusExecutionStatus.IDLE) }
    var lastSyncedPlannedMinutes by remember { mutableIntStateOf(0) }
    var lastSyncedPhaseIndex by remember { mutableIntStateOf(0) }
    val totalSeconds = (focusSession.plannedDurationMinutes * 60).coerceAtLeast(60)
    val timeLeft = remainingSeconds.toInt().coerceAtLeast(0)
    val blockId = focusSession.blockId

    LaunchedEffect(recoverableServiceSessionId) {
        if (recoverableServiceSessionId != null) {
            serviceSessionId = recoverableServiceSessionId
        }
    }

    LaunchedEffect(focusSession.status, blockId) {
        when (focusSession.status) {
            FocusExecutionStatus.RUNNING -> {
                if (serviceSessionId == null) {
                    // Fresh start — also the entry point for each new phase of a
                    // split session, since the boundary STOP clears the id below.
                    val newId = UUID.randomUUID().toString()
                    serviceSessionId = newId
                    lastSyncedPlannedMinutes = focusSession.plannedDurationMinutes
                    lastSyncedPhaseIndex = focusSession.currentPhaseIndex
                    val boundaryLabel = if (focusSession.isSplitSession) {
                        focusSession.nextPhase?.let(::focusPhaseBoundaryText)
                    } else {
                        null
                    }
                    context.sendFocusServiceCommand(
                        action = com.chronosflow.feature.focus.FocusService.ACTION_START,
                        timeLeft = timeLeft,
                        totalSeconds = totalSeconds,
                        sessionId = newId,
                        blockId = blockId,
                        terminal = !focusSession.isSplitSession,
                        boundaryLabel = boundaryLabel
                    )
                } else if (previousStatus == FocusExecutionStatus.PAUSED) {
                    context.sendFocusServiceCommand(
                        action = com.chronosflow.feature.focus.FocusService.ACTION_RESUME,
                        timeLeft = timeLeft,
                        totalSeconds = totalSeconds,
                        sessionId = serviceSessionId,
                        blockId = blockId
                    )
                }
            }

            FocusExecutionStatus.PAUSED -> {
                val sessionId = serviceSessionId ?: return@LaunchedEffect
                if (focusSession.awaitingPhaseAdvance) {
                    // At a phase boundary nothing is counting down: clear the
                    // notification so the next phase starts a fresh service session.
                    context.sendFocusServiceCommand(
                        action = com.chronosflow.feature.focus.FocusService.ACTION_STOP,
                        timeLeft = timeLeft,
                        totalSeconds = totalSeconds,
                        sessionId = sessionId,
                        blockId = blockId,
                        archiveOnStop = false,
                        logActualOnStop = false
                    )
                    serviceSessionId = null
                    lastSyncedPlannedMinutes = 0
                    lastSyncedPhaseIndex = 0
                } else {
                    context.sendFocusServiceCommand(
                        action = com.chronosflow.feature.focus.FocusService.ACTION_PAUSE,
                        timeLeft = timeLeft,
                        totalSeconds = totalSeconds,
                        sessionId = sessionId,
                        blockId = blockId
                    )
                }
            }

            FocusExecutionStatus.FINISHED,
            FocusExecutionStatus.SKIPPED,
            FocusExecutionStatus.IDLE -> {
                val sessionId = serviceSessionId ?: return@LaunchedEffect
                context.sendFocusServiceCommand(
                    action = com.chronosflow.feature.focus.FocusService.ACTION_STOP,
                    timeLeft = timeLeft,
                    totalSeconds = totalSeconds,
                    sessionId = sessionId,
                    blockId = blockId,
                    archiveOnStop = true,
                    logActualOnStop = false,
                    wasSkip = focusSession.status == FocusExecutionStatus.SKIPPED
                )
                serviceSessionId = null
                lastSyncedPlannedMinutes = 0
                lastSyncedPhaseIndex = 0
            }
        }
        previousStatus = focusSession.status
    }

    LaunchedEffect(
        focusSession.plannedDurationMinutes,
        focusSession.currentPhaseIndex,
        focusSession.status,
        serviceSessionId
    ) {
        val sessionId = serviceSessionId ?: return@LaunchedEffect
        if (focusSession.status != FocusExecutionStatus.RUNNING &&
            focusSession.status != FocusExecutionStatus.PAUSED
        ) {
            return@LaunchedEffect
        }
        // A phase change restarts the service fresh (handled by the START branch),
        // so re-baseline here instead of syncing the cross-phase jump as an extend.
        if (focusSession.currentPhaseIndex != lastSyncedPhaseIndex) {
            lastSyncedPhaseIndex = focusSession.currentPhaseIndex
            lastSyncedPlannedMinutes = focusSession.plannedDurationMinutes
            return@LaunchedEffect
        }
        // Same phase, duration changed = the user tapped +/-5m: extend the live timer.
        val deltaMinutes = focusSession.plannedDurationMinutes - lastSyncedPlannedMinutes
        if (deltaMinutes == 0) return@LaunchedEffect
        lastSyncedPlannedMinutes = focusSession.plannedDurationMinutes
        context.sendFocusServiceCommand(
            action = com.chronosflow.feature.focus.FocusService.ACTION_EXTEND,
            timeLeft = timeLeft,
            totalSeconds = totalSeconds,
            sessionId = sessionId,
            blockId = blockId,
            adjustSeconds = deltaMinutes * 60
        )
    }

    LaunchedEffect(
        focusSession.status,
        focusSession.plannedDurationMinutes,
        remainingSeconds,
        serviceSessionId
    ) {
        val sessionId = serviceSessionId ?: return@LaunchedEffect
        if (focusSession.status != FocusExecutionStatus.RUNNING &&
            focusSession.status != FocusExecutionStatus.PAUSED
        ) {
            return@LaunchedEffect
        }
        context.sendFocusServiceCommand(
            action = com.chronosflow.feature.focus.FocusService.ACTION_SYNC,
            timeLeft = timeLeft,
            totalSeconds = totalSeconds,
            sessionId = sessionId,
            blockId = blockId
        )
    }
}
