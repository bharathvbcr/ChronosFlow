import Foundation

// Portable Chronos-Dial *ring layering* math — the platform-agnostic half of the iOS dial's
// 3-ring face. Faithful port of `feature/daydial/.../ChronosDial.kt` (`ringForBlock`,
// `ringRadius`) and `dial/ChronosDialRenderModel.kt` (night-band + free-time segment building).
//
// Depends only on Foundation (no SwiftUI / CoreGraphics), so it builds + unit-tests on any OS.
// The SwiftUI `Canvas` in the app draws the arcs these functions produce.

/// Which of the three concentric lanes a block sits on. Mirrors Android's `DialRing`
/// (the OUTER / MIDDLE / INNER subset that actually carries blocks).
public enum DialBlockRing: String, Sendable, Equatable, CaseIterable {
    case outer   // fixed commitments — imported calendar events
    case middle  // the plan — manual / routine / AI blocks
    case inner   // actions — tasks, habits, medications
}

/// Radius fraction (of the dial's outer radius) at which each ring's *centre line* is stroked.
/// Matches Android's `OUTER/MIDDLE/INNER_RING_RADIUS_FRACTION` (0.98 / 0.72 / 0.40).
public enum DialRingRadius {
    public static let outer: Double = 0.98
    public static let middle: Double = 0.72
    public static let inner: Double = 0.40

    public static func fraction(for ring: DialBlockRing) -> Double {
        switch ring {
        case .outer: return outer
        case .middle: return middle
        case .inner: return inner
        }
    }
}

/// The block fields ring-routing depends on, reduced to plain values so the classifier is pure.
/// `provenance` / `category` are compared case-insensitively, mirroring the Android string match.
public struct RingBlockInput: Sendable, Equatable {
    public let provenance: String
    public let category: String
    public let hasTaskLink: Bool
    public let hasHabitLink: Bool
    public let hasMedicationLink: Bool
    public let hasCalendarLink: Bool

    public init(
        provenance: String,
        category: String,
        hasTaskLink: Bool = false,
        hasHabitLink: Bool = false,
        hasMedicationLink: Bool = false,
        hasCalendarLink: Bool = false
    ) {
        self.provenance = provenance
        self.category = category
        self.hasTaskLink = hasTaskLink
        self.hasHabitLink = hasHabitLink
        self.hasMedicationLink = hasMedicationLink
        self.hasCalendarLink = hasCalendarLink
    }
}

/// Route a block to its ring. Mirrors Android `ringForBlock`:
///   calendar (link / provenance / category) → outer;
///   task / habit / medication link, or ROUTINE / MEDICATION category → inner;
///   everything else (the plan: manual / AI / routine-with-no-action-link) → middle.
public func ringForBlock(_ block: RingBlockInput) -> DialBlockRing {
    let provenance = block.provenance.lowercased()
    let category = block.category.lowercased()

    if block.hasCalendarLink || provenance == "calendar" || category == "calendar" {
        return .outer
    }
    if block.hasTaskLink || block.hasHabitLink || block.hasMedicationLink
        || provenance == "task" || provenance == "habit" || provenance == "medication"
        || category == "routine" || category == "medication" {
        return .inner
    }
    return .middle
}

// MARK: - Arc segments (start minute + duration on the 1440-minute circle)

/// A span on the dial expressed in minutes-of-day. `durationMinutes` is the forward sweep
/// (1...1440), so an overnight span (e.g. 21:00→07:00) is one segment that wraps midnight.
public struct DialArcSegment: Sendable, Equatable {
    public let startMinute: Int
    public let durationMinutes: Int
    public init(startMinute: Int, durationMinutes: Int) {
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
    }
    /// End minute on the circle (wraps past midnight).
    public var endMinute: Int { (startMinute + durationMinutes) % 1440 }
}

private let minutesPerDay = 1440

private func wrapMinute(_ minute: Int) -> Int {
    ((minute % minutesPerDay) + minutesPerDay) % minutesPerDay
}

/// Forward distance start→end on the 1440 circle, always 1...1440 (a full lap when equal).
private func circularDuration(from start: Int, to end: Int) -> Int {
    let d = (wrapMinute(end) - wrapMinute(start) + minutesPerDay) % minutesPerDay
    return d == 0 ? minutesPerDay : d
}

/// The sleep window as a single (possibly midnight-wrapping) night-band arc. Returns `nil`
/// when the window is empty (start == end). Mirrors Android's `nightArcs` (non-windowed case).
public func nightBandSegment(startMinute: Int, endMinute: Int) -> DialArcSegment? {
    guard wrapMinute(startMinute) != wrapMinute(endMinute) else { return nil }
    return DialArcSegment(
        startMinute: wrapMinute(startMinute),
        durationMinutes: circularDuration(from: startMinute, to: endMinute)
    )
}

/// Compute free-time arc segments: the daytime gaps between busy blocks, with the sleep/night
/// window subtracted so "open time" never paints over the night band (Android's
/// `subtractNightFromFreeSegments` semantics, but starting from busy blocks rather than
/// pre-computed free ranges so this is self-contained and matches `PlannerMath.freeWindows`).
///
/// - Parameters:
///   - busy: scheduled spans (start minute + duration). Only the busy minutes matter.
///   - dayStart / dayEnd: the planning window (default 6:00…23:00, matching `PlannerMath`).
///   - minDuration: drop free slivers shorter than this.
///   - night: optional sleep window to carve out; pass `nil` to keep all daytime gaps.
/// - Returns: free segments sorted by start minute, each at least `minDuration` long.
public func freeTimeSegments(
    busy: [DialArcSegment],
    dayStart: Int = 6 * 60,
    dayEnd: Int = 23 * 60,
    minDuration: Int = 30,
    night: DialArcSegment? = nil
) -> [DialArcSegment] {
    // 1. Mark every busy minute (and the night window) as unavailable on a [dayStart, dayEnd) mask.
    guard dayEnd > dayStart else { return [] }
    var free = [Bool](repeating: true, count: dayEnd - dayStart)

    func markBusy(start: Int, duration: Int) {
        guard duration > 0 else { return }
        // Project the span onto the linear [dayStart, dayEnd) axis. Spans that wrap midnight are
        // handled by walking each minute through wrapMinute and clamping into the day window.
        for offset in 0..<min(duration, minutesPerDay) {
            let m = wrapMinute(start + offset)
            if m >= dayStart && m < dayEnd { free[m - dayStart] = false }
        }
    }

    for span in busy { markBusy(start: span.startMinute, duration: span.durationMinutes) }
    if let night { markBusy(start: night.startMinute, duration: night.durationMinutes) }

    // 2. Emit maximal runs of free minutes, keeping only those >= minDuration.
    var segments: [DialArcSegment] = []
    var runStart = -1
    for index in 0...free.count {
        let isFree = index < free.count && free[index]
        if isFree {
            if runStart == -1 { runStart = index }
        } else if runStart != -1 {
            let length = index - runStart
            if length >= minDuration {
                segments.append(DialArcSegment(startMinute: dayStart + runStart, durationMinutes: length))
            }
            runStart = -1
        }
    }
    return segments
}
