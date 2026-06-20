package com.ChronosFlow.VBCR.core.domain.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

class PlannerCommandHistory {
    // ArrayDeque is not thread-safe. All public methods are @Synchronized so callers on
    // Dispatchers.Default (e.g. DayDialViewModel withContext blocks) cannot corrupt the deque or
    // interleave with the StateFlow assignments (TS-008).
    private val undoStack = ArrayDeque<PlannerCommand>()
    private val redoStack = ArrayDeque<PlannerCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    @Synchronized
    fun push(command: PlannerCommand) {
        undoStack.addLast(command)
        redoStack.clear()
        refresh()
    }

    @Synchronized
    fun popUndo(): PlannerCommand? {
        val command = undoStack.pollLast()
        command?.let { redoStack.addLast(it) }
        refresh()
        return command
    }

    @Synchronized
    fun popRedo(): PlannerCommand? {
        val command = redoStack.pollLast()
        command?.let { undoStack.addLast(it) }
        refresh()
        return command
    }

    @Synchronized
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        refresh()
    }

    // Called only from within @Synchronized methods — the lock already held.
    private fun refresh() {
        _canUndo.update { undoStack.isNotEmpty() }
        _canRedo.update { redoStack.isNotEmpty() }
    }
}
