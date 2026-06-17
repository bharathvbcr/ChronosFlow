import Foundation

// Readiness-driven scheduling — portable, Foundation-only mutations that let sleep readiness
// actually reshape the day's plan, not just advise. Ports the Android thresholds from
// ScheduleTaskIntoDayUseCase (DEPLETED_DEMANDING_FLOOR_MINUTE, energy-peak skip) and
// GapFillPlanner (DEPLETED_FOCUS_STRETCH_MINUTES / DEPLETED_BREAK_DURATION_MINUTES).
//
// After a DEPLETED night:
//   • demanding (high-energy) work is floored to >= 11:00 (the grogginess buffer),
//   • the historical energy-peak deferral is skipped (the morning peak is unreliable),
//   • focus stretches shorten 90 -> 60 minutes,
//   • recovery breaks lengthen 20 -> 25 minutes.
// Every other readiness (normal / rested / unknown) is a no-op, preserving the existing plan.

public enum ReadinessSchedule {
    /// Minutes; demanding work avoids the early-morning window after a depleted night. == 11:00.
    public static let depletedDemandingFloorMinute = 11 * 60

    /// Energy level at or above which a block counts as "demanding". Matches Android's HIGH+ gate.
    public static let demandingEnergyLevel = EnergyIntensity.intense.rawValue   // 4

    static let normalFocusStretchMinutes = 90
    static let depletedFocusStretchMinutes = 60
    static let normalRecoveryBreakMinutes = 20
    static let depletedRecoveryBreakMinutes = 25

    /// Earliest minute-of-day a demanding task may start, or nil when readiness imposes no floor.
    public static func demandingTaskEarliestStartMinute(_ readiness: SleepReadiness) -> Int? {
        readiness == .depleted ? depletedDemandingFloorMinute : nil
    }

    /// Whether to skip biasing demanding work toward the historical energy peak.
    public static func shouldSkipEnergyPeakDeferral(_ readiness: SleepReadiness) -> Bool {
        readiness == .depleted
    }

    /// Focus-stretch length before a recovery break is offered.
    public static func focusStretchMinutes(_ readiness: SleepReadiness) -> Int {
        readiness == .depleted ? depletedFocusStretchMinutes : normalFocusStretchMinutes
    }

    /// Recovery-break length.
    public static func recoveryBreakMinutes(_ readiness: SleepReadiness) -> Int {
        readiness == .depleted ? depletedRecoveryBreakMinutes : normalRecoveryBreakMinutes
    }

    /// True when a block at `energyLevel` counts as demanding/high-energy.
    public static func isDemanding(energyLevel: Int) -> Bool {
        energyLevel >= demandingEnergyLevel
    }
}

/// A planned block reduced to the fields the readiness shifter reasons about. Deliberately a value
/// type so the iOS target can map `TimeBlock` onto it, run the pure shift, then write start changes
/// back. `isFixed` blocks (locked / protected / fixed flexibility) are never moved.
public struct ReadinessPlanBlock: Sendable, Equatable, Identifiable {
    public let id: String
    public var startMinute: Int
    public let durationMinutes: Int
    public let energyLevel: Int
    public let isFixed: Bool

    public init(id: String, startMinute: Int, durationMinutes: Int, energyLevel: Int, isFixed: Bool = false) {
        self.id = id
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.energyLevel = energyLevel
        self.isFixed = isFixed
    }

    public var endMinute: Int { startMinute + durationMinutes }
}

/// Apply readiness to a day's planned blocks. When `readiness == .depleted`, any movable, demanding
/// (high-energy) block that starts before the 11:00 floor is shifted to the floor or — if the floor
/// is occupied — to the next free slot that fits without overlapping the other (already-placed)
/// blocks. Non-demanding, fixed, and already-late blocks are returned untouched. For every other
/// readiness this is a deterministic no-op returning the input order unchanged.
///
/// Determinism: blocks are processed in start-time order; each shifted block is placed against the
/// set of blocks NOT being shifted (the obstacles), so the result depends only on the input.
public func applyReadiness(
    to plannedBlocks: [ReadinessPlanBlock],
    readiness: SleepReadiness,
    dayEndMinute: Int = 23 * 60
) -> [ReadinessPlanBlock] {
    guard readiness == .depleted else { return plannedBlocks }
    let floor = ReadinessSchedule.depletedDemandingFloorMinute

    // Blocks that move vs. obstacles that stay. A block moves iff it is demanding, movable, and
    // currently starts before the floor.
    func shouldShift(_ b: ReadinessPlanBlock) -> Bool {
        !b.isFixed
            && ReadinessSchedule.isDemanding(energyLevel: b.energyLevel)
            && b.startMinute < floor
    }

    let toShift = plannedBlocks.filter(shouldShift).sorted { $0.startMinute < $1.startMinute }
    guard !toShift.isEmpty else { return plannedBlocks }

    // Obstacles: everything not being shifted, as occupied [start,end) spans. Shifted blocks are
    // placed one at a time and become obstacles for the next, so two shifted blocks never collide.
    var occupied: [(start: Int, end: Int)] = plannedBlocks
        .filter { !shouldShift($0) }
        .map { ($0.startMinute, $0.endMinute) }

    var newStarts: [String: Int] = [:]
    for block in toShift {
        let placed = firstFreeStart(
            earliest: floor,
            duration: block.durationMinutes,
            dayEnd: dayEndMinute,
            occupied: occupied)
        // If nothing fits before day end, fall back to the floor (soft preference, matching the
        // Android "fall through rather than fail" behaviour).
        let start = placed ?? floor
        newStarts[block.id] = start
        occupied.append((start, start + block.durationMinutes))
        occupied.sort { $0.start < $1.start }
    }

    return plannedBlocks.map { block in
        guard let start = newStarts[block.id] else { return block }
        var moved = block
        moved.startMinute = start
        return moved
    }
}

/// First start >= `earliest` where a `duration`-long block fits without overlapping `occupied`,
/// staying within `dayEnd`. Returns nil if no such slot exists. `occupied` must be sorted by start.
private func firstFreeStart(
    earliest: Int,
    duration: Int,
    dayEnd: Int,
    occupied: [(start: Int, end: Int)]
) -> Int? {
    var cursor = earliest
    for span in occupied where span.end > cursor {
        // If the candidate placement [cursor, cursor+duration) clears this span, we're done.
        if cursor + duration <= span.start { break }
        // Otherwise jump past it.
        cursor = max(cursor, span.end)
    }
    return cursor + duration <= dayEnd ? cursor : nil
}
