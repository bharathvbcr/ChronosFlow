package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.FreeTimeCalculator
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialDragPreview
import com.ChronosFlow.VBCR.feature.daydial.delegate.DayDialReviewDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

internal class DayDialStateFlows(
    val selectedFocusBlock: StateFlow<TimeBlockUiModel?>,
    val timeBlocks: StateFlow<List<TimeBlockUiModel>>,
    val timeBlocksDomain: StateFlow<List<TimeBlock>>,
    val freeTime: StateFlow<List<TimeRangeUi>>,
    val selectedBlock: StateFlow<TimeBlockUiModel?>,
    val dailyReview: StateFlow<DailyReview>
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun buildDayDialStateFlows(
    scope: CoroutineScope,
    repository: TimeBlockRepository,
    selectedDate: StateFlow<LocalDate>,
    selectedBlockId: StateFlow<String?>,
    focusExecutionState: StateFlow<FocusExecutionState>,
    dragPreview: StateFlow<DayDialDragPreview?>,
    reviewDelegate: DayDialReviewDelegate,
    freeTimeCalculator: FreeTimeCalculator
): DayDialStateFlows {
    val selectedFocusBlock = focusExecutionState.flatMapLatest { state ->
        repository.getTimeBlocksByDate(selectedDate.value)
            .map { blocks -> blocks.find { it.id == state.blockId } }
    }.map { block ->
        block?.toDayDialUiModel(isSelected = true)
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val timeBlocks = selectedDate.flatMapLatest { date ->
        repository.getTimeBlocksByDate(date)
    }.combine(dragPreview) { blocks, preview ->
        blocks.map { block ->
            val previewed = if (preview?.blockId == block.id) {
                block.copy(
                    startMinuteOfDay = preview.startMinute,
                    durationMinutes = preview.durationMinutes
                )
            } else {
                block
            }
            previewed.toDayDialUiModel()
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeBlocksDomain = selectedDate.flatMapLatest { date ->
        repository.getTimeBlocksByDate(date)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val freeTime = selectedDate.flatMapLatest { date ->
        repository.getTimeBlocksByDate(date)
    }.map { blocks ->
        freeTimeCalculator.calculate(blocks).map {
            TimeRangeUi(startMinute = it.startMinute, endMinute = it.endMinute)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedBlock = selectedBlockId.flatMapLatest { id ->
        timeBlocksDomain.map { blocks -> blocks.find { it.id == id } }
    }.map { domainBlock ->
        domainBlock?.let { it.toDayDialUiModel(isSelected = true) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    val dailyReview = reviewDelegate.dailyReview(scope, selectedDate, timeBlocksDomain)

    return DayDialStateFlows(
        selectedFocusBlock = selectedFocusBlock,
        timeBlocks = timeBlocks,
        timeBlocksDomain = timeBlocksDomain,
        freeTime = freeTime,
        selectedBlock = selectedBlock,
        dailyReview = dailyReview
    )
}
