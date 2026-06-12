package com.chronosflow.feature.daydial

import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.feature.daydial.model.FocusExecutionState
import com.chronosflow.feature.daydial.model.FocusExecutionStatus
import java.time.Duration
import java.time.Instant

internal suspend fun buildFocusExecutionStateFromPersisted(
    session: FocusSessionState,
    repository: TimeBlockRepository
): FocusExecutionState? {
    return when (session) {
        is FocusSessionState.Running -> buildTabState(
            blockId = session.blockId,
            startedAt = session.startedAt,
            plannedEndAt = session.plannedEndAt,
            pausedAt = null,
            status = FocusExecutionStatus.RUNNING,
            repository = repository
        )

        is FocusSessionState.Paused -> buildTabState(
            blockId = session.blockId,
            startedAt = session.startedAt,
            plannedEndAt = session.plannedEndAt,
            pausedAt = session.pausedAt,
            status = FocusExecutionStatus.PAUSED,
            repository = repository
        )

        else -> null
    }
}

private suspend fun buildTabState(
    blockId: String?,
    startedAt: Instant,
    plannedEndAt: Instant,
    pausedAt: Instant?,
    status: FocusExecutionStatus,
    repository: TimeBlockRepository
): FocusExecutionState {
    val block = blockId?.let { repository.getTimeBlockById(it) }
    val plannedMinutes = Duration.between(startedAt, plannedEndAt)
        .seconds
        .let { seconds -> ((seconds + 59) / 60).toInt() }
        .coerceIn(5, 480)

    return FocusExecutionState(
        blockId = blockId,
        blockTitle = block?.title ?: "Focus session",
        blockStartMinute = block?.startMinuteOfDay ?: 0,
        plannedDurationMinutes = plannedMinutes,
        startedEpochMs = startedAt.toEpochMilli(),
        status = status,
        pausedAtEpochMs = pausedAt?.toEpochMilli(),
        totalPausedMs = 0L
    )
}

internal fun recoverableServiceSessionId(session: FocusSessionState?): String? = when (session) {
    is FocusSessionState.Running -> session.sessionId
    is FocusSessionState.Paused -> session.sessionId
    else -> null
}

internal fun recoverableSessionBlockId(session: FocusSessionState?): String? = when (session) {
    is FocusSessionState.Running -> session.blockId
    is FocusSessionState.Paused -> session.blockId
    else -> null
}
