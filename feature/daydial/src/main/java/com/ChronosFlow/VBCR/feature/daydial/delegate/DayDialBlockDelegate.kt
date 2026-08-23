package com.ChronosFlow.VBCR.feature.daydial.delegate

import com.ChronosFlow.VBCR.core.domain.model.BlockFlexibility
import com.ChronosFlow.VBCR.core.domain.model.BlockProvenance
import com.ChronosFlow.VBCR.core.domain.model.EnergyIntensity
import com.ChronosFlow.VBCR.core.domain.model.SleepSchedule
import com.ChronosFlow.VBCR.core.domain.model.TimeBlock
import com.ChronosFlow.VBCR.core.domain.planner.BlockStartChange
import com.ChronosFlow.VBCR.core.domain.planner.ConflictResolutionResult
import com.ChronosFlow.VBCR.core.domain.planner.CreateTimeBlockCommand
import com.ChronosFlow.VBCR.core.domain.planner.DeleteTimeBlockCommand
import com.ChronosFlow.VBCR.core.domain.planner.FreeTimeCalculator
import com.ChronosFlow.VBCR.core.domain.planner.MoveTimeBlockCommand
import com.ChronosFlow.VBCR.core.domain.planner.PlannerCommand
import com.ChronosFlow.VBCR.core.domain.planner.PlannerCommandHistory
import com.ChronosFlow.VBCR.core.domain.planner.PlannerDataUnavailableException
import com.ChronosFlow.VBCR.core.domain.planner.PlannerOperationResult
import com.ChronosFlow.VBCR.core.domain.planner.PlannerService
import com.ChronosFlow.VBCR.core.domain.planner.ResolveConflictsCommand
import com.ChronosFlow.VBCR.core.domain.planner.conflictRepairManualMessage
import com.ChronosFlow.VBCR.core.domain.planner.conflictRepairSummaryMessage
import com.ChronosFlow.VBCR.core.domain.repository.CalendarEventRepository
import com.ChronosFlow.VBCR.core.domain.repository.SleepScheduleRepository
import com.ChronosFlow.VBCR.core.domain.repository.TimeBlockRepository
import com.ChronosFlow.VBCR.core.domain.usecase.MoveBlockUseCase
import com.ChronosFlow.VBCR.core.domain.usecase.ResizeBlockUseCase
import com.ChronosFlow.VBCR.feature.daydial.DialUtils
import com.ChronosFlow.VBCR.feature.daydial.dial.ChronosDialInteractionEngine
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
                // Conflict or missing preview: snap block back silently. The preview already
                // surfaced the conflict indicator; no commit callback is issued.
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
            val nextDuration = durationMinutes.coerceIn(10, (1440 - nextStart).coerceAtLeast(10))
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
            val normalizedDuration = finalDurationMinutes.coerceIn(10, (1440 - normalizedStart).coerceAtLeast(10))
            val result = sleepWindowResult(block.id, normalizedStart, normalizedDuration)
                ?: resizeBlockUseCase(block.id, normalizedStart, normalizedDuration)
            onResult(result, false)
            if (result is PlannerOperationResult.Applied) {
                syncLinkedCalendarExport(block.id)
            }
        }
    }

    fun onBlockDragCancelled() {
        val blockId = _dragPreview.value?.blockId
        _dragPreview.value = null
        if (blockId != null) activeDragStartMinuteByBlock.remove(blockId)
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
            // A copy at the source's exact start always overlaps the source, so placement
            // validation would reject it and nothing would appear. Drop the copy into open time,
            // shrinking it to fit the nearest partial gap when nothing fits the full block, so a
            // duplicate only fails on a day with literally no free time.
            val dayBlocks = repository.getTimeBlocksByDate(source.date).first()
            val placement = nextDuplicatePlacement(dayBlocks, source)
            if (placement == null) {
                onResult(
                    PlannerOperationResult.Rejected("No free time to duplicate this block", blockId),
                    false
                )
                return@launch
            }
            // The copy is a fresh, standalone user block: drop every instance-identity link
            // (task/habit/medication/recurrence/routine/calendar + actuals) so it isn't a phantom
            // second instance of the same scheduled thing, and normalize provenance/source.
            // It must also be creatable — createBlock rejects locked blocks and validatePlacement
            // rejects FIXED ones — so unlock it and downgrade FIXED to MOVABLE.
            val duplicate = source.copy(
                id = UUID.randomUUID().toString(),
                title = "${source.title} (Copy)",
                startMinuteOfDay = placement.startMinute,
                durationMinutes = placement.durationMinutes,
                provenance = BlockProvenance.USER_CREATED,
                source = "USER",
                flexibility = if (source.flexibility == BlockFlexibility.FIXED) {
                    BlockFlexibility.MOVABLE
                } else {
                    source.flexibility
                },
                isLocked = false,
                taskId = null,
                taskOccurrenceDate = null,
                calendarEventId = null,
                medicationPlanId = null,
                habitId = null,
                recurrenceRuleId = null,
                routineId = null,
                actualStartMinuteOfDay = null,
                actualEndMinuteOfDay = null,
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
            executeCommand(
                command = command,
                onResult = { result, snapped ->
                    onResult(result, snapped)
                    if (result is PlannerOperationResult.Applied) onDeleted(blockId)
                }
            )
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
            val blocksToApply = sourceBlocks.filter { sourceBlock ->
                sleepWindowResult(sourceBlock.id, sourceBlock.startMinuteOfDay, sourceBlock.durationMinutes) == null
            }
            if (blocksToApply.isEmpty()) {
                onResult(PlannerOperationResult.Rejected(SLEEP_SCHEDULE_MESSAGE, ""), false)
                return@launch
            }

            blocksToApply.forEach { sourceBlock ->
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
        fromMinute: Int? = null,
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
            val result = plannerService.rebalanceDay(date, fromMinute)
            onResult(result, false)
        }
    }

    /**
     * Deterministically repairs overlapping blocks via [PlannerService.resolveConflicts], pushing the
     * batch of relocations as ONE undoable [ResolveConflictsCommand]. Surfaces a summary (or a
     * "needs a manual change" message when only immovable blocks overlap) through [onResult], and
     * hands the full [ConflictResolutionResult] to [onResolution] so callers can decide whether to
     * fall back to AI for any unresolved leftovers.
     */
    fun resolveConflicts(
        scope: CoroutineScope,
        date: LocalDate,
        fromMinute: Int? = null,
        onResult: (PlannerOperationResult, Boolean) -> Unit,
        onResolution: (ConflictResolutionResult) -> Unit = {}
    ) {
        scope.launch {
            // Unlike rebalanceDay (which refuses to run while sleep is on), conflict repair AVOIDS the
            // active sleep window — pass it through so relocations never land inside it.
            val sleepSchedule = currentSleepSchedule().takeIf { it.isActive }
            // resolveConflicts throws PlannerDataUnavailableException when the day's blocks cannot be
            // loaded; report that as a rejection rather than letting the coroutine die — a fabricated
            // ConflictResolutionResult(hadConflicts=false) would falsely claim the day is clean.
            val resolution = try {
                plannerService.resolveConflicts(date, fromMinute ?: 0, sleepSchedule)
            } catch (e: PlannerDataUnavailableException) {
                onResult(PlannerOperationResult.Rejected("Couldn't load your schedule — try again", date.toString()), false)
                return@launch
            }
            if (resolution.moves.isNotEmpty()) {
                commandHistory.push(
                    ResolveConflictsCommand(
                        id = UUID.randomUUID().toString(),
                        changes = resolution.moves.map {
                            BlockStartChange(it.blockId, it.originalStartMinute, it.newStartMinute)
                        }
                    )
                )
                onResult(
                    PlannerOperationResult.Applied(
                        conflictRepairSummaryMessage(resolution),
                        resolution.moves.first().blockId,
                        null,
                        resolution.moves.map { it.blockId }
                    ),
                    false
                )
            } else if (resolution.hadConflicts) {
                onResult(
                    PlannerOperationResult.Rejected(conflictRepairManualMessage(resolution), date.toString()),
                    false
                )
            }
            onResolution(resolution)
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

    /** Start minute and (possibly shrunk) duration for a duplicate, or null if there is no room. */
    private data class DuplicatePlacement(val startMinute: Int, val durationMinutes: Int)

    /**
     * Decides where a duplicate of [source] lands so it sits in open time instead of on top of the
     * original. In order of preference: the first free gap at/after the original's end that fits the
     * full block, then the earliest gap anywhere that fits the full block, then the largest gap with
     * the copy shrunk to fit it. Returns null only when no usable free time remains.
     */
    private fun nextDuplicatePlacement(dayBlocks: List<TimeBlock>, source: TimeBlock): DuplicatePlacement? {
        val duration = source.durationMinutes
        val preferredStart = source.startMinuteOfDay + duration
        val freeSegments = freeTimeCalculator.calculate(dayBlocks)
        if (freeSegments.isEmpty()) return null

        freeSegments
            .filter { it.endMinute - maxOf(it.startMinute, preferredStart) >= duration }
            .minByOrNull { it.startMinute }
            ?.let { return DuplicatePlacement(maxOf(it.startMinute, preferredStart), duration) }

        freeSegments
            .filter { it.endMinute - it.startMinute >= duration }
            .minByOrNull { it.startMinute }
            ?.let { return DuplicatePlacement(it.startMinute, duration) }

        val largest = freeSegments.maxByOrNull { it.endMinute - it.startMinute } ?: return null
        val gapSize = largest.endMinute - largest.startMinute
        return if (gapSize >= MIN_DUPLICATE_GAP_MINUTES) {
            DuplicatePlacement(largest.startMinute, gapSize.coerceAtMost(duration))
        } else {
            null
        }
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

        // Below this, a leftover gap is too small to be a useful duplicate, so we report no room
        // instead of dropping in a sliver of a block.
        const val MIN_DUPLICATE_GAP_MINUTES = 15
    }

    private suspend fun syncLinkedCalendarExport(blockId: String) {
        val updatedBlock = repository.getTimeBlockById(blockId) ?: return
        // Imported blocks link to the user's original device event, not to an
        // export we own — never write back to it.
        if (updatedBlock.provenance == BlockProvenance.CALENDAR_IMPORTED) {
            return
        }
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
