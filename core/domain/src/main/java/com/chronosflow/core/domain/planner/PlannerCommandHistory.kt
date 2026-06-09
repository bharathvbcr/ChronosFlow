package com.chronosflow.core.domain.planner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

class PlannerCommandHistory {
    private val undoStack = ArrayDeque<PlannerCommand>()
    private val redoStack = ArrayDeque<PlannerCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    fun push(command: PlannerCommand) {
        undoStack.addLast(command)
        redoStack.clear()
        refresh()
    }

    fun popUndo(): PlannerCommand? {
        val command = undoStack.pollLast()
        command?.let { redoStack.addLast(it) }
        refresh()
        return command
    }

    fun popRedo(): PlannerCommand? {
        val command = redoStack.pollLast()
        command?.let { undoStack.addLast(it) }
        refresh()
        return command
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        refresh()
    }

    private fun refresh() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
