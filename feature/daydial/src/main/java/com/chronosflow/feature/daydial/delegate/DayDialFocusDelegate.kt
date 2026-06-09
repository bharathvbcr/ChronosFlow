package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.ActualTimeSource
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.feature.daydial.buildFocusExecutionStateFromPersisted
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.LogActualTimeUseCase
import com.chronosflow.feature.daydial.DialUtils
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DayDialFocusDelegate @Inject constructor(
    private val repository: TimeBlockRepository,
    private val logActualTimeUseCase: LogActualTimeUseCase,
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry
) {
    private val _focusExecutionState = MutableStateFlow(FocusExecutionState())
    val focusExecutionState = _focusExecutionState.asStateFlow()

    fun startFocusSession(
        scope: CoroutineScope,
        plannerService: PlannerService,
        blockId: String
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val startedAt = System.currentTimeMillis()
            _focusExecutionState.value = FocusExecutionState(
                blockId = block.id,
                blockTitle = block.title,
                blockStartMinute = block.startMinuteOfDay,
                plannedDurationMinutes = block.durationMinutes,
                startedEpochMs = startedAt,
                status = FocusExecutionStatus.RUNNING
            )
            val startMinute = DialUtils.snapToIncrement(block.startMinuteOfDay)
            val endMinute = DialUtils.snapToIncrement((startMinute + 15).coerceAtLeast(startMinute + 1))
            plannerService.logActualWindow(block.id, startMinute, endMinute)
            logActualTimeUseCase(block.toActualSegment(startMinute, endMinute))
        }
    }

    fun pauseFocusSession() {
        val state = _focusExecutionState.value
        if (state.status != FocusExecutionStatus.RUNNING || state.blockId == null) return
        _focusExecutionState.value = state.copy(
            status = FocusExecutionStatus.PAUSED,
            pausedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun resumeFocusSession() {
        val state = _focusExecutionState.value
        val pausedAt = state.pausedAtEpochMs ?: return
        if (state.status != FocusExecutionStatus.PAUSED) return
        val pauseMs = System.currentTimeMillis() - pausedAt
        _focusExecutionState.value = state.copy(
            status = FocusExecutionStatus.RUNNING,
            pausedAtEpochMs = null,
            totalPausedMs = state.totalPausedMs + pauseMs
        )
    }

    fun extendFocusSession(additionalMinutes: Int = 15) {
        val state = _focusExecutionState.value
        if (state.status == FocusExecutionStatus.IDLE || state.status == FocusExecutionStatus.SKIPPED) return
        _focusExecutionState.value = state.copy(
            plannedDurationMinutes = (state.plannedDurationMinutes + additionalMinutes).coerceAtMost(480)
        )
    }

    fun shortenFocusSession(minutes: Int = 15) {
        val state = _focusExecutionState.value
        if (state.status == FocusExecutionStatus.IDLE || state.status == FocusExecutionStatus.SKIPPED) return
        _focusExecutionState.value = state.copy(
            plannedDurationMinutes = (state.plannedDurationMinutes - minutes).coerceAtLeast(5)
        )
    }

    fun skipFocusSession(onSkippedBlock: (String) -> Unit) {
        val blockId = _focusExecutionState.value.blockId
        _focusExecutionState.value = FocusExecutionState(status = FocusExecutionStatus.SKIPPED)
        blockId?.let(onSkippedBlock)
    }

    /** Aligns tab UI when full-screen Focus skips the same linked block. Returns true if state changed. */
    fun skipIfActiveBlock(blockId: String): Boolean {
        val state = _focusExecutionState.value
        if (state.blockId != blockId) return false
        return when (state.status) {
            FocusExecutionStatus.RUNNING,
            FocusExecutionStatus.PAUSED -> {
                _focusExecutionState.value = FocusExecutionState(status = FocusExecutionStatus.SKIPPED)
                true
            }
            else -> false
        }
    }

    fun finishFocusSession(
        scope: CoroutineScope,
        plannerService: PlannerService,
        note: String = ""
    ) {
        val state = _focusExecutionState.value
        if (
            state.blockId == null ||
            state.status !in setOf(FocusExecutionStatus.RUNNING, FocusExecutionStatus.PAUSED)
        ) {
            return
        }
        val elapsedMinutes = (focusElapsedSeconds(state) / 60)
            .coerceAtLeast(1)
            .coerceAtMost(state.plannedDurationMinutes.toLong())
            .toInt()
        scope.launch {
            val block = repository.getTimeBlockById(state.blockId) ?: return@launch
            plannerService.logActualWindow(
                blockId = block.id,
                actualStartMinute = block.startMinuteOfDay,
                actualEndMinute = (block.startMinuteOfDay + elapsedMinutes).coerceIn(0, 1440)
            )
            logActualTimeUseCase(block.toActualSegment(block.startMinuteOfDay, block.startMinuteOfDay + elapsedMinutes))
            manualMissedBlockRegistry.clearMissed(block.id, block.date)
        }
        _focusExecutionState.value = state.copy(
            status = FocusExecutionStatus.FINISHED,
            completionNote = note
        )
    }

    fun focusElapsedSeconds(state: FocusExecutionState = _focusExecutionState.value): Long {
        if (state.status == FocusExecutionStatus.IDLE || state.status == FocusExecutionStatus.SKIPPED) return 0L
        val now = System.currentTimeMillis()
        val effective = when (state.status) {
            FocusExecutionStatus.PAUSED -> (state.pausedAtEpochMs ?: now) - state.startedEpochMs - state.totalPausedMs
            else -> now - state.startedEpochMs - state.totalPausedMs
        }
        return kotlin.math.max(0L, effective / 1000)
    }

    fun focusRemainingSeconds(state: FocusExecutionState = _focusExecutionState.value): Long {
        val totalPlanned = state.plannedDurationMinutes * 60L
        return kotlin.math.max(0L, totalPlanned - focusElapsedSeconds(state))
    }

    suspend fun restoreFromPersistedSession(session: FocusSessionState) {
        val restored = buildFocusExecutionStateFromPersisted(session, repository) ?: return
        _focusExecutionState.value = restored
    }

    fun clearToIdle() {
        _focusExecutionState.value = FocusExecutionState()
    }

    fun isIdle(): Boolean = _focusExecutionState.value.status == FocusExecutionStatus.IDLE

    internal fun setFocusStateForTest(state: FocusExecutionState) {
        _focusExecutionState.value = state
    }

    private fun TimeBlock.toActualSegment(startMinute: Int, endMinute: Int): ActualTimeSegment {
        val zone = ZoneId.systemDefault()
        val normalizedStart = ((startMinute % 1440) + 1440) % 1440
        val normalizedEnd = ((endMinute % 1440) + 1440) % 1440
        val start = date.atStartOfDay(zone).plusMinutes(normalizedStart.toLong()).toInstant()
        val endDate = if (endMinute > startMinute || normalizedEnd > normalizedStart) date else date.plusDays(1)
        val end = endDate.atStartOfDay(zone).plusMinutes(normalizedEnd.toLong()).toInstant()
        return ActualTimeSegment(
            id = "actual-${id}-${Instant.now().toEpochMilli()}",
            blockId = id,
            date = date,
            startInstant = start,
            endInstant = end,
            source = ActualTimeSource.FOCUS_SESSION,
            confidence = 1f
        )
    }
}
