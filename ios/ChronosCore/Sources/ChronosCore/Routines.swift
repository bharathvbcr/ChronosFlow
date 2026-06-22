import Foundation

// Routine instantiation — port of applying a Routine's steps onto a chosen start time, producing
// concrete block specs (ApplyRoutineToDateUseCase / RoutineStep in Routine.kt).
//
// Android reference (core/domain/.../ApplyRoutineToDateUseCase.kt:38-44):
//   val total    = startMinuteOfDay + step.offsetMinute
//   val dayShift = Math.floorDiv(total, 1440)   // which date the step lands on (0 = same day, 1 = next…)
//   val start    = Math.floorMod(total, 1440)   // minute-of-day on that date, always in 0..1439
//   date.plusDays(dayShift)
//
// A step whose absolute minute exceeds 24:00 must roll onto the FOLLOWING date rather than folding
// back to the start of the same day. Plain `%` wraps within a single day and silently loses the
// day-shift, which is the latent bug this file fixes. floorDiv/floorMod stay correct for any anchor,
// including negative offsets (a step scheduled before the anchor rolls to the previous date).

public struct RoutineStepSpec: Sendable, Equatable {
    public var title: String
    public var category: String
    public var offsetMinute: Int
    public var durationMinutes: Int
    public var energyLevel: Int
    public init(title: String, category: String = "ROUTINE", offsetMinute: Int,
                durationMinutes: Int, energyLevel: Int = 2) {
        self.title = title
        self.category = category
        self.offsetMinute = offsetMinute
        self.durationMinutes = durationMinutes
        self.energyLevel = energyLevel
    }
}

/// A concrete block produced by instantiating a routine step at an absolute start minute.
///
/// `daysOffset` is the number of whole days the step's start rolls past the anchor date:
/// `0` is the anchor date, `1` the next date (the step crossed midnight), `-1` the previous date,
/// and so on. Callers add `daysOffset` days to the anchor date to find the calendar date this block
/// belongs on. `startMinuteOfDay` is always normalized into `0..1439` for that resulting date.
public struct InstantiatedBlock: Sendable, Equatable {
    public let title: String
    public let category: String
    public let startMinuteOfDay: Int
    public let durationMinutes: Int
    public let energyLevel: Int
    public let daysOffset: Int

    public init(title: String, category: String, startMinuteOfDay: Int,
                durationMinutes: Int, energyLevel: Int, daysOffset: Int) {
        self.title = title
        self.category = category
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.energyLevel = energyLevel
        self.daysOffset = daysOffset
    }
}

/// Number of whole minutes in a day. Anchor + offset arithmetic normalizes against this.
private let minutesPerDay = 1440

/// Floored integer division — mirrors `Math.floorDiv`. Rounds toward negative infinity (not toward
/// zero like Swift's `/`), so `chronosFloorDiv(-1, 1440) == -1`, matching the Kotlin source exactly.
public func chronosFloorDiv(_ value: Int, _ divisor: Int) -> Int {
    let q = value / divisor
    // If the signs differ and the division was not exact, the truncated quotient is one too high.
    if (value % divisor != 0) && ((value < 0) != (divisor < 0)) {
        return q - 1
    }
    return q
}

/// Floored modulo — mirrors `Math.floorMod`. The result has the same sign as `divisor`, so for a
/// positive `divisor` the result is always in `0..<divisor` (`chronosFloorMod(-30, 1440) == 1410`).
public func chronosFloorMod(_ value: Int, _ divisor: Int) -> Int {
    let r = value % divisor
    if r != 0 && ((r < 0) != (divisor < 0)) {
        return r + divisor
    }
    return r
}

/// Splits an absolute minute count into the calendar day it lands on and the normalized
/// minute-of-day, exactly as `ApplyRoutineToDateUseCase` does (`floorDiv` / `floorMod` over 1440).
///
/// - Returns: `(daysOffset, minuteOfDay)` where `minuteOfDay` is always in `0..1439`.
public func dayAndMinute(forAbsoluteMinute total: Int) -> (daysOffset: Int, minuteOfDay: Int) {
    (chronosFloorDiv(total, minutesPerDay), chronosFloorMod(total, minutesPerDay))
}

/// Instantiate routine steps starting at `startMinute`. Each step lands at `startMinute + offset`.
/// When that absolute minute crosses a day boundary the block carries a non-zero `daysOffset` so the
/// caller can place it on the correct calendar date; `startMinuteOfDay` is normalized into `0..1439`
/// for that date. Duration is clamped to `1..1440`. Pure and deterministic — no wall-clock reads.
public func instantiateRoutine(steps: [RoutineStepSpec], startMinute: Int) -> [InstantiatedBlock] {
    steps.map { step in
        let total = startMinute + step.offsetMinute
        let (daysOffset, start) = dayAndMinute(forAbsoluteMinute: total)
        return InstantiatedBlock(
            title: step.title,
            category: step.category,
            startMinuteOfDay: start,
            durationMinutes: min(max(step.durationMinutes, 1), minutesPerDay),
            energyLevel: step.energyLevel,
            daysOffset: daysOffset
        )
    }
}

/// Day-aware instantiation that resolves each block onto a concrete `Date`. Given an `anchorDate`
/// (interpreted at its start-of-day in `calendar`) and an anchor `startMinute`, returns each block
/// paired with the calendar date it falls on. Calendar/date are injected for determinism — no
/// wall-clock access. Returns `nil` only if the calendar cannot do the day arithmetic.
public func instantiateRoutineOnDates(
    steps: [RoutineStepSpec],
    startMinute: Int,
    anchorDate: Date,
    calendar: Calendar
) -> [(block: InstantiatedBlock, date: Date)]? {
    let startOfAnchor = calendar.startOfDay(for: anchorDate)
    let blocks = instantiateRoutine(steps: steps, startMinute: startMinute)
    var result: [(block: InstantiatedBlock, date: Date)] = []
    result.reserveCapacity(blocks.count)
    for block in blocks {
        guard let date = calendar.date(byAdding: .day, value: block.daysOffset, to: startOfAnchor) else {
            return nil
        }
        result.append((block, date))
    }
    return result
}
