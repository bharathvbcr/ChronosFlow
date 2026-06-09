package com.chronosflow.feature.focus

import com.chronosflow.core.domain.model.FocusSessionEvent
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.planner.FocusSessionReducer
import java.time.Duration
import java.time.Instant

internal data class FocusSessionSnapshot(
    val state: FocusSessionState,
    val totalSeconds: Int,
    val timeLeftSeconds: Int,
    val sessionId: String?,
    val blockId: String?,
    val plannedEndAt: Instant?,
    val isRunning: Boolean,
    val isPaused: Boolean
)

internal class FocusSessionRuntime(
    private val reducer: FocusSessionReducer,
    private val now: () -> Instant = Instant::now
) {
    private var state: FocusSessionState = FocusSessionState.Idle
    private var totalSeconds: Int = DEFAULT_FOCUS_SECONDS

    fun restore(state: FocusSessionState, totalSeconds: Int) {
        this.state = state
        this.totalSeconds = totalSeconds
    }

    fun start(
        sessionId: String,
        blockId: String?,
        timeLeftSeconds: Int,
        totalSeconds: Int
    ): FocusSessionSnapshot {
        val currentNow = now()
        this.totalSeconds = totalSeconds
        state = reducer.reduce(
            state,
            FocusSessionEvent.Start(
                sessionId = sessionId,
                blockId = blockId,
                now = currentNow,
                plannedEndAt = currentNow.plusSeconds(timeLeftSeconds.toLong())
            )
        )
        return snapshot(currentNow)
    }

    fun pause(): FocusSessionSnapshot {
        val currentNow = now()
        state = reducer.reduce(state, FocusSessionEvent.Pause(currentNow))
        return snapshot(currentNow)
    }

    fun resume(): FocusSessionSnapshot {
        val currentNow = now()
        state = reducer.reduce(state, FocusSessionEvent.Resume(currentNow))
        return snapshot(currentNow)
    }

    fun extend(extraSeconds: Int): FocusSessionSnapshot = adjustSeconds(extraSeconds)

    fun adjustSeconds(deltaSeconds: Int): FocusSessionSnapshot {
        val currentNow = now()
        val plannedEnd = plannedEndAtOf(state)
        if (plannedEnd == null || deltaSeconds == 0) {
            return snapshot(currentNow)
        }
        val minEnd = currentNow.plusSeconds(60)
        val requestedEnd = plannedEnd.plusSeconds(deltaSeconds.toLong())
        val clampedEnd = when {
            deltaSeconds < 0 && requestedEnd.isBefore(minEnd) -> minEnd
            else -> requestedEnd
        }
        val appliedDelta = java.time.Duration.between(plannedEnd, clampedEnd).seconds.toInt()
        if (appliedDelta == 0) {
            return snapshot(currentNow)
        }
        totalSeconds = (totalSeconds + appliedDelta).coerceAtLeast(60)
        state = reducer.reduce(state, FocusSessionEvent.Extend(clampedEnd))
        return snapshot(currentNow)
    }

    fun completeIfFinished(): Boolean {
        if (state !is FocusSessionState.Running || remainingSeconds() > 0) {
            return false
        }
        state = reducer.reduce(state, FocusSessionEvent.Complete)
        return true
    }

    fun archive(): FocusSessionSnapshot {
        val archivedState = sessionIdOf(state)?.let { FocusSessionState.Archived(it) } ?: state
        state = archivedState
        return snapshot()
    }

    fun snapshot(at: Instant = now()): FocusSessionSnapshot = FocusSessionSnapshot(
        state = state,
        totalSeconds = totalSeconds,
        timeLeftSeconds = remainingSeconds(at),
        sessionId = sessionIdOf(state),
        blockId = blockIdOf(state),
        plannedEndAt = plannedEndAtOf(state),
        isRunning = state is FocusSessionState.Running,
        isPaused = state is FocusSessionState.Paused
    )

    fun remainingSeconds(at: Instant = now()): Int = when (val current = state) {
        is FocusSessionState.Running -> Duration.between(at, current.plannedEndAt)
            .seconds
            .coerceAtLeast(0L)
            .toInt()

        is FocusSessionState.Paused -> Duration.between(current.pausedAt, current.plannedEndAt)
            .seconds
            .coerceAtLeast(0L)
            .toInt()

        else -> totalSeconds
    }

    companion object {
        private const val DEFAULT_FOCUS_SECONDS = 25 * 60
    }
}

private fun sessionIdOf(state: FocusSessionState): String? = when (state) {
    is FocusSessionState.Running -> state.sessionId
    is FocusSessionState.Paused -> state.sessionId
    is FocusSessionState.Completing -> state.sessionId
    is FocusSessionState.Archived -> state.sessionId
    is FocusSessionState.ServiceKilledRecoverable -> state.sessionId
    else -> null
}

private fun blockIdOf(state: FocusSessionState): String? = when (state) {
    is FocusSessionState.Running -> state.blockId
    is FocusSessionState.Paused -> state.blockId
    else -> null
}

private fun plannedEndAtOf(state: FocusSessionState): Instant? = when (state) {
    is FocusSessionState.Running -> state.plannedEndAt
    is FocusSessionState.Paused -> state.plannedEndAt
    else -> null
}
