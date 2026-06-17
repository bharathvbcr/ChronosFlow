import Foundation

// Dial drag resolution — the pure math behind dragging a block on the Chronos Dial to move or
// resize it, with grid snapping and clamping. Backs the app's DialDragMode gestures; kept here so
// the fiddly wrap/clamp arithmetic is unit-tested off-device.

public struct DialEdit: Sendable, Equatable {
    public let startMinute: Int
    public let durationMinutes: Int
}

private func norm(_ m: Int) -> Int { ((m % 1440) + 1440) % 1440 }
private func snap(_ m: Int, _ grid: Int) -> Int {
    let g = max(grid, 1)
    return ((norm(m) + g / 2) / g * g) % 1440
}
/// Forward distance from `start` to `end` around the 1440-minute clock (always 1...1440).
private func forwardSpan(from start: Int, to end: Int) -> Int {
    let s = norm(start), e = norm(end)
    let span = e - s
    return span > 0 ? span : span + 1440
}

/// Move a block by `deltaMinutes`, snapping the new start to the grid. Duration is preserved.
public func dragMove(currentStart: Int, durationMinutes: Int, deltaMinutes: Int, grid: Int = 15) -> DialEdit {
    DialEdit(startMinute: snap(currentStart + deltaMinutes, grid),
             durationMinutes: min(max(durationMinutes, 1), 1440))
}

/// Resize by dragging the end edge to `targetEndMinute`. Start is fixed; duration is the snapped
/// forward span, clamped to [minDuration, 1440].
public func dragResizeEnd(currentStart: Int, targetEndMinute: Int, grid: Int = 15, minDuration: Int = 15) -> DialEdit {
    let snappedEnd = snap(targetEndMinute, grid)
    let duration = min(max(forwardSpan(from: currentStart, to: snappedEnd), minDuration), 1440)
    return DialEdit(startMinute: norm(currentStart), durationMinutes: duration)
}

/// Resize by dragging the start edge to `targetStartMinute`. End is fixed; start snaps and duration
/// is the forward span from the new start to the old end, clamped to [minDuration, 1440].
public func dragResizeStart(currentStart: Int, durationMinutes: Int, targetStartMinute: Int,
                            grid: Int = 15, minDuration: Int = 15) -> DialEdit {
    let oldEnd = norm(currentStart + durationMinutes)
    let newStart = snap(targetStartMinute, grid)
    let duration = forwardSpan(from: newStart, to: oldEnd)
    // If snapping pushed start past the end, keep the minimum duration anchored to the end.
    let clamped = min(max(duration, minDuration), 1440)
    return DialEdit(startMinute: newStart, durationMinutes: clamped)
}
