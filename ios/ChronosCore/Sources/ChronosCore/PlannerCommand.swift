import Foundation

// PlannerCommand — pure, Foundation-only, value-typed port of `core/domain/planner/PlannerCommand.kt`.
//
// Android's PlannerCommand is a sealed interface whose execute/undo run *through a PlannerService*
// (suspend, hits Room). ChronosCore is pure and has no service, so each command here applies and
// inverts directly over an immutable `[PlannerBlock]` (the day's blocks), returning a new array —
// the same shape as a transactional reducer. The iOS app runs a command, then mirrors the resulting
// array into its SwiftData store. This keeps the whole undo/redo audit trail portable and testable.
//
// Semantics mirror Android exactly:
//  • Create:  apply = append the block;            inverse = remove it by id.
//  • Delete:  apply = remove the snapshot by id;   inverse = re-insert the snapshot.
//  • Move:    apply = set start = target;          inverse = set start = original.
//  • Resize:  apply = set duration = target;       inverse = set duration = original.
//  • ResolveConflicts: apply = force each block's start to its new value (no validation, mirrors
//    PlannerService.setBlockStart); inverse = force each back to its original — deliberately
//    recreating the prior, possibly overlapping, layout, as ONE undoable batch.
//
// Move/Resize/ResolveConflicts carry the *original* values so they invert losslessly without a
// service round-trip. Everything is `Sendable` and value-typed.

/// One block's repositioning within a `resolveConflicts` batch. Port of `BlockStartChange`.
public struct BlockStartChange: Sendable, Equatable {
    public let blockId: String
    public let originalStartMinute: Int
    public let newStartMinute: Int
    public init(blockId: String, originalStartMinute: Int, newStartMinute: Int) {
        self.blockId = blockId
        self.originalStartMinute = originalStartMinute
        self.newStartMinute = newStartMinute
    }
}

/// A value-typed, undoable planner edit. Mirrors the `PlannerCommand` sealed interface — the cases
/// are `CreateTimeBlockCommand`, `MoveTimeBlockCommand`, `ResizeTimeBlockCommand`,
/// `DeleteTimeBlockCommand`, and `ResolveConflictsCommand`. `apply`/`inverse` are pure functions of
/// the day's blocks: they return a *new* `[PlannerBlock]` and never mutate in place.
public enum PlannerCommand: Sendable, Equatable, Identifiable {
    /// Append a freshly-built block to the day.
    case create(id: String, block: PlannerBlock)
    /// Re-anchor a block's start. Carries the original start so it inverts losslessly.
    case move(id: String, blockId: String, targetStartMinute: Int, originalStartMinute: Int)
    /// Re-size a block's duration. Carries the original duration so it inverts losslessly.
    case resize(id: String, blockId: String, targetDurationMinutes: Int, originalDurationMinutes: Int)
    /// Remove a block, keeping a full snapshot so undo restores it exactly.
    case delete(id: String, blockSnapshot: PlannerBlock)
    /// A batch of conflict-repair relocations that undoes/redoes as one action.
    case resolveConflicts(id: String, changes: [BlockStartChange])

    /// Stable command id (Android's `PlannerCommand.id`). Used as the command-history key.
    public var id: String {
        switch self {
        case let .create(id, _),
             let .move(id, _, _, _),
             let .resize(id, _, _, _),
             let .delete(id, _),
             let .resolveConflicts(id, _):
            return id
        }
    }

    /// Human-readable label (Android's `PlannerCommand.label`).
    public var label: String {
        switch self {
        case .create: return "Create block"
        case .move: return "Move block"
        case .resize: return "Resize block"
        case .delete: return "Delete block"
        case .resolveConflicts: return "Repair schedule"
        }
    }

    /// Run the command forward, returning the updated day. Pure — no wall clock, no service.
    public func apply(to blocks: [PlannerBlock]) -> [PlannerBlock] {
        switch self {
        case let .create(_, block):
            return PlannerCommand.insert(block, into: blocks)
        case let .move(_, blockId, target, _):
            return PlannerCommand.setStart(blockId, to: target, in: blocks)
        case let .resize(_, blockId, target, _):
            return PlannerCommand.setDuration(blockId, to: target, in: blocks)
        case let .delete(_, snapshot):
            return PlannerCommand.remove(snapshot.id, from: blocks)
        case let .resolveConflicts(_, changes):
            return PlannerCommand.applyStarts(changes.map { ($0.blockId, $0.newStartMinute) }, in: blocks)
        }
    }

    /// Run the command backward, restoring the prior day. Inverse of `apply`.
    public func inverse(on blocks: [PlannerBlock]) -> [PlannerBlock] {
        switch self {
        case let .create(_, block):
            return PlannerCommand.remove(block.id, from: blocks)
        case let .move(_, blockId, _, original):
            return PlannerCommand.setStart(blockId, to: original, in: blocks)
        case let .resize(_, blockId, _, original):
            return PlannerCommand.setDuration(blockId, to: original, in: blocks)
        case let .delete(_, snapshot):
            return PlannerCommand.insert(snapshot, into: blocks)
        case let .resolveConflicts(_, changes):
            return PlannerCommand.applyStarts(changes.map { ($0.blockId, $0.originalStartMinute) }, in: blocks)
        }
    }

    // MARK: - Pure reducers

    /// Append unless a block with the same id already exists (idempotent re-insert on undo).
    private static func insert(_ block: PlannerBlock, into blocks: [PlannerBlock]) -> [PlannerBlock] {
        guard !blocks.contains(where: { $0.id == block.id }) else { return blocks }
        return blocks + [block]
    }

    private static func remove(_ id: String, from blocks: [PlannerBlock]) -> [PlannerBlock] {
        blocks.filter { $0.id != id }
    }

    private static func setStart(_ id: String, to start: Int, in blocks: [PlannerBlock]) -> [PlannerBlock] {
        blocks.map { block in
            guard block.id == id else { return block }
            var updated = block
            updated.startMinuteOfDay = start
            return updated
        }
    }

    private static func setDuration(_ id: String, to duration: Int, in blocks: [PlannerBlock]) -> [PlannerBlock] {
        blocks.map { block in
            guard block.id == id else { return block }
            var updated = block
            updated.durationMinutes = duration
            return updated
        }
    }

    private static func applyStarts(_ starts: [(String, Int)], in blocks: [PlannerBlock]) -> [PlannerBlock] {
        let byId = Dictionary(starts, uniquingKeysWith: { _, last in last })
        return blocks.map { block in
            guard let start = byId[block.id] else { return block }
            var updated = block
            updated.startMinuteOfDay = start
            return updated
        }
    }
}
