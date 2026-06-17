import Foundation

// Gap-fill planner — port of GapFillPlanner: fit pending tasks into the day's free windows,
// highest-priority and longest first, packing each window front-to-back without overflowing it.

public struct PendingTask: Sendable, Equatable {
    public let id: String
    public let title: String
    public let priority: Int          // higher = scheduled sooner
    public let durationMinutes: Int
    public init(id: String, title: String, priority: Int = 0, durationMinutes: Int = 30) {
        self.id = id
        self.title = title
        self.priority = priority
        self.durationMinutes = max(durationMinutes, 1)
    }
}

public struct GapFillSuggestion: Sendable, Equatable {
    public let taskID: String
    public let title: String
    public let startMinute: Int
    public let durationMinutes: Int
}

/// Pack `tasks` into `windows`. Tasks are tried by priority desc, then duration desc; each task is
/// placed in the first window with room, advancing that window's cursor. Returns the placements;
/// tasks that don't fit anywhere are omitted (the caller can surface them as unscheduled).
public func gapFill(tasks: [PendingTask], windows: [FreeWindow]) -> [GapFillSuggestion] {
    // Remaining capacity per window, tracked as (cursor, end).
    var cursors = windows
        .sorted { $0.startMinute < $1.startMinute }
        .map { (start: $0.startMinute, end: $0.startMinute + $0.durationMinutes) }
    var suggestions: [GapFillSuggestion] = []

    let ordered = tasks.sorted {
        $0.priority != $1.priority ? $0.priority > $1.priority : $0.durationMinutes > $1.durationMinutes
    }

    for task in ordered {
        for i in cursors.indices {
            if cursors[i].end - cursors[i].start >= task.durationMinutes {
                suggestions.append(GapFillSuggestion(
                    taskID: task.id, title: task.title,
                    startMinute: cursors[i].start, durationMinutes: task.durationMinutes))
                cursors[i].start += task.durationMinutes
                break
            }
        }
    }
    return suggestions
}
