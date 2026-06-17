import Foundation

// MARK: - Planner helpers
//
// Ports of the small, pure planner utilities from core/domain/planner that the UI relies on:
// sleep readiness, conflict detection, and free-time / gap finding.

/// Derives `SleepReadiness` from the night that ended this morning. A user rating (1..5) leads;
/// when absent, measured duration stands in; a heavily-interrupted night drags either down to
/// `depleted`. Returns `.unknown` (a no-op) when no usable signal exists.
/// Faithful port of `deriveSleepReadiness` in `SleepReadiness.kt`.
func deriveSleepReadiness(lastNight: SleepTrack?) -> SleepReadiness {
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

/// An overlap between two blocks on the dial. Mirrors `ScheduleConflict.kt`.
struct ScheduleConflict: Identifiable, Hashable {
    let id = UUID()
    let firstID: String
    let secondID: String
    let overlapMinutes: Int
}

/// A free window on the dial. Mirrors the output of `FreeTimeCalculator.kt`.
struct FreeWindow: Identifiable, Hashable {
    let id = UUID()
    let startMinute: Int
    let durationMinutes: Int
    var endMinute: Int { (startMinute + durationMinutes) % 1440 }
}

enum PlannerMath {
    /// Detect pairwise overlaps among same-day blocks (ignores cross-midnight wrap for simplicity,
    /// matching the common-case behaviour of the Android `ConflictDetectionEngine`).
    static func conflicts(in blocks: [TimeBlock]) -> [ScheduleConflict] {
        let sorted = blocks.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
        var result: [ScheduleConflict] = []
        for i in sorted.indices {
            for j in (i + 1)..<sorted.count {
                let a = sorted[i], b = sorted[j]
                let aEnd = a.startMinuteOfDay + a.durationMinutes
                let overlap = min(aEnd, b.startMinuteOfDay + b.durationMinutes) - b.startMinuteOfDay
                guard b.startMinuteOfDay < aEnd, overlap > 0 else { continue }
                result.append(ScheduleConflict(firstID: a.id, secondID: b.id, overlapMinutes: overlap))
            }
        }
        return result
    }

    /// Find free windows of at least `minDuration` minutes between `dayStart` and `dayEnd`.
    static func freeWindows(
        in blocks: [TimeBlock],
        dayStart: Int = 6 * 60,
        dayEnd: Int = 23 * 60,
        minDuration: Int = 30
    ) -> [FreeWindow] {
        let busy = blocks
            .map { (start: $0.startMinuteOfDay, end: min($0.startMinuteOfDay + $0.durationMinutes, 1440)) }
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
