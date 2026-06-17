import Foundation

// Conflict resolution — port of the "repair an overloaded schedule" planner intent. Given the day's
// blocks, propose moving each *movable* block that overlaps an earlier block into the nearest free
// window large enough to hold it, without creating new overlaps. Fixed/locked blocks never move.

public struct ResolvableBlock: Sendable, Equatable {
    public let id: String
    public let startMinute: Int
    public let durationMinutes: Int
    public let isMovable: Bool
    public init(id: String, startMinute: Int, durationMinutes: Int, isMovable: Bool) {
        self.id = id
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.isMovable = isMovable
    }
    var endMinute: Int { startMinute + durationMinutes }
}

public struct MoveSuggestion: Sendable, Equatable {
    public let blockID: String
    public let fromStart: Int
    public let toStart: Int
}

/// Propose moves that clear overlaps. Greedy: walk blocks by start; when a movable block overlaps
/// the running "occupied" frontier, relocate it to the earliest free window (within `dayStart`…
/// `dayEnd`) that fits, treating already-placed blocks as fixed.
public func resolveConflicts(
    _ blocks: [ResolvableBlock], dayStart: Int = 6 * 60, dayEnd: Int = 23 * 60
) -> [MoveSuggestion] {
    let sorted = blocks.sorted { $0.startMinute < $1.startMinute }
    // Occupied spans we won't move (fixed blocks + blocks already accepted/relocated).
    var occupied: [(start: Int, end: Int)] = []
    var suggestions: [MoveSuggestion] = []

    func overlaps(_ start: Int, _ end: Int) -> Bool {
        occupied.contains { start < $0.end && $0.start < end }
    }

    func firstFreeStart(duration: Int) -> Int? {
        let busy = occupied.sorted { $0.start < $1.start }
        var cursor = dayStart
        for span in busy {
            if span.start - cursor >= duration, cursor + duration <= dayEnd { return cursor }
            cursor = max(cursor, span.end)
        }
        return cursor + duration <= dayEnd ? cursor : nil
    }

    for block in sorted {
        if !overlaps(block.startMinute, block.endMinute) {
            occupied.append((block.startMinute, block.endMinute))
            continue
        }
        // Overlap. If movable, try to relocate; else leave it (can't fix a fixed block).
        if block.isMovable, let newStart = firstFreeStart(duration: block.durationMinutes) {
            suggestions.append(MoveSuggestion(blockID: block.id, fromStart: block.startMinute, toStart: newStart))
            occupied.append((newStart, newStart + block.durationMinutes))
        } else {
            occupied.append((block.startMinute, block.endMinute))
        }
    }
    return suggestions
}
