import Foundation

// Portable planner logic — sleep readiness + conflict / free-window detection over plain value
// types. Faithful ports of SleepReadiness.kt, ConflictDetectionEngine.kt, and FreeTimeCalculator.kt.

/// A logged night, reduced to the fields readiness depends on.
public struct SleepNight: Sendable {
    public var sleepQuality: Int       // 0 = unrated, else 1..5
    public var interruptedCount: Int
    public var actualStartMinute: Int?
    public var actualEndMinute: Int?

    public init(sleepQuality: Int = 0, interruptedCount: Int = 0,
                actualStartMinute: Int? = nil, actualEndMinute: Int? = nil) {
        self.sleepQuality = sleepQuality
        self.interruptedCount = interruptedCount
        self.actualStartMinute = actualStartMinute
        self.actualEndMinute = actualEndMinute
    }

    /// Measured sleep length in minutes, wrapping past midnight.
    public var durationMinutes: Int? {
        guard let s = actualStartMinute, let e = actualEndMinute else { return nil }
        let span = e - s
        return span >= 0 ? span : span + 1440
    }
}

/// Derives readiness from the night that ended this morning. A 1..5 rating leads; when absent,
/// measured duration stands in; a heavily-interrupted night drags either down to `.depleted`.
/// Returns `.unknown` (a no-op) when no usable signal exists. Port of `deriveSleepReadiness`.
public func deriveSleepReadiness(lastNight: SleepNight?) -> SleepReadiness {
    guard let lastNight else { return .unknown }
    let heavilyInterrupted = lastNight.interruptedCount >= 3
    if lastNight.sleepQuality > 0 {
        let q = lastNight.sleepQuality
        if q <= 2 || heavilyInterrupted { return .depleted }
        if q >= 4 { return .rested }
        return .normal
    }
    guard let duration = lastNight.durationMinutes else { return .unknown }
    if duration < 6 * 60 || heavilyInterrupted { return .depleted }
    if duration >= 7 * 60 + 30 { return .rested }
    return .normal
}

/// A scheduled span, reduced to the fields the planner reasons about.
public struct BlockSpan: Identifiable, Sendable {
    public let id: String
    public let startMinute: Int
    public let durationMinutes: Int
    public init(id: String, startMinute: Int, durationMinutes: Int) {
        self.id = id
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
    }
    public var endMinute: Int { startMinute + durationMinutes }
}

public struct Conflict: Equatable, Sendable {
    public let firstID: String
    public let secondID: String
    public let overlapMinutes: Int
}

public struct FreeWindow: Equatable, Sendable {
    public let startMinute: Int
    public let durationMinutes: Int
}

public enum PlannerMath {
    /// Pairwise overlaps among same-day spans. Port of the common case of ConflictDetectionEngine.
    public static func conflicts(in blocks: [BlockSpan]) -> [Conflict] {
        let sorted = blocks.sorted { $0.startMinute < $1.startMinute }
        var result: [Conflict] = []
        for i in sorted.indices {
            for j in (i + 1)..<sorted.count {
                let a = sorted[i], b = sorted[j]
                let overlap = min(a.endMinute, b.endMinute) - b.startMinute
                guard b.startMinute < a.endMinute, overlap > 0 else { continue }
                result.append(Conflict(firstID: a.id, secondID: b.id, overlapMinutes: overlap))
            }
        }
        return result
    }

    /// Free windows of at least `minDuration` minutes between `dayStart` and `dayEnd`.
    public static func freeWindows(
        in blocks: [BlockSpan],
        dayStart: Int = 6 * 60,
        dayEnd: Int = 23 * 60,
        minDuration: Int = 30
    ) -> [FreeWindow] {
        let busy = blocks
            .map { (start: $0.startMinute, end: min($0.endMinute, 1440)) }
            .sorted { $0.start < $1.start }
        var windows: [FreeWindow] = []
        var cursor = dayStart
        for span in busy {
            if span.start > cursor {
                let gap = min(span.start, dayEnd) - cursor
                if gap >= minDuration { windows.append(FreeWindow(startMinute: cursor, durationMinutes: gap)) }
            }
            cursor = max(cursor, span.end)
            if cursor >= dayEnd { break }
        }
        if cursor < dayEnd {
            let gap = dayEnd - cursor
            if gap >= minDuration { windows.append(FreeWindow(startMinute: cursor, durationMinutes: gap)) }
        }
        return windows
    }
}
