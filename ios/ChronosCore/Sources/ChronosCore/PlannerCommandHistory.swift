import Foundation

// PlannerCommandHistory — pure, value-typed port of `core/domain/planner/PlannerCommandHistory.kt`.
//
// Android keeps two `ArrayDeque<PlannerCommand>` (undo + redo) behind `@Synchronized` methods and
// publishes `canUndo`/`canRedo` as `StateFlow<Boolean>`. On iOS the history is a plain value type:
// the app holds it in SwiftUI `@State`, so each mutation yields a new value and the view recomputes —
// no locks, no flows, no shared mutable state. `canUndo`/`canRedo` are derived (the same
// `isNotEmpty` checks Android's `refresh()` makes).
//
// Stack discipline matches Android exactly:
//  • push      — add to undo (addLast); clear redo (a new edit forks history).
//  • popUndo   — pop the newest undo (pollLast); push it onto redo; return it (nil if empty).
//  • popRedo   — pop the newest redo (pollLast); push it back onto undo; return it (nil if empty).
//  • clear     — empty both.
//
// The struct stores commands only; it never executes them — the caller runs `command.apply`/`.inverse`
// against the day's `[PlannerBlock]`. Returning the popped command (rather than applying it here)
// mirrors `popUndo()` / `popRedo()` returning the command for the delegate to run.
public struct PlannerCommandHistory: Sendable, Equatable {
    private var undoStack: [PlannerCommand]
    private var redoStack: [PlannerCommand]

    public init() {
        self.undoStack = []
        self.redoStack = []
    }

    /// True when there is at least one command that can be undone. Mirrors `canUndo`.
    public var canUndo: Bool { !undoStack.isEmpty }

    /// True when an undone command is available to redo. Mirrors `canRedo`.
    public var canRedo: Bool { !redoStack.isEmpty }

    /// Record a freshly-executed command. Forks history by discarding any pending redo. Port of `push`.
    public mutating func push(_ command: PlannerCommand) {
        undoStack.append(command)
        redoStack.removeAll(keepingCapacity: true)
    }

    /// Move the newest command from the undo stack to the redo stack and return it (nil if empty).
    /// The caller runs the returned command's `inverse`. Port of `popUndo`.
    public mutating func popUndo() -> PlannerCommand? {
        guard let command = undoStack.popLast() else { return nil }
        redoStack.append(command)
        return command
    }

    /// Move the newest command from the redo stack back to the undo stack and return it (nil if
    /// empty). The caller re-runs the returned command's `apply`. Port of `popRedo`.
    public mutating func popRedo() -> PlannerCommand? {
        guard let command = redoStack.popLast() else { return nil }
        undoStack.append(command)
        return command
    }

    /// Drop the entire history (e.g. on a date change). Port of `clear`.
    public mutating func clear() {
        undoStack.removeAll(keepingCapacity: true)
        redoStack.removeAll(keepingCapacity: true)
    }
}
