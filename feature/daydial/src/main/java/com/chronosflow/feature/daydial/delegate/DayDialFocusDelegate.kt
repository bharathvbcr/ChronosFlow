package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.data.focus.ManualMissedBlockRegistry
import com.chronosflow.core.domain.model.ActualTimeSegment
import com.chronosflow.core.domain.model.ActualTimeSource
import com.chronosflow.core.domain.model.FocusSessionState
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.feature.daydial.buildFocusExecutionStateFromPersisted
import com.chronosflow.feature.daydial.recoverableSessionBlockId
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.LogActualTimeUseCase
import com.chronosflow.feature.daydial.DialUtils
import com.chronosflow.feature.daydial.FocusExecutionState
import com.chronosflow.feature.daydial.FocusExecutionStatus
import com.chronosflow.feature.daydial.model.FocusPhaseKind
import com.chronosflow.feature.daydial.model.FocusPhasePlanner
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
    private val manualMissedBlockRegistry: ManualMissedBlockRegistry,
    private val splitSessionStore: FocusSplitSessionStore = FocusSplitSessionStore.None
) {
    private val _focusExecutionState = MutableStateFlow(FocusExecutionState())
    val focusExecutionState = _focusExecutionState.asStateFlow()

    /**
     * Single funnel for state changes so the persisted split snapshot stays in
     * sync: an active split is saved (to survive process death), anything else
     * clears the store.
     */
    private fun applyState(state: FocusExecutionState) {
        _focusExecutionState.value = state
        val active = state.status == FocusExecutionStatus.RUNNING ||
            state.status == FocusExecutionStatus.PAUSED
        if (state.isSplitSession && active) {
            splitSessionStore.save(state)
        } else {
            splitSessionStore.clear()
        }
    }

    /**
     * Starts a focus session. When [workMinutes] and [breakMinutes] describe a
     * valid split, the block is divided into alternating focus/break phases;
     * otherwise it runs as a single flat block (legacy behavior).
     */
    fun startFocusSession(
        scope: CoroutineScope,
        plannerService: PlannerService,
        blockId: String,
        workMinutes: Int = 0,
        breakMinutes: Int = 0
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val startedAt = System.currentTimeMillis()
            val plan = if (workMinutes > 0 && breakMinutes > 0) {
                FocusPhasePlanner.plan(block.durationMinutes, workMinutes, breakMinutes)
            } else {
                emptyList()
            }
            // A plan with a single phase adds no value over the flat path, so we
            // drop it and let the existing single-block behavior run unchanged.
            val phases = plan.takeIf { it.size > 1 }.orEmpty()
            val firstPhaseMinutes = phases.firstOrNull()?.durationMinutes ?: block.durationMinutes
            applyState(
                FocusExecutionState(
                    blockId = block.id,
                    blockTitle = block.title,
                    blockStartMinute = block.startMinuteOfDay,
                    plannedDurationMinutes = firstPhaseMinutes,
                    startedEpochMs = startedAt,
                    status = FocusExecutionStatus.RUNNING,
                    phases = phases,
                    currentPhaseIndex = 0
                )
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
        applyState(
            state.copy(
                status = FocusExecutionStatus.PAUSED,
                pausedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun resumeFocusSession() {
        val state = _focusExecutionState.value
        val pausedAt = state.pausedAtEpochMs ?: return
        if (state.status != FocusExecutionStatus.PAUSED) return
        val pauseMs = System.currentTimeMillis() - pausedAt
        applyState(
            state.copy(
                status = FocusExecutionStatus.RUNNING,
                pausedAtEpochMs = null,
                totalPausedMs = state.totalPausedMs + pauseMs
            )
        )
    }

    /**
     * Called on each timer tick of a split session. When the current phase's
     * time runs out, holds at the boundary (waiting for the user to tap
     * Continue) or, on the final phase, finishes the whole session.
     */
    fun checkPhaseBoundary(
        scope: CoroutineScope,
        plannerService: PlannerService
    ) {
        val state = _focusExecutionState.value
        if (state.status != FocusExecutionStatus.RUNNING || !state.isSplitSession) return
        if (focusRemainingSeconds(state) > 0L) return
        if (state.isLastPhase) {
            finishFocusSession(scope, plannerService)
            return
        }
        applyState(
            state.copy(
                status = FocusExecutionStatus.PAUSED,
                awaitingPhaseAdvance = true,
                pausedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    /** Tap-to-continue: starts the next phase of a split session. */
    fun advancePhase() {
        val state = _focusExecutionState.value
        if (!state.awaitingPhaseAdvance) return
        val nextIndex = state.currentPhaseIndex + 1
        val next = state.phases.getOrNull(nextIndex)
        if (next == null) {
            // No further phases — collapse the boundary into a finished hold.
            applyState(state.copy(awaitingPhaseAdvance = false))
            return
        }
        applyState(
            state.copy(
                status = FocusExecutionStatus.RUNNING,
                currentPhaseIndex = nextIndex,
                plannedDurationMinutes = next.durationMinutes,
                startedEpochMs = System.currentTimeMillis(),
                pausedAtEpochMs = null,
                totalPausedMs = 0L,
                awaitingPhaseAdvance = false
            )
        )
    }

    fun extendFocusSession(additionalMinutes: Int = 15) {
        val state = _focusExecutionState.value
        if (state.status == FocusExecutionStatus.IDLE || state.status == FocusExecutionStatus.SKIPPED) return
        applyState(state.withCurrentPhaseDuration((state.plannedDurationMinutes + additionalMinutes).coerceAtMost(480)))
    }

    fun shortenFocusSession(minutes: Int = 15) {
        val state = _focusExecutionState.value
        if (state.status == FocusExecutionStatus.IDLE || state.status == FocusExecutionStatus.SKIPPED) return
        applyState(state.withCurrentPhaseDuration((state.plannedDurationMinutes - minutes).coerceAtLeast(5)))
    }

    /**
     * Sets the current phase's duration. For a split session this also rewrites
     * the active entry in [FocusExecutionState.phases] so the timer ring (which
     * reads the phase) and the remaining-time math (which reads
     * [FocusExecutionState.plannedDurationMinutes]) stay in agreement.
     */
    private fun FocusExecutionState.withCurrentPhaseDuration(minutes: Int): FocusExecutionState {
        if (!isSplitSession) return copy(plannedDurationMinutes = minutes)
        val updatedPhases = phases.toMutableList().also { list ->
            list[currentPhaseIndex] = list[currentPhaseIndex].copy(durationMinutes = minutes)
        }
        return copy(plannedDurationMinutes = minutes, phases = updatedPhases)
    }

    fun skipFocusSession(onSkippedBlock: (String) -> Unit) {
        val blockId = _focusExecutionState.value.blockId
        applyState(FocusExecutionState(status = FocusExecutionStatus.SKIPPED))
        blockId?.let(onSkippedBlock)
    }

    /** Aligns tab UI when full-screen Focus skips the same linked block. Returns true if state changed. */
    fun skipIfActiveBlock(blockId: String): Boolean {
        val state = _focusExecutionState.value
        if (state.blockId != blockId) return false
        return when (state.status) {
            FocusExecutionStatus.RUNNING,
            FocusExecutionStatus.PAUSED -> {
                applyState(FocusExecutionState(status = FocusExecutionStatus.SKIPPED))
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
        val elapsedMinutes = consumedFocusMinutes(state)
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
        applyState(
            state.copy(
                status = FocusExecutionStatus.FINISHED,
                completionNote = note
            )
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

    /**
     * Focus minutes to credit the linked block on completion. For a split
     * session this sums the focus phases already finished plus any focus time
     * spent in the current phase; for a flat block it is just the elapsed time
     * capped at the planned duration.
     */
    private fun consumedFocusMinutes(state: FocusExecutionState): Int {
        if (!state.isSplitSession) {
            return (focusElapsedSeconds(state) / 60)
                .coerceAtLeast(1)
                .coerceAtMost(state.plannedDurationMinutes.toLong())
                .toInt()
        }
        val completedFocus = state.phases
            .take(state.currentPhaseIndex)
            .filter { it.kind == FocusPhaseKind.FOCUS }
            .sumOf { it.durationMinutes }
        val currentFocus = state.currentPhase
            ?.takeIf { it.kind == FocusPhaseKind.FOCUS }
            ?.let { phase ->
                (focusElapsedSeconds(state) / 60)
                    .coerceAtMost(phase.durationMinutes.toLong())
                    .toInt()
            } ?: 0
        return (completedFocus + currentFocus).coerceAtLeast(1)
    }

    suspend fun restoreFromPersistedSession(session: FocusSessionState) {
        // Prefer a persisted split snapshot (full phase plan) over the flat
        // service session, as long as both describe the same block.
        val splitSnapshot = splitSessionStore.load()
        if (splitSnapshot != null &&
            splitSnapshot.isSplitSession &&
            splitSnapshot.blockId == recoverableSessionBlockId(session)
        ) {
            applyState(splitSnapshot)
            return
        }
        val restored = buildFocusExecutionStateFromPersisted(session, repository) ?: return
        applyState(restored)
    }

    fun clearToIdle() {
        applyState(FocusExecutionState())
    }

    fun isIdle(): Boolean = _focusExecutionState.value.status == FocusExecutionStatus.IDLE

    internal fun setFocusStateForTest(state: FocusExecutionState) {
        applyState(state)
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
