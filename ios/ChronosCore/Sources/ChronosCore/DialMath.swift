import Foundation

/// Portable Chronos Dial math — the platform-agnostic half of the iOS `DialGeometry` (which adds
/// the CoreGraphics hit-testing). A faithful port of `core/domain/planner/DialGeometry.kt`:
/// minute-of-day 0 is at the top (-90°) and time advances clockwise over 1440 minutes.
public struct DialMath: Sendable {
    public init() {}

    public func minuteToAngle(_ minute: Int) -> Double {
        Double(normalize(minute)) / 1440.0 * 360.0 - 90.0
    }

    public func angleToMinute(_ angleDegrees: Double) -> Int {
        let normalized = ((angleDegrees + 90).truncatingRemainder(dividingBy: 360) + 360)
            .truncatingRemainder(dividingBy: 360)
        let minute = Int((normalized / 360.0 * 1440.0).rounded())
        return min(max(minute, 0), 1439)
    }

    /// Sweep, in degrees, for a block of `durationMinutes`.
    public func sweepDegrees(durationMinutes: Int) -> Double {
        Double(min(max(durationMinutes, 1), 1440)) / 1440.0 * 360.0
    }

    /// Snap a minute to the nearest grid increment (e.g. 5 or 15).
    public func snap(_ minute: Int, grid: Int) -> Int {
        let increment = max(grid, 1)
        return ((normalize(minute) + increment / 2) / increment * increment) % 1440
    }

    private func normalize(_ minute: Int) -> Int { ((minute % 1440) + 1440) % 1440 }
}

/// Format a minute-of-day as a localized clock time, e.g. 545 -> "9:05 AM".
public func clockTime(_ minute: Int) -> String {
    var comps = DateComponents()
    comps.hour = minute / 60
    comps.minute = minute % 60
    let date = Calendar.current.date(from: comps) ?? Date(timeIntervalSince1970: 0)
    let f = DateFormatter()
    f.timeStyle = .short
    return f.string(from: date)
}
