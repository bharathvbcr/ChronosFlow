package com.chronosflow.feature.daydial

import com.chronosflow.core.domain.planner.PlannerOperationResult
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
            selectedBlockStartMinute ?: LocalTime.now(ZoneId.systemDefault()).let { it.hour * 60 }
        } else {
            0
        }
    }

    fun moveWindowBack() {
        if (compactModeState.value) {
            compactWindowStartState.value = (compactWindowStartState.value - 720 + 1440) % 1440
        }
    }

    fun moveWindowForward() {
        if (compactModeState.value) {
            compactWindowStartState.value = (compactWindowStartState.value + 720) % 1440
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

internal fun nextMinuteBoundaryDelayMillis(): Long {
    val now = LocalTime.now(ZoneId.systemDefault())
    return (60_000L - now.second * 1_000L - now.nano / 1_000_000L).coerceAtLeast(250L)
}
