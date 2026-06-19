package com.ChronosFlow.VBCR

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.domain.model.ChronosShellSummary
import com.ChronosFlow.VBCR.core.domain.usecase.ObserveChronosShellSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

typealias ChronosShellState = ChronosShellSummary

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChronosShellViewModel @Inject constructor(
    observeChronosShellSummary: ObserveChronosShellSummaryUseCase
) : ViewModel() {
    // Re-read the date every tick so the shell rolls over at midnight instead
    // of serving yesterday's summary until process restart.
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()
    private val minuteTicker = flow {
        while (true) {
            emit(LocalTime.now().hour * 60 + LocalTime.now().minute)
            delay(60_000)
        }
    }

    val state: StateFlow<ChronosShellState> = today.flatMapLatest { date ->
        observeChronosShellSummary(
            date = date,
            currentMinuteOfDay = minuteTicker
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChronosShellState()
    )
}
