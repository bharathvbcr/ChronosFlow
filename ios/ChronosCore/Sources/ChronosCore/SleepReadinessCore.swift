import Foundation

// SleepReadinessCore — the single, portable entry point that centralizes the "how did the user
// sleep, and how should today's plan adapt?" logic that was previously scattered across the iOS app
// (ios/ChronosFlow/Planner/Planner.swift) and SwiftUI views. It is a thin orchestration layer over
// the already-ported primitives so callers have one place to reach for, rather than reaching into
// `deriveSleepReadiness`, `ReadinessSchedule`, and the gap math separately.
//
// Foundation-only, deterministic, and clock-free: every date/comparison the floor needs is injected
// as a parameter (`isSameDay`), never read from the wall clock. This file does NOT redefine the
// existing primitives — it reuses:
//   • `deriveSleepReadiness(lastNight:)` and `SleepNight`    (Planner.swift)
//   • `ReadinessSchedule` / `ReadinessPlanBlock` / `applyReadiness` (ReadinessSchedule.swift)
//   • `FreeWindow`                                            (Planner.swift)
//   • `SleepReadiness` / `EnergyIntensity`                    (Enums.swift)
//
// What it ADDS:
//   1. `deriveSleepReadiness(...)` convenience overloads (centralization of the entry point).
//   2. `excludeSleepMinutesFromWindows(_:sleepWindow:)` — splits free windows around the planned
//      sleep window so the planner never fills time during sleep. Exact port of Android's
//      `GapFillPlanner.excludeSleepMinutes` (per-minute split, overnight-aware).
//   3. `applyReadinessFloor(...)` — the single-task analogue of `applyReadiness`: defer one
//      demanding task past the 11:00 grogginess floor after a depleted night, same-day gated, with
//      UNKNOWN/non-depleted as a no-op. Port of `ScheduleTaskIntoDayUseCase.findStartMinute`'s
//      `DEPLETED_DEMANDING_FLOOR_MINUTE` branch.

// MARK: - Planned sleep window

/// The planned (scheduled) sleep window as a pair of minute-of-day endpoints, used to carve sleep
/// time out of free windows. This is the portable analogue of Android's `SleepSchedule` (the
/// `plannedStartMinute` / `plannedEndMinute` on a `SleepTrack`). A window is *active* only when both
/// ends are present and differ; an inactive window leaves the day untouched.
public struct PlannedSleepWindow: Sendable, Equatable {
    /// Minute-of-day the user plans to fall asleep (e.g. 21*60 = 21:00).
    public let startMinute: Int
    /// Minute-of-day the user plans to wake (e.g. 7*60 = 07:00).
    public let endMinute: Int

    public init(startMinute: Int, endMinute: Int) {
        self.startMinute = startMinute
        self.endMinute = endMinute
    }

    /// Minutes in a full day.
    public static let minutesPerDay = 24 * 60

    private static func normalize(_ minute: Int) -> Int {
        ((minute % minutesPerDay) + minutesPerDay) % minutesPerDay
    }

    private var normalizedStart: Int { Self.normalize(startMinute) }
    private var normalizedEnd: Int { Self.normalize(endMinute) }

    /// A zero-length (or equal-endpoint) window carries no sleep span, so it is inactive. Mirrors
    /// `SleepSchedule.isActive`.
    public var isActive: Bool { normalizedStart != normalizedEnd }

    /// True when the window wraps past midnight (bed later in the day than wake). Mirrors
    /// `SleepSchedule.isOvernight`.
    public var isOvernight: Bool { isActive && normalizedStart > normalizedEnd }

    /// Whether `minuteOfDay` falls inside the planned sleep span. Mirrors `SleepSchedule.contains`.
    public func contains(_ minuteOfDay: Int) -> Bool {
        guard isActive else { return false }
        let m = Self.normalize(minuteOfDay)
        if isOvernight {
            return m >= normalizedStart || m < normalizedEnd
        } else {
            return m >= normalizedStart && m < normalizedEnd
        }
    }

    /// Builds a window from optional endpoints (as carried on a logged night's planned fields),
    /// returning nil when either is missing — the "no schedule set" case.
    public static func from(plannedStartMinute: Int?, plannedEndMinute: Int?) -> PlannedSleepWindow? {
        guard let s = plannedStartMinute, let e = plannedEndMinute else { return nil }
        return PlannedSleepWindow(startMinute: s, endMinute: e)
    }
}

// MARK: - SleepReadinessCore facade

/// Centralized facade for sleep-readiness-driven planning. Every member is `static` and pure; the
/// type is an empty namespace (no stored state) so it is trivially `Sendable`.
public enum SleepReadinessCore {

    // MARK: Derivation (centralized entry point)

    /// Derives next-day readiness from the night that ended this morning. Delegates to the existing
    /// `deriveSleepReadiness(lastNight:)` so there is exactly one rule set. Provided here so callers
    /// reach for a single namespace (`SleepReadinessCore.deriveReadiness(...)`) instead of the
    /// free function, matching the Kotlin module's single home for the rule.
    public static func deriveReadiness(lastNight: SleepNight?) -> SleepReadiness {
        deriveSleepReadiness(lastNight: lastNight)
    }

    /// Convenience overload taking the raw logged fields, for call sites that hold a SwiftData/Room
    /// row rather than a `SleepNight`. A `sleepQuality` of 0 means "unrated".
    public static func deriveReadiness(
        sleepQuality: Int,
        interruptedCount: Int,
        actualStartMinute: Int?,
        actualEndMinute: Int?
    ) -> SleepReadiness {
        deriveSleepReadiness(
            lastNight: SleepNight(
                sleepQuality: sleepQuality,
                interruptedCount: interruptedCount,
                actualStartMinute: actualStartMinute,
                actualEndMinute: actualEndMinute
            )
        )
    }

    // MARK: Demanding-floor primitives (re-exported for one-stop discovery)

    /// Earliest minute-of-day a demanding task may start under `readiness`, or nil for no floor.
    /// Mirrors `ReadinessSchedule.demandingTaskEarliestStartMinute`.
    public static func demandingFloorMinute(_ readiness: SleepReadiness) -> Int? {
        ReadinessSchedule.demandingTaskEarliestStartMinute(readiness)
    }

    /// True when a block at `energyLevel` counts as demanding/high-energy (>= intense).
    public static func isDemanding(energyLevel: Int) -> Bool {
        ReadinessSchedule.isDemanding(energyLevel: energyLevel)
    }

    // MARK: 1. Exclude sleep minutes from free windows

    /// Splits each free window around the planned sleep window, so the planner never proposes work
    /// during sleep. Exact port of Android's `GapFillPlanner.excludeSleepMinutes`: walk each window
    /// minute-by-minute, emitting a sub-window for every contiguous run of waking minutes. When the
    /// sleep window is inactive (or nil) the input is returned unchanged. Overnight windows are
    /// handled via `PlannedSleepWindow.contains` (which wraps past midnight).
    ///
    /// Determinism: pure function of the inputs; windows are processed in the given order and each
    /// emitted sub-window preserves the source ordering.
    public static func excludeSleepMinutesFromWindows(
        _ windows: [FreeWindow],
        sleepWindow: PlannedSleepWindow?
    ) -> [FreeWindow] {
        guard let sleepWindow, sleepWindow.isActive else { return windows }
        var result: [FreeWindow] = []
        for window in windows {
            let startMinute = window.startMinute
            let endMinute = window.startMinute + window.durationMinutes
            guard endMinute > startMinute else { continue }
            var runStart: Int? = nil
            // Mirror the Kotlin `for (minute in gap.startMinute until gap.endMinute)` loop exactly.
            for minute in startMinute..<endMinute {
                let sleeping = sleepWindow.contains(minute)
                if !sleeping && runStart == nil {
                    runStart = minute
                }
                if (sleeping || minute == endMinute - 1), let s = runStart {
                    let runEnd = sleeping ? minute : minute + 1
                    if runEnd > s {
                        result.append(FreeWindow(startMinute: s, durationMinutes: runEnd - s))
                    }
                    runStart = nil
                }
            }
        }
        return result
    }

    /// Overload accepting the planned bed/wake minutes directly (as carried on a logged night).
    public static func excludeSleepMinutesFromWindows(
        _ windows: [FreeWindow],
        plannedStartMinute: Int?,
        plannedEndMinute: Int?
    ) -> [FreeWindow] {
        excludeSleepMinutesFromWindows(
            windows,
            sleepWindow: PlannedSleepWindow.from(
                plannedStartMinute: plannedStartMinute,
                plannedEndMinute: plannedEndMinute
            )
        )
    }

    // MARK: 2. Apply readiness floor to a single task placement

    /// The outcome of attempting to floor a single demanding task after a depleted night.
    public struct FloorResult: Sendable, Equatable {
        /// The minute-of-day the task should start at (possibly the original).
        public let startMinute: Int
        /// True iff the floor actually moved the start later.
        public let wasDeferred: Bool

        public init(startMinute: Int, wasDeferred: Bool) {
            self.startMinute = startMinute
            self.wasDeferred = wasDeferred
        }
    }

    /// Single-task analogue of `applyReadiness`. After a *depleted* night, slide one *demanding*
    /// (high-energy) task past the 11:00 grogginess floor — but ONLY when:
    ///   • `readiness == .depleted` (every other readiness, incl. `.unknown`, is a no-op), and
    ///   • the task is being placed for *today* (`isSameDay == true`; last night only bears on
    ///     today's plan — Android sets readiness to UNKNOWN for future dates), and
    ///   • the task is demanding (`energyLevel >= intense`), and
    ///   • the user gave no explicit preferred start (`hasUserPreferredStart == false`; an explicit
    ///     choice is respected, matching Android's `preferredStartMinuteOfDay == null` gate).
    ///
    /// Soft preference: the deferred start is `max(currentStartMinute, 11:00)`. If the floored start
    /// would not fit before `dayEndMinute`, the original start is kept (Android "falls through to
    /// first-fit rather than fail to schedule"). The function never moves a task *earlier*.
    public static func applyReadinessFloor(
        currentStartMinute: Int,
        durationMinutes: Int,
        energyLevel: Int,
        readiness: SleepReadiness,
        isSameDay: Bool,
        hasUserPreferredStart: Bool = false,
        dayEndMinute: Int = 23 * 60
    ) -> FloorResult {
        let noChange = FloorResult(startMinute: currentStartMinute, wasDeferred: false)
        guard isSameDay,
              readiness == .depleted,
              !hasUserPreferredStart,
              isDemanding(energyLevel: energyLevel),
              let floor = demandingFloorMinute(readiness)
        else { return noChange }

        // Only ever push later; an already-late task is untouched.
        guard currentStartMinute < floor else { return noChange }

        let deferred = max(currentStartMinute, floor)
        // Soft fallback: if the floored placement would overrun the day, keep the original start.
        guard deferred + durationMinutes <= dayEndMinute else { return noChange }
        return FloorResult(startMinute: deferred, wasDeferred: true)
    }

    /// Convenience wrapper returning just the resolved start minute.
    public static func flooredStartMinute(
        currentStartMinute: Int,
        durationMinutes: Int,
        energyLevel: Int,
        readiness: SleepReadiness,
        isSameDay: Bool,
        hasUserPreferredStart: Bool = false,
        dayEndMinute: Int = 23 * 60
    ) -> Int {
        applyReadinessFloor(
            currentStartMinute: currentStartMinute,
            durationMinutes: durationMinutes,
            energyLevel: energyLevel,
            readiness: readiness,
            isSameDay: isSameDay,
            hasUserPreferredStart: hasUserPreferredStart,
            dayEndMinute: dayEndMinute
        ).startMinute
    }
}
