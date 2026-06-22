import Foundation

// Smart duplicate placement — pure, Foundation-only port of
// `DayDialBlockDelegate.nextDuplicatePlacement` (+ the `FreeTimeCalculator` it relies on).
//
// The iOS app's `TimeBlock` is a SwiftData `@Model` reference type and so cannot cross into this
// pure, Sendable core. Mirroring the ChronosCore convention (BlockSpan / ResolvableBlock /
// PendingTask in Planner.swift / ConflictResolve.swift), the planner-command layer reasons over a
// reduced *value* type, `PlannerBlock`, that the UI maps `TimeBlock` onto and back. It carries
// exactly the fields the command pattern and duplicate heuristics touch — nothing UI-specific.
//
// Everything here is deterministic and reads no wall clock: callers inject ids/timestamps.

// MARK: - PlannerBlock (the portable "[TimeBlock]" the planner operates over)

/// A value-typed projection of the app's `TimeBlock`, holding the fields the planner reasons about.
/// Mirrors `core.domain.model.TimeBlock`'s planner-relevant surface so the command pattern and the
/// duplicate heuristics port over unchanged. The iOS UI maps its SwiftData `TimeBlock` to this and
/// writes the resulting changes back.
public struct PlannerBlock: Sendable, Equatable, Identifiable {
    public var id: String
    public var title: String
    public var category: String
    public var startMinuteOfDay: Int
    public var durationMinutes: Int
    public var timezone: String
    public var provenance: BlockProvenance
    public var flexibility: BlockFlexibility
    public var energyLevel: EnergyIntensity
    public var source: String

    // Cross-entity instance-identity links (kept as ids to match the relational model). Cleared when
    // a block is duplicated so the copy isn't a phantom second instance of the same scheduled thing.
    public var taskId: String?
    public var taskOccurrenceDate: Date?
    public var calendarEventId: Int64?
    public var medicationPlanId: String?
    public var habitId: String?
    public var goalId: String?
    public var routineId: String?
    public var recurrenceRuleId: String?

    public var isLocked: Bool
    public var isProtected: Bool

    // Actual (logged) execution window. Cleared on duplicate (a copy hasn't been executed yet).
    public var actualStartMinuteOfDay: Int?
    public var actualEndMinuteOfDay: Int?

    public init(
        id: String,
        title: String,
        category: String = "FOCUS",
        startMinuteOfDay: Int,
        durationMinutes: Int,
        timezone: String = "UTC",
        provenance: BlockProvenance = .user,
        flexibility: BlockFlexibility = .movable,
        energyLevel: EnergyIntensity = .moderate,
        source: String = "USER",
        taskId: String? = nil,
        taskOccurrenceDate: Date? = nil,
        calendarEventId: Int64? = nil,
        medicationPlanId: String? = nil,
        habitId: String? = nil,
        goalId: String? = nil,
        routineId: String? = nil,
        recurrenceRuleId: String? = nil,
        isLocked: Bool = false,
        isProtected: Bool = false,
        actualStartMinuteOfDay: Int? = nil,
        actualEndMinuteOfDay: Int? = nil
    ) {
        self.id = id
        self.title = title
        self.category = category
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.timezone = timezone
        self.provenance = provenance
        self.flexibility = flexibility
        self.energyLevel = energyLevel
        self.source = source
        self.taskId = taskId
        self.taskOccurrenceDate = taskOccurrenceDate
        self.calendarEventId = calendarEventId
        self.medicationPlanId = medicationPlanId
        self.habitId = habitId
        self.goalId = goalId
        self.routineId = routineId
        self.recurrenceRuleId = recurrenceRuleId
        self.isLocked = isLocked
        self.isProtected = isProtected
        self.actualStartMinuteOfDay = actualStartMinuteOfDay
        self.actualEndMinuteOfDay = actualEndMinuteOfDay
    }

    /// End in minute-of-day terms (may exceed 1440 when the block wraps past midnight).
    public var endMinuteOfDay: Int { startMinuteOfDay + durationMinutes }

    /// All-day calendar imports don't occupy schedule time. Port of `isAllDayCalendarImport`.
    public var isAllDayCalendarImport: Bool {
        provenance == .calendar &&
            calendarEventId != nil &&
            (category.caseInsensitiveCompare(PlannerBlock.allDayCalendarCategory) == .orderedSame ||
                durationMinutes >= 1440)
    }

    /// Port of `TimeBlock.occupiesScheduleTime()` — everything but an all-day calendar import.
    public var occupiesScheduleTime: Bool { !isAllDayCalendarImport }

    static let allDayCalendarCategory = "CALENDAR_ALL_DAY"
}

// MARK: - Free-time segments (port of FreeTimeCalculator)

/// A contiguous open span of the day, in minute-of-day terms. Port of `FreeTimeSegment`.
public struct FreeTimeSegment: Sendable, Equatable {
    public let startMinute: Int
    public let endMinute: Int
    public init(startMinute: Int, endMinute: Int) {
        self.startMinute = startMinute
        self.endMinute = endMinute
    }
    public var lengthMinutes: Int { endMinute - startMinute }
}

public enum FreeTimeCalculator {
    /// Free spans across the 0…1440 day, computed by marking every occupied minute (wrapping past
    /// midnight, modulo 1440) and reading off the gaps. Faithful port of `FreeTimeCalculator.calculate`:
    /// only blocks that `occupiesScheduleTime` count, and the trailing gap to minute 1440 is included.
    public static func calculate(_ blocks: [PlannerBlock]) -> [FreeTimeSegment] {
        var taken = [Bool](repeating: false, count: 1440)
        for block in blocks where block.occupiesScheduleTime {
            let start = block.startMinuteOfDay
            let end = start + block.durationMinutes
            if end <= 1440 {
                var minute = start
                while minute < end {
                    taken[((minute % 1440) + 1440) % 1440] = true
                    minute += 1
                }
            } else {
                var minute = start
                while minute < 1440 { taken[minute] = true; minute += 1 }
                let wrapEnd = end % 1440
                var w = 0
                while w < wrapEnd { taken[w] = true; w += 1 }
            }
        }

        var segments: [FreeTimeSegment] = []
        var cursor: Int? = nil
        for minute in 0..<1440 {
            if !taken[minute] && cursor == nil {
                cursor = minute
            }
            if (taken[minute] || minute == 1439), let start = cursor {
                let end = taken[minute] ? minute : minute + 1
                if end > start { segments.append(FreeTimeSegment(startMinute: start, endMinute: end)) }
                cursor = nil
            }
        }
        return segments
    }
}

// MARK: - Duplicate placement heuristic

/// Where a duplicate of a block should land, in minute-of-day terms. Port of `DuplicatePlacement`.
public struct DuplicatePlacement: Sendable, Equatable {
    public let startMinute: Int
    public let durationMinutes: Int
    public init(startMinute: Int, durationMinutes: Int) {
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
    }
}

/// The smallest gap a shrunk duplicate may occupy. Mirrors `MIN_DUPLICATE_GAP_MINUTES = 15`.
public let minDuplicateGapMinutes = 15

/// Decides where a copy of `source` lands so it sits in open time instead of on top of the original.
///
/// In order of preference (faithful port of `nextDuplicatePlacement`):
///  1. the first free gap at/after the original's end that fits the *full* duration,
///  2. the earliest gap anywhere that fits the *full* duration,
///  3. the largest gap, with the copy shrunk to fit it (never below `minDuplicateGapMinutes`),
///  4. `nil` when no usable free time remains.
///
/// `dayBlocks` should be the source's whole day, *including* the source itself (it occupies time, so
/// a copy can't land on top of it). Pure and deterministic.
public func nextDuplicatePlacement(dayBlocks: [PlannerBlock], source: PlannerBlock) -> DuplicatePlacement? {
    let duration = source.durationMinutes
    let preferredStart = source.startMinuteOfDay + duration
    let freeSegments = FreeTimeCalculator.calculate(dayBlocks)
    if freeSegments.isEmpty { return nil }

    // (1) First gap at/after the original's end that fits the full block.
    if let segment = freeSegments
        .filter({ $0.endMinute - max($0.startMinute, preferredStart) >= duration })
        .min(by: { $0.startMinute < $1.startMinute }) {
        return DuplicatePlacement(startMinute: max(segment.startMinute, preferredStart), durationMinutes: duration)
    }

    // (2) Earliest gap anywhere that fits the full block.
    if let segment = freeSegments
        .filter({ $0.endMinute - $0.startMinute >= duration })
        .min(by: { $0.startMinute < $1.startMinute }) {
        return DuplicatePlacement(startMinute: segment.startMinute, durationMinutes: duration)
    }

    // (3) Largest gap, copy shrunk to fit it (>= the minimum gap). On ties keep the earliest
    // segment, matching Kotlin `maxByOrNull` (first maximum) — `Sequence.max(by:)` keeps the last.
    guard let largest = freeSegments.reduce(nil as FreeTimeSegment?, { best, seg in
        guard let best else { return seg }
        return seg.lengthMinutes > best.lengthMinutes ? seg : best
    }) else { return nil }
    let gapSize = largest.lengthMinutes
    guard gapSize >= minDuplicateGapMinutes else { return nil }
    return DuplicatePlacement(startMinute: largest.startMinute, durationMinutes: min(gapSize, duration))
}

/// Builds the standalone duplicate of `source` once `nextDuplicatePlacement` has chosen a slot.
///
/// Mirrors the `source.copy(...)` in `DayDialBlockDelegate.duplicateBlock`: a fresh id and the chosen
/// window, every instance-identity link dropped (task / habit / medication / recurrence / routine /
/// calendar + actuals) so the copy isn't a phantom second instance, provenance/source normalized to
/// USER, the block unlocked, and FIXED downgraded to MOVABLE (so it survives create/placement
/// validation). The title gains a " (Copy)" suffix. Ids/timestamps are injected — never read here.
public func makeDuplicate(
    of source: PlannerBlock,
    placement: DuplicatePlacement,
    newId: String,
    copySuffix: String = " (Copy)"
) -> PlannerBlock {
    var copy = source
    copy.id = newId
    copy.title = source.title + copySuffix
    copy.startMinuteOfDay = placement.startMinute
    copy.durationMinutes = placement.durationMinutes
    copy.provenance = .user
    copy.source = "USER"
    copy.flexibility = source.flexibility == .fixed ? .movable : source.flexibility
    copy.isLocked = false
    copy.taskId = nil
    copy.taskOccurrenceDate = nil
    copy.calendarEventId = nil
    copy.medicationPlanId = nil
    copy.habitId = nil
    copy.goalId = nil
    copy.routineId = nil
    copy.recurrenceRuleId = nil
    copy.actualStartMinuteOfDay = nil
    copy.actualEndMinuteOfDay = nil
    return copy
}
