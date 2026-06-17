import CoreGraphics
import Foundation

// MARK: - Chronos Dial geometry
//
// Faithful port of `core/domain/planner/DialGeometry.kt`. The dial is a 24-hour radial
// planner: minute-of-day 0 sits at the top (12 o'clock = -90°) and time advances clockwise.

enum DialRing: Sendable {
    case outer, middle, inner, center, outside
}

struct DialArc: Sendable {
    let startAngleDegrees: Double
    let sweepDegrees: Double
    let ring: DialRing
}

enum DialHit: Sendable, Equatable {
    case ring(DialRing, minute: Int)
    case blockMove(blockID: String, minute: Int)
    case resizeStart(blockID: String, minute: Int)
    case resizeEnd(blockID: String, minute: Int)
    case outside
}

enum DialDragMode: Sendable, Equatable {
    case move(blockID: String, anchorMinute: Int)
    case resizeStart(blockID: String)
    case resizeEnd(blockID: String)
    case create(startMinute: Int)
}

struct DialGeometry: Sendable {
    var innerRadiusFraction: Double = 0.40
    var middleRadiusFraction: Double = 0.72
    var outerRadiusFraction: Double = 0.98

    func minuteToAngle(_ minute: Int) -> Double {
        Double(normalize(minute)) / 1440.0 * 360.0 - 90.0
    }

    func angleToMinute(_ angleDegrees: Double) -> Int {
        let normalized = ((angleDegrees + 90).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360)
        let minute = Int((normalized / 360.0 * 1440.0).rounded())
        return min(max(minute, 0), 1439)
    }

    func blockToArc(startMinute: Int, durationMinutes: Int, ring: DialRing = .middle) -> DialArc {
        DialArc(
            startAngleDegrees: minuteToAngle(startMinute),
            sweepDegrees: Double(min(max(durationMinutes, 1), 1440)) / 1440.0 * 360.0,
            ring: ring
        )
    }

    /// The arc for a `TimeBlock`, routed to its lane (outer=calendar, inner=actions, middle=plan)
    /// by the shared `ringForBlock` classifier instead of forcing `.middle`. Mirrors Android.
    func blockToArc(for block: TimeBlock) -> DialArc {
        blockToArc(startMinute: block.startMinuteOfDay,
                   durationMinutes: block.durationMinutes,
                   ring: ring(for: block))
    }

    /// Which lane a block rides (calendar→outer, task/habit/med→inner, else the plan→middle).
    /// Mirrors the portable `ChronosCore.ringForBlock` classifier (kept in lock-step; that one
    /// carries the unit tests). Mirrored here rather than imported so this file stays usable by
    /// the widget extension, which compiles `DialGeometry.swift` but does not link ChronosCore.
    func ring(for block: TimeBlock) -> DialRing {
        let provenance = block.provenance.rawValue.lowercased()
        let category = block.category.lowercased()
        if block.calendarEventID != nil || provenance == "calendar" || category == "calendar" {
            return .outer
        }
        if block.taskID != nil || block.habitID != nil || block.medicationPlanID != nil
            || provenance == "task" || provenance == "habit" || provenance == "medication"
            || category == "routine" || category == "medication" {
            return .inner
        }
        return .middle
    }

    func ring(forRadius distance: Double, maxRadius: Double) -> DialRing {
        let fraction = maxRadius <= 0 ? 0 : distance / maxRadius
        switch fraction {
        case let f where f > outerRadiusFraction: return .outside
        case let f where f > middleRadiusFraction: return .outer
        case let f where f > innerRadiusFraction: return .middle
        case let f where f > 0.08: return .inner
        default: return .center
        }
    }

    func hitTest(point: CGPoint, center: CGPoint, maxRadius: Double) -> DialHit {
        let dx = Double(point.x - center.x)
        let dy = Double(point.y - center.y)
        let ring = ring(forRadius: hypot(dx, dy), maxRadius: maxRadius)
        if ring == .outside { return .outside }
        let angle = atan2(dy, dx) * 180 / .pi
        return .ring(ring, minute: angleToMinute(angle))
    }

    /// Snap a minute to the nearest grid increment (e.g. 5 or 15 minutes).
    func snap(_ minute: Int, grid: Int) -> Int {
        let increment = max(grid, 1)
        return ((normalize(minute) + increment / 2) / increment * increment) % 1440
    }

    /// The point on `ring` at `minute`, for a dial centered at `center` with `maxRadius`.
    func point(minute: Int, ring: DialRing, center: CGPoint, maxRadius: Double) -> CGPoint {
        let r = radius(for: ring, maxRadius: maxRadius)
        let angle = minuteToAngle(minute) * .pi / 180
        return CGPoint(x: center.x + CGFloat(cos(angle) * r), y: center.y + CGFloat(sin(angle) * r))
    }

    /// Centre-line radius of `ring`'s block lane. Three concentric lanes whose centre lines sit at
    /// the Android `OUTER/MIDDLE/INNER_RING_RADIUS_FRACTION` (0.98 / 0.72 / 0.40) of `maxRadius`.
    func radius(for ring: DialRing, maxRadius: Double) -> Double {
        switch ring {
        case .outer: return maxRadius * outerRadiusFraction
        case .middle: return maxRadius * middleRadiusFraction
        case .inner: return maxRadius * innerRadiusFraction
        case .center, .outside: return 0
        }
    }

    /// The narrow stroke width of a block lane — thin enough that three lanes coexist without
    /// touching (Android strokes block arcs at ~0.42 of the full ring stroke).
    func laneWidth(maxRadius: Double) -> Double { maxRadius * 0.10 }

    func ringBand(for ring: DialRing, maxRadius: Double) -> (inner: Double, outer: Double) {
        let r = radius(for: ring, maxRadius: maxRadius)
        let half = laneWidth(maxRadius: maxRadius) / 2
        guard r > 0 else { return (0, 0) }
        return (r - half, r + half)
    }

    private func normalize(_ minute: Int) -> Int { ((minute % 1440) + 1440) % 1440 }
}

extension Int {
    /// Format a minute-of-day as a localized clock time, e.g. 545 -> "9:05 AM".
    var clockTime: String {
        let h = self / 60
        let m = self % 60
        var comps = DateComponents()
        comps.hour = h
        comps.minute = m
        let date = Calendar.current.date(from: comps) ?? .now
        let f = DateFormatter()
        f.timeStyle = .short
        return f.string(from: date)
    }
}
