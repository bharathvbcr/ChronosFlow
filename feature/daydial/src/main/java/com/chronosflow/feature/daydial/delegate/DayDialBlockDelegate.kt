package com.chronosflow.feature.daydial.delegate

import com.chronosflow.core.domain.model.BlockFlexibility
import com.chronosflow.core.domain.model.BlockProvenance
import com.chronosflow.core.domain.model.EnergyIntensity
import com.chronosflow.core.domain.model.SleepSchedule
import com.chronosflow.core.domain.model.TimeBlock
import com.chronosflow.core.domain.planner.CreateTimeBlockCommand
import com.chronosflow.core.domain.planner.DeleteTimeBlockCommand
import com.chronosflow.core.domain.planner.FreeTimeCalculator
import com.chronosflow.core.domain.planner.MoveTimeBlockCommand
import com.chronosflow.core.domain.planner.PlannerCommand
import com.chronosflow.core.domain.planner.PlannerCommandHistory
import com.chronosflow.core.domain.planner.PlannerOperationResult
import com.chronosflow.core.domain.planner.PlannerService
import com.chronosflow.core.domain.repository.CalendarEventRepository
import com.chronosflow.core.domain.repository.SleepScheduleRepository
import com.chronosflow.core.domain.repository.TimeBlockRepository
import com.chronosflow.core.domain.usecase.MoveBlockUseCase
import com.chronosflow.core.domain.usecase.ResizeBlockUseCase
import com.chronosflow.feature.daydial.DialUtils
import com.chronosflow.feature.daydial.dial.ChronosDialInteractionEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DayDialDragPreview(
    val blockId: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val result: PlannerOperationResult
)

class DayDialBlockDelegate @Inject constructor(
    private val repository: TimeBlockRepository,
    private val sleepScheduleRepository: SleepScheduleRepository,
    private val moveBlockUseCase: MoveBlockUseCase,
    private val resizeBlockUseCase: ResizeBlockUseCase,
    private val calendarEventRepository: CalendarEventRepository,
    private val plannerService: PlannerService = PlannerService(repository),
    private val freeTimeCalculator: FreeTimeCalculator = FreeTimeCalculator(),
    private val interactionEngine: ChronosDialInteractionEngine = ChronosDialInteractionEngine()
) {
    private val commandHistory = PlannerCommandHistory()
    private val activeDragStartMinuteByBlock = HashMap<String, Int>()
    private val _dragPreview = MutableStateFlow<DayDialDragPreview?>(null)

    val dragPreview = _dragPreview.asStateFlow()
    val canUndo = commandHistory.canUndo
    val canRedo = commandHistory.canRedo

    fun onBlockMoved(
        scope: CoroutineScope,
        blockId: String,
        newStartMinute: Int,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            sleepWindowResult(block.id, newStartMinute, block.durationMinutes)?.let {
                onResult(it, false)
                return@launch
            }
            val command = MoveTimeBlockCommand(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                targetStartMinute = newStartMinute,
                originalStartMinute = block.startMinuteOfDay
            )
            executeCommand(command, onResult, markAsFinalMove = true)
        }
    }

    fun onBlockDragStarted(blockId: String, startMinute: Int) {
        activeDragStartMinuteByBlock[blockId] = startMinute
    }

    fun onBlockDragMoved(
        scope: CoroutineScope,
        blockId: String,
        newStartMinute: Int,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val snappedMinute = interactionEngine.snapMinute(newStartMinute)
            val result = sleepWindowResult(block.id, snappedMinute, block.durationMinutes)
                ?: plannerService.previewMove(block.id, snappedMinute)
            val preview = interactionEngine.previewMove(
                blockId = block.id,
                blockStartMinute = block.startMinuteOfDay,
                blockDurationMinutes = block.durationMinutes,
                dragStartMinute = activeDragStartMinuteByBlock[block.id] ?: block.startMinuteOfDay,
                currentMinute = snappedMinute,
                isLocked = block.isLocked,
                validation = result
            )
            _dragPreview.value = DayDialDragPreview(
                blockId = preview.blockId,
                startMinute = preview.startMinute,
                durationMinutes = preview.durationMinutes,
                result = result
            )
            onResult(result, false)
            if (block.startMinuteOfDay == snappedMinute) {
                return@launch
            }
            if (result !is PlannerOperationResult.Applied) {
                if (activeDragStartMinuteByBlock[block.id] == null) {
                    activeDragStartMinuteByBlock[block.id] = block.startMinuteOfDay
                }
            }
        }
    }

    fun onBlockMoveCommitted(
        scope: CoroutineScope,
        blockId: String,
        finalStartMinute: Int,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val preview = _dragPreview.value?.takeIf { it.blockId == blockId }
            _dragPreview.value = null
            if (preview?.result !is PlannerOperationResult.Applied) {
                activeDragStartMinuteByBlock.remove(blockId)
                return@launch
            }
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val originalStartMinute = activeDragStartMinuteByBlock.remove(block.id) ?: block.startMinuteOfDay
            if (block.startMinuteOfDay == finalStartMinute && originalStartMinute == finalStartMinute) {
                return@launch
            }
            val commit = interactionEngine.commitMove(
                blockId = block.id,
                snappedMinute = finalStartMinute,
                durationMinutes = block.durationMinutes
            )
            val snappedFinalStart = interactionEngine.snapMinute(commit.startMinute)
            val result = sleepWindowResult(commit.blockId, snappedFinalStart, block.durationMinutes)
                ?: moveBlockUseCase(commit.blockId, snappedFinalStart)
            onResult(result, false)
            if (result is PlannerOperationResult.Applied) {
                val command = MoveTimeBlockCommand(
                    id = UUID.randomUUID().toString(),
                    blockId = block.id,
                    targetStartMinute = snappedFinalStart,
                    originalStartMinute = originalStartMinute
                )
                commandHistory.push(command)
                syncLinkedCalendarExport(commit.blockId)
            }
        }
    }

    fun onBlockResize(
        scope: CoroutineScope,
        blockId: String,
        startMinute: Int,
        durationMinutes: Int,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val nextStart = DialUtils.snapToIncrement(startMinute).coerceIn(0, 1439)
            val nextDuration = durationMinutes.coerceIn(10, 240)
            val result = sleepWindowResult(block.id, nextStart, nextDuration)
                ?: plannerService.previewResizeWindow(block.id, nextStart, nextDuration)
            if (result is PlannerOperationResult.Applied) {
                _dragPreview.value = DayDialDragPreview(
                    blockId = block.id,
                    startMinute = nextStart,
                    durationMinutes = nextDuration,
                    result = result
                )
            }
            onResult(result, false)
        }
    }

    fun onBlockResizeCommitted(
        scope: CoroutineScope,
        blockId: String,
        finalStartMinute: Int,
        finalDurationMinutes: Int,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val preview = _dragPreview.value?.takeIf { it.blockId == blockId }
            _dragPreview.value = null
            if (preview?.result !is PlannerOperationResult.Applied) {
                return@launch
            }
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            val normalizedStart = DialUtils.snapToIncrement(finalStartMinute).coerceIn(0, 1439)
            val normalizedDuration = finalDurationMinutes.coerceIn(10, 240)
            val result = sleepWindowResult(block.id, normalizedStart, normalizedDuration)
                ?: resizeBlockUseCase(block.id, normalizedStart, normalizedDuration)
            onResult(result, false)
            if (result is PlannerOperationResult.Applied) {
                syncLinkedCalendarExport(block.id)
            }
        }
    }

    fun onBlockDragCancelled() {
        _dragPreview.value = null
    }

    fun createQuickBlock(
        scope: CoroutineScope,
        date: LocalDate,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val block = buildUserTimeBlock(
                date = date,
                title = title,
                startMinute = startMinute,
                durationMinutes = durationMinutes,
                category = category
            )
            sleepWindowResult(block.id, block.startMinuteOfDay, block.durationMinutes)?.let {
                onResult(it, false)
                return@launch
            }
            val command = CreateTimeBlockCommand(id = UUID.randomUUID().toString(), block = block)
            executeCommand(command, onResult)
        }
    }

    fun duplicateBlock(
        scope: CoroutineScope,
        blockId: String,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val source = repository.getTimeBlockById(blockId) ?: return@launch
            val duplicate = source.copy(
                id = UUID.randomUUID().toString(),
                title = "${source.title} (Copy)",
                calendarEventId = null,
                updatedAt = Instant.now(),
                createdAt = Instant.now()
            )
            sleepWindowResult(duplicate.id, duplicate.startMinuteOfDay, duplicate.durationMinutes)?.let {
                onResult(it, false)
                return@launch
            }
            val command = CreateTimeBlockCommand(
                id = UUID.randomUUID().toString(),
                block = duplicate
            )
            executeCommand(command, onResult)
        }
    }

    fun deleteBlock(
        scope: CoroutineScope,
        blockId: String,
        onResult: (PlannerOperationResult, Boolean) -> Unit,
        onDeleted: (String) -> Unit
    ) {
        scope.launch {
            val block = repository.getTimeBlockById(blockId) ?: return@launch
            deleteExportedCalendarEventIfNeeded(block)
            val command = DeleteTimeBlockCommand(
                id = UUID.randomUUID().toString(),
                blockId = block.id,
                blockSnapshot = block
            )
            executeCommand(command, onResult)
            onDeleted(blockId)
        }
    }

    fun clearCurrentDay(scope: CoroutineScope, date: LocalDate) {
        scope.launch {
            repository.getTimeBlocksByDate(date).first()
                .forEach { block -> deleteExportedCalendarEventIfNeeded(block) }
            repository.clearDay(date)
        }
    }

    fun copyPlanFromPreviousDay(
        scope: CoroutineScope,
        date: LocalDate,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val previousDate = date.minusDays(1)
            val sourceBlocks = repository.getTimeBlocksByDate(previousDate).first()
            if (sourceBlocks.isEmpty()) {
                return@launch
            }
            if (sourceBlocks.any { sourceBlock ->
                    sleepWindowResult(sourceBlock.id, sourceBlock.startMinuteOfDay, sourceBlock.durationMinutes) != null
                }
            ) {
                onResult(PlannerOperationResult.Rejected(SLEEP_SCHEDULE_MESSAGE, ""), false)
                return@launch
            }

            sourceBlocks.forEach { sourceBlock ->
                val copied = sourceBlock.copy(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    calendarEventId = null,
                    actualStartMinuteOfDay = null,
                    actualEndMinuteOfDay = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                val command = CreateTimeBlockCommand(
                    id = UUID.randomUUID().toString(),
                    block = copied
                )
                executeCommand(command, onResult)
            }
        }
    }

    fun fillEmptyTime(
        scope: CoroutineScope,
        date: LocalDate,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val blocks = repository.getTimeBlocksByDate(date).first()
            val gap = freeTimeCalculator.calculate(blocks).firstOrNull { candidate ->
                val plannedDuration = (candidate.endMinute - candidate.startMinute).coerceAtLeast(1).coerceAtMost(30)
                sleepWindowResult("", candidate.startMinute, plannedDuration) == null
            }
            if (gap != null) {
                val duration = (gap.endMinute - gap.startMinute).coerceAtLeast(1)
                val planned = duration.coerceAtMost(30)
                val command = CreateTimeBlockCommand(
                    id = UUID.randomUUID().toString(),
                    block = buildUserTimeBlock(
                        date = date,
                        title = "Recovered Focus",
                        startMinute = gap.startMinute,
                        durationMinutes = planned,
                        category = "RECOVERY"
                    )
                )
                executeCommand(command, onResult)
            } else {
                onResult(
                    PlannerOperationResult.Rejected("No daytime gap is available outside your sleep schedule", ""),
                    false
                )
            }
        }
    }

    fun updateBlockTitle(scope: CoroutineScope, blockId: String, title: String) {
        scope.launch {
            val source = repository.getTimeBlockById(blockId) ?: return@launch
            repository.saveTimeBlock(source.copy(title = title, updatedAt = Instant.now()))
            syncLinkedCalendarExport(blockId)
        }
    }

    fun updateBlockDetails(
        scope: CoroutineScope,
        blockId: String,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String,
        isLocked: Boolean,
        isProtected: Boolean,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            val source = repository.getTimeBlockById(blockId) ?: return@launch
            val normalizedStart = DialUtils.snapToIncrement(startMinute).coerceIn(0, 1439)
            val normalizedDuration = durationMinutes.coerceIn(5, 240)
            sleepWindowResult(blockId, normalizedStart, normalizedDuration)?.let {
                onResult(it, false)
                return@launch
            }
            val normalizedCategory = category.ifBlank { source.category }.uppercase(Locale.getDefault())
            repository.saveTimeBlock(
                source.copy(
                    title = title.ifBlank { source.title },
                    category = normalizedCategory,
                    startMinuteOfDay = normalizedStart,
                    durationMinutes = normalizedDuration,
                    isLocked = isLocked,
                    isProtected = isProtected,
                    updatedAt = Instant.now()
                )
            )
            syncLinkedCalendarExport(blockId)
            onResult(PlannerOperationResult.Applied("Block updated", blockId, null), false)
        }
    }

    fun rebalanceDay(
        scope: CoroutineScope,
        date: LocalDate,
        onResult: (PlannerOperationResult, Boolean) -> Unit
    ) {
        scope.launch {
            if (currentSleepSchedule().isActive) {
                onResult(
                    PlannerOperationResult.Rejected("Disable the sleep schedule to rebalance the day", date.toString()),
                    false
                )
                return@launch
            }
            val result = plannerService.rebalanceDay(date)
            onResult(result, false)
        }
    }

    fun undo(scope: CoroutineScope, onResult: (PlannerOperationResult, Boolean) -> Unit) {
        scope.launch {
            val command = commandHistory.popUndo() ?: return@launch
            val result = command.undo(plannerService)
            onResult(result, false)
        }
    }

    fun redo(scope: CoroutineScope, onResult: (PlannerOperationResult, Boolean) -> Unit) {
        scope.launch {
            val command = commandHistory.popRedo() ?: return@launch
            val result = command.execute(plannerService)
            onResult(result, false)
        }
    }

    private suspend fun executeCommand(
        command: PlannerCommand,
        onResult: (PlannerOperationResult, Boolean) -> Unit,
        markAsFinalMove: Boolean = false
    ) {
        val result = command.execute(plannerService)
        onResult(result, markAsFinalMove && result is PlannerOperationResult.Applied && result.snappedToMinute != null)
        if (result is PlannerOperationResult.Applied) {
            commandHistory.push(command)
        }
    }

    private fun buildUserTimeBlock(
        date: LocalDate,
        title: String,
        startMinute: Int,
        durationMinutes: Int,
        category: String
    ): TimeBlock {
        val snappedStart = DialUtils.snapToIncrement(startMinute)
        val snappedDuration = durationMinutes.coerceIn(5, 240)
        val categoryNormalized = category.uppercase(Locale.getDefault())
        return TimeBlock(
            id = UUID.randomUUID().toString(),
            date = date,
            title = title,
            category = categoryNormalized,
            startMinuteOfDay = snappedStart,
            durationMinutes = snappedDuration,
            timezone = ZoneId.systemDefault().id,
            provenance = BlockProvenance.USER_CREATED,
            flexibility = if (categoryNormalized == "MEETING") BlockFlexibility.MOVABLE else BlockFlexibility.RESIZABLE,
            energyLevel = EnergyIntensity.MODERATE,
            source = "USER",
            taskId = null,
            calendarEventId = null,
            medicationPlanId = null,
            habitId = null,
            isLocked = false,
            isProtected = false,
            recurrenceRuleId = null,
            actualStartMinuteOfDay = null,
            actualEndMinuteOfDay = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun currentSleepSchedule(): SleepSchedule = sleepScheduleRepository.getSleepSchedule()

    private fun sleepWindowResult(
        blockId: String,
        startMinute: Int,
        durationMinutes: Int
    ): PlannerOperationResult.Rejected? {
        val sleepSchedule = currentSleepSchedule()
        return if (sleepSchedule.intersects(startMinute, durationMinutes)) {
            PlannerOperationResult.Rejected(SLEEP_SCHEDULE_MESSAGE, blockId)
        } else {
            null
        }
    }

    private companion object {
        const val SLEEP_SCHEDULE_MESSAGE = "This time overlaps your sleep schedule"
    }

    private suspend fun syncLinkedCalendarExport(blockId: String) {
        val updatedBlock = repository.getTimeBlockById(blockId) ?: return
        if (updatedBlock.calendarEventId != null) {
            calendarEventRepository.updateExportedTimeBlock(updatedBlock)
        }
    }

    private suspend fun deleteExportedCalendarEventIfNeeded(block: TimeBlock) {
        val calendarEventId = block.calendarEventId ?: return
        if (block.provenance == BlockProvenance.CALENDAR_IMPORTED) {
            return
        }
        calendarEventRepository.deleteExportedTimeBlock(calendarEventId)
    }
}
