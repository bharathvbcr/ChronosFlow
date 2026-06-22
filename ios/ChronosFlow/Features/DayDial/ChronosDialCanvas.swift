import SwiftUI
import ChronosCore

/// The 24-hour Chronos Dial rendered with SwiftUI `Canvas`. The iOS port of the Android
/// `feature/daydial` Compose canvas, driven by the shared `DialGeometry`.
///
/// Density (matching Android): three concentric block lanes — **outer** = calendar commitments,
/// **middle** = the plan, **inner** = task/habit/medication actions — over a soft indigo
/// **night band** for the sleep window, **dashed free-time arcs** for open daytime, and **dashed
/// error-tinted conflict overlays** on double-booked blocks. The selected block shows drag
/// **handles** at each edge, and an active drag renders a **live snapped preview** of the
/// move/resize before committing on release (with light snap haptics, reduce-motion aware).
struct ChronosDialCanvas: View {
    let blocks: [TimeBlock]
    let nowMinute: Int
    var selectedBlockID: String?
    var overlayEvents: [CalendarOverlayEvent] = []
    /// IDs of blocks involved in a conflict — drawn with a dashed error overlay. Computed upstream
    /// in `DayDialScreen` (`PlannerMath.conflicts`).
    var conflictBlockIDs: Set<String> = []
    /// Sleep window for the night band, in minute-of-day. Default 21:00→07:00 mirrors Android.
    var nightStartMinute: Int = 21 * 60
    var nightEndMinute: Int = 7 * 60
    var onTapBlock: (TimeBlock) -> Void = { _ in }
    /// Called when a block becomes the active drag target, so the screen can show its handles.
    var onSelectBlock: (_ blockID: String) -> Void = { _ in }
    var onCreate: (_ minute: Int) -> Void = { _ in }
    /// Commit a drag: new start + duration for the dragged block (called on drag end).
    var onAdjustBlock: (_ block: TimeBlock, _ newStart: Int, _ newDuration: Int) -> Void = { _, _, _ in }

    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    private let geometry = DialGeometry()
    private let grid = 15
    private let edgeTolerance = 18   // minutes near a block edge that start a resize

    private enum DragKind { case move, resizeStart, resizeEnd }
    @State private var dragBlockID: String?
    @State private var dragKind: DragKind?
    @State private var dragAnchorMinute = 0
    /// Live, snapped preview of the dragged block while a drag is in flight (nil = no drag).
    @State private var previewStart: Int?
    @State private var previewDuration: Int?
    @State private var lastSnappedMinute = -1
    /// True while the live preview window would overlap another block — drives a warning tint on the
    /// dragged arc (mirrors Android's previewMove → Conflict result feedback). Recomputed each move
    /// via `ChronosCore.PlannerMath.conflicts` over the other blocks + the candidate preview.
    @State private var previewHasConflict = false

    private var motionDisabled: Bool {
        ChronosSettings.shared.motionDisabled(systemReduceMotion)
    }

    var body: some View {
        GeometryReader { proxy in
            let size = min(proxy.size.width, proxy.size.height)
            let center = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            let maxRadius = Double(size) / 2 - 8

            ZStack {
                Canvas { context, _ in
                    drawNightBand(&context, center: center, maxRadius: maxRadius)
                    drawHourTicks(&context, center: center, maxRadius: maxRadius)
                    drawFreeTime(&context, center: center, maxRadius: maxRadius)
                    drawOverlayEvents(&context, center: center, maxRadius: maxRadius)
                    drawBlocks(&context, center: center, maxRadius: maxRadius)
                    drawConflicts(&context, center: center, maxRadius: maxRadius)
                    drawSelectionHandles(&context, center: center, maxRadius: maxRadius)
                    drawNowHand(&context, center: center, maxRadius: maxRadius)
                }
                centerSummary
            }
            .contentShape(Circle())
            .gesture(
                SpatialTapGesture()
                    .onEnded { value in handleTap(at: value.location, center: center, maxRadius: maxRadius) }
            )
            .gesture(
                DragGesture(minimumDistance: 12)
                    .onChanged { value in updateDrag(at: value, center: center, maxRadius: maxRadius) }
                    .onEnded { value in endDrag(at: value.location, center: center, maxRadius: maxRadius) }
            )
        }
        .aspectRatio(1, contentMode: .fit)
    }

    // MARK: Drawing

    /// A soft indigo arc over the sleep window, beneath the blocks, so the night reads as part of
    /// the dial face. Drawn across all three lanes' span for a full-depth band.
    private func drawNightBand(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        guard let night = nightBandSegment(startMinute: nightStartMinute, endMinute: nightEndMinute) else { return }
        let arc = geometry.blockToArc(startMinute: night.startMinute, durationMinutes: night.durationMinutes)
        let inner = geometry.ringBand(for: .inner, maxRadius: maxRadius).inner
        let outer = geometry.ringBand(for: .outer, maxRadius: maxRadius).outer
        let r = (inner + outer) / 2
        var path = Path()
        path.addArc(center: center, radius: r,
                    startAngle: .degrees(arc.startAngleDegrees),
                    endAngle: .degrees(arc.startAngleDegrees + arc.sweepDegrees),
                    clockwise: false)
        context.stroke(path, with: .color(ChronosColors.brandPrimary.opacity(0.22)),
                       style: StrokeStyle(lineWidth: outer - inner, lineCap: .butt))
    }

    private func drawHourTicks(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        for hour in 0..<24 {
            let minute = hour * 60
            let angle = geometry.minuteToAngle(minute) * .pi / 180
            let isMajor = hour % 6 == 0
            let outer = maxRadius
            let inner = maxRadius - (isMajor ? 14 : 8)
            var path = Path()
            path.move(to: point(center, angle, outer))
            path.addLine(to: point(center, angle, inner))
            context.stroke(path, with: .color(.secondary.opacity(isMajor ? 0.7 : 0.35)),
                           lineWidth: isMajor ? 2 : 1)
            if isMajor {
                let label = Text("\(hour)").font(.caption2).foregroundStyle(.secondary)
                context.draw(label, at: point(center, angle, inner - 12))
            }
        }
    }

    /// Open daytime gaps as dashed teal arcs on the middle lane — "free to plan" at a glance,
    /// with the night window carved out so they never overlap the night band.
    private func drawFreeTime(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        let busy = blocks.map { DialArcSegment(startMinute: $0.startMinuteOfDay, durationMinutes: $0.durationMinutes) }
        let night = nightBandSegment(startMinute: nightStartMinute, endMinute: nightEndMinute)
        let segments = freeTimeSegments(busy: busy, night: night)
        guard !segments.isEmpty else { return }
        let r = geometry.radius(for: .middle, maxRadius: maxRadius)
        for seg in segments {
            let arc = geometry.blockToArc(startMinute: seg.startMinute, durationMinutes: seg.durationMinutes)
            var path = Path()
            path.addArc(center: center, radius: r,
                        startAngle: .degrees(arc.startAngleDegrees),
                        endAngle: .degrees(arc.startAngleDegrees + arc.sweepDegrees),
                        clockwise: false)
            context.stroke(path, with: .color(ChronosColors.brandSecondary.opacity(0.32)),
                           style: StrokeStyle(lineWidth: geometry.laneWidth(maxRadius: maxRadius) * 0.34,
                                              lineCap: .round, dash: [4, 7]))
        }
    }

    private func drawBlocks(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        let lineWidth = geometry.laneWidth(maxRadius: maxRadius)
        for block in blocks {
            let ring = geometry.ring(for: block)
            let r = geometry.radius(for: ring, maxRadius: maxRadius)
            let selected = block.id == selectedBlockID
            // While dragging the selected block, render its live snapped preview instead.
            let start = selected ? (previewStart ?? block.startMinuteOfDay) : block.startMinuteOfDay
            let duration = selected ? (previewDuration ?? block.durationMinutes) : block.durationMinutes
            let arc = geometry.blockToArc(startMinute: start, durationMinutes: duration, ring: ring)
            var path = Path()
            path.addArc(center: center, radius: r,
                        startAngle: .degrees(arc.startAngleDegrees),
                        endAngle: .degrees(arc.startAngleDegrees + arc.sweepDegrees),
                        clockwise: false)
            // Warn (error tint) when the live preview of the dragged block would collide; otherwise
            // the block's normal category color, full-opacity while selected.
            let warning = selected && dragKind != nil && previewHasConflict
            let color = warning ? ChronosColors.brandAccent : ChronosColors.category(block.category)
            context.stroke(path, with: .color(color.opacity(selected ? 1 : 0.85)),
                           style: StrokeStyle(lineWidth: lineWidth - (selected ? 0 : 3),
                                              lineCap: .round))
        }
    }

    /// Dashed error-tinted arc over each conflicting block so double-booked time is visible at a
    /// glance, drawn on the block's own lane over the solid arc.
    private func drawConflicts(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        guard !conflictBlockIDs.isEmpty else { return }
        for block in blocks where conflictBlockIDs.contains(block.id) {
            let ring = geometry.ring(for: block)
            let r = geometry.radius(for: ring, maxRadius: maxRadius)
            let arc = geometry.blockToArc(startMinute: block.startMinuteOfDay,
                                          durationMinutes: block.durationMinutes, ring: ring)
            var path = Path()
            path.addArc(center: center, radius: r,
                        startAngle: .degrees(arc.startAngleDegrees),
                        endAngle: .degrees(arc.startAngleDegrees + arc.sweepDegrees),
                        clockwise: false)
            context.stroke(path, with: .color(ChronosColors.brandAccent.opacity(0.85)),
                           style: StrokeStyle(lineWidth: geometry.laneWidth(maxRadius: maxRadius) * 0.30,
                                              lineCap: .round, dash: [5, 5]))
        }
    }

    /// Handle dots at the start/end edges of the selected block (on its lane), and a faint radial
    /// indicator to the live drag minute while a drag is in flight.
    private func drawSelectionHandles(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        guard let id = selectedBlockID, let block = blocks.first(where: { $0.id == id }) else { return }
        let ring = geometry.ring(for: block)
        let r = geometry.radius(for: ring, maxRadius: maxRadius)
        let start = previewStart ?? block.startMinuteOfDay
        let duration = previewDuration ?? block.durationMinutes
        let end = (start + duration) % 1440

        for minute in [start, end] {
            let angle = geometry.minuteToAngle(minute) * .pi / 180
            let p = point(center, angle, r)
            let halo = CGRect(x: p.x - 11, y: p.y - 11, width: 22, height: 22)
            context.fill(Circle().path(in: halo), with: .color(ChronosColors.brandPrimary.opacity(0.18)))
            let dot = CGRect(x: p.x - 6, y: p.y - 6, width: 12, height: 12)
            context.fill(Circle().path(in: dot), with: .color(.primary))
        }

        // Live radial indicator from hub to the snapped drag minute.
        if dragKind != nil, lastSnappedMinute >= 0 {
            let angle = geometry.minuteToAngle(lastSnappedMinute) * .pi / 180
            let tip = point(center, angle, geometry.radius(for: .outer, maxRadius: maxRadius))
            var line = Path()
            line.move(to: center)
            line.addLine(to: tip)
            context.stroke(line, with: .color(ChronosColors.brandPrimary.opacity(0.28)),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round))
            let tipDot = CGRect(x: tip.x - 4, y: tip.y - 4, width: 8, height: 8)
            context.fill(Circle().path(in: tipDot), with: .color(ChronosColors.brandPrimary))
        }
    }

    /// Calendar events drawn as a thin dashed ghost ring on the outer lane — visible fixed
    /// commitments to plan around, never written as blocks until the user imports them.
    private func drawOverlayEvents(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        guard !overlayEvents.isEmpty else { return }
        let r = geometry.radius(for: .outer, maxRadius: maxRadius)
        for event in overlayEvents {
            let arc = geometry.blockToArc(startMinute: event.startMinute,
                                          durationMinutes: event.durationMinutes, ring: .outer)
            var path = Path()
            path.addArc(center: center, radius: r,
                        startAngle: .degrees(arc.startAngleDegrees),
                        endAngle: .degrees(arc.startAngleDegrees + arc.sweepDegrees),
                        clockwise: false)
            context.stroke(path, with: .color(.secondary.opacity(0.7)),
                           style: StrokeStyle(lineWidth: 5, lineCap: .round, dash: [2, 4]))
        }
    }

    private func drawNowHand(_ context: inout GraphicsContext, center: CGPoint, maxRadius: Double) {
        let angle = geometry.minuteToAngle(nowMinute) * .pi / 180
        var path = Path()
        path.move(to: center)
        path.addLine(to: point(center, angle, maxRadius - 4))
        context.stroke(path, with: .color(ChronosColors.brandAccent),
                       style: StrokeStyle(lineWidth: 2, lineCap: .round))
        let dot = CGRect(x: center.x - 4, y: center.y - 4, width: 8, height: 8)
        context.fill(Circle().path(in: dot), with: .color(ChronosColors.brandAccent))
    }

    private var centerSummary: some View {
        VStack(spacing: 2) {
            Text(nowMinute.clockTime)
                .font(.chronosTitle)
                .monospacedDigit()
            Text("\(blocks.count) blocks")
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: Hit testing

    private func handleTap(at location: CGPoint, center: CGPoint, maxRadius: Double) {
        let hit = geometry.hitTest(point: location, center: center, maxRadius: maxRadius)
        guard case let .ring(ring, minute) = hit else { return }
        if let block = block(at: minute, ring: ring) {
            onTapBlock(block)
        } else {
            onCreate(geometry.snap(minute, grid: grid))
        }
    }

    /// The block under `minute` on `ring` — only blocks routed to that lane count, so the three
    /// rings can carry overlapping times without ambiguity (matches Android `findBlockAtMinute`).
    private func block(at minute: Int, ring: DialRing) -> TimeBlock? {
        blocks.first { block in
            guard geometry.ring(for: block) == ring else { return false }
            let end = block.startMinuteOfDay + block.durationMinutes
            if end <= 1440 { return minute >= block.startMinuteOfDay && minute < end }
            return minute >= block.startMinuteOfDay || minute < (end % 1440)
        }
    }

    // MARK: Drag to move / resize with live preview (math mirrors ChronosCore.DialDrag, unit-tested)

    // BENCHMARK-FIRST (DD03 latency): each pointer event runs geometry.hitTest, an O(n) block lookup,
    // and updatePreviewConflict's O(n) conflict scan. For a day's worth of blocks (tens) this is
    // negligible. iOS is single-process SwiftData (no Android-style WAL contention), and we only
    // persist on drag *end*, not per event — so no drag-session cache is added here unless on-device
    // profiling shows a real cost at high block counts.
    private func updateDrag(at value: DragGesture.Value, center: CGPoint, maxRadius: Double) {
        if dragKind == nil { beginDrag(at: value.startLocation, center: center, maxRadius: maxRadius) }
        guard let kind = dragKind, let id = dragBlockID,
              let block = blocks.first(where: { $0.id == id }),
              case let .ring(_, target) = geometry.hitTest(point: value.location, center: center, maxRadius: maxRadius)
        else { return }

        let snappedTarget = geometry.snap(target, grid: grid)
        switch kind {
        case .move:
            let delta = target - dragAnchorMinute
            let newStart = geometry.snap(block.startMinuteOfDay + delta, grid: grid)
            previewStart = newStart
            previewDuration = block.durationMinutes
            emitSnap(at: newStart)
        case .resizeEnd:
            previewStart = block.startMinuteOfDay
            previewDuration = forwardSpan(from: block.startMinuteOfDay, to: snappedTarget)
            emitSnap(at: snappedTarget)
        case .resizeStart:
            let oldEnd = block.plannedEndMinuteOfDay
            previewStart = snappedTarget
            previewDuration = forwardSpan(from: snappedTarget, to: oldEnd)
            emitSnap(at: snappedTarget)
        }
        updatePreviewConflict(for: id)
    }

    /// Recompute whether the dragged block's *preview* window overlaps any other block, reusing the
    /// shared `ChronosCore.PlannerMath.conflicts`. Cheap (≤ n spans) and pure — no persistence.
    private func updatePreviewConflict(for id: String) {
        guard let start = previewStart, let duration = previewDuration else { previewHasConflict = false; return }
        var spans = blocks
            .filter { $0.id != id }
            .map { ChronosCore.BlockSpan(id: $0.id, startMinute: $0.startMinuteOfDay, durationMinutes: $0.durationMinutes) }
        spans.append(ChronosCore.BlockSpan(id: id, startMinute: start, durationMinutes: duration))
        previewHasConflict = ChronosCore.PlannerMath.conflicts(in: spans).contains { $0.firstID == id || $0.secondID == id }
    }

    private func beginDrag(at start: CGPoint, center: CGPoint, maxRadius: Double) {
        guard case let .ring(ring, minute) = geometry.hitTest(point: start, center: center, maxRadius: maxRadius),
              let block = block(at: minute, ring: ring) else { return }
        dragBlockID = block.id
        dragAnchorMinute = minute
        lastSnappedMinute = -1
        onSelectBlock(block.id)
        let fromStart = abs(minute - block.startMinuteOfDay)
        let fromEnd = abs(block.plannedEndMinuteOfDay - minute)
        if fromStart <= edgeTolerance { dragKind = .resizeStart }
        else if fromEnd <= edgeTolerance { dragKind = .resizeEnd }
        else { dragKind = .move }
    }

    private func endDrag(at location: CGPoint, center: CGPoint, maxRadius: Double) {
        defer { dragKind = nil; dragBlockID = nil; previewStart = nil; previewDuration = nil; lastSnappedMinute = -1; previewHasConflict = false }
        guard let id = dragBlockID, dragKind != nil,
              let block = blocks.first(where: { $0.id == id }),
              let start = previewStart, let duration = previewDuration
        else { return }
        onAdjustBlock(block, start, duration)
    }

    /// Fire a light snap haptic when the snapped minute changes (reduce-motion suppresses it).
    private func emitSnap(at minute: Int) {
        guard minute != lastSnappedMinute else { return }
        lastSnappedMinute = minute
        if !motionDisabled {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }

    /// Forward distance around the 1440-minute clock, clamped to [15, 1440].
    private func forwardSpan(from start: Int, to end: Int) -> Int {
        let s = ((start % 1440) + 1440) % 1440
        let e = ((end % 1440) + 1440) % 1440
        let span = e - s
        return min(max(span > 0 ? span : span + 1440, 15), 1440)
    }

    private func point(_ center: CGPoint, _ angle: Double, _ radius: Double) -> CGPoint {
        CGPoint(x: center.x + CGFloat(cos(angle) * radius), y: center.y + CGFloat(sin(angle) * radius))
    }
}
