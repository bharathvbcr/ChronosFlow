package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal class DayDialCoordinatorState {
    private val selectedDateState = MutableStateFlow(LocalDate.now())
    val selectedDate = selectedDateState.asStateFlow()

    private val selectedBlockIdState = MutableStateFlow<String?>(null)
    val selectedBlockId = selectedBlockIdState.asStateFlow()

    private val hapticCueState = MutableStateFlow(PlannerHapticCue.NONE)
    val hapticCue = hapticCueState.asStateFlow()

    private val compactModeState = MutableStateFlow(false)
    val compactMode = compactModeState.asStateFlow()

    private val compactWindowStartState = MutableStateFlow(LocalTime.now().hour * 60)
    val compactWindowStart = compactWindowStartState.asStateFlow()

    private val lastResultState = MutableStateFlow<PlannerOperationResult?>(null)
    val lastResult = lastResultState.asStateFlow()

    private val currentMinuteState = MutableStateFlow(currentMinuteOfDay())
    val currentMinute = currentMinuteState.asStateFlow()

    /**
     * The real-world current date, refreshed by the same per-minute ticker as [currentMinute].
     * Distinct only when the day actually rolls over, so collectors (e.g. the Insights trend window)
     * advance to the new day while the app stays open without re-emitting every minute.
     */
    private val currentDateState = MutableStateFlow(LocalDate.now())
    val currentDate = currentDateState.asStateFlow()

    val selectedDateValue: LocalDate
        get() = selectedDateState.value

    val selectedBlockIdValue: String?
        get() = selectedBlockIdState.value

    val currentMinuteValue: Int
        get() = currentMinuteState.value

    /** True when the selected day is the real-world today, so "now"-relative actions apply. */
    val isViewingToday: Boolean
        get() = selectedDateState.value == LocalDate.now()

    fun refreshCurrentMinute() {
        currentMinuteState.value = currentMinuteOfDay()
        currentDateState.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate) {
        selectedDateState.value = date
    }

    fun selectBlock(blockId: String?) {
        selectedBlockIdState.value = blockId
    }

    fun clearSelectedBlockIf(blockId: String) {
        if (selectedBlockIdState.value == blockId) {
            selectedBlockIdState.value = null
        }
    }

    fun moveToPreviousDate() {
        selectedDateState.value = selectedDateState.value.minusDays(1)
    }

    fun moveToNextDate() {
        selectedDateState.value = selectedDateState.value.plusDays(1)
    }

    fun onCompactModeToggled(selectedBlockStartMinute: Int?) {
        compactModeState.value = !compactModeState.value
        compactWindowStartState.value = if (compactModeState.value) {
            val anchorMinute = selectedBlockStartMinute ?: currentMinuteOfDay()
            compactWindowStartCenteredOn(anchorMinute)
        } else {
            0
        }
    }

    // Half-window steps keep six hours of context visible across each move.
    fun moveWindowBack() {
        if (compactModeState.value) {
            compactWindowStartState.value = (compactWindowStartState.value - 360 + 1440) % 1440
        }
    }

    fun moveWindowForward() {
        if (compactModeState.value) {
            compactWindowStartState.value = (compactWindowStartState.value + 360) % 1440
        }
    }

    fun centerWindowOnNow() {
        if (compactModeState.value) {
            compactWindowStartState.value = compactWindowStartCenteredOn(currentMinuteOfDay())
        }
    }

    fun clearHapticCue() {
        hapticCueState.value = PlannerHapticCue.NONE
    }

    fun handlePlannerResult(result: PlannerOperationResult, forceSuccessfulDrop: Boolean) {
        lastResultState.value = result
        hapticCueState.value = if (forceSuccessfulDrop) {
            PlannerHapticCue.SUCCESSFUL_DROP
        } else {
            hapticCueFor(result)
        }
    }

    private fun hapticCueFor(result: PlannerOperationResult): PlannerHapticCue = when (result) {
        is PlannerOperationResult.Conflict -> PlannerHapticCue.CONFLICT_BOUNDARY
        is PlannerOperationResult.Locked -> PlannerHapticCue.LOCKED_COLLISION
        is PlannerOperationResult.Applied -> {
            if (result.snappedToMinute != null) PlannerHapticCue.SNAP else PlannerHapticCue.SUCCESSFUL_DROP
        }
        else -> PlannerHapticCue.NONE
    }
}

private fun currentMinuteOfDay(): Int {
    val now = LocalTime.now(ZoneId.systemDefault())
    return now.hour * 60 + now.minute
}

/** Window start that places the anchor mid-window, snapped to the hour so ticks stay round. */
internal fun compactWindowStartCenteredOn(anchorMinute: Int): Int {
    val centered = (anchorMinute - 360 + 1440) % 1440
    return (centered / 60) * 60
}

internal fun nextMinuteBoundaryDelayMillis(): Long {
    val now = LocalTime.now(ZoneId.systemDefault())
    return (60_000L - now.second * 1_000L - now.nano / 1_000_000L).coerceAtLeast(250L)
}
