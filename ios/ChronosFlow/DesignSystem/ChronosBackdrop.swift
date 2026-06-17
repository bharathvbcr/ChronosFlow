import SwiftUI

/// The living backdrop the glass refracts over. Ported in spirit from `ChronosScreenBackdrop` /
/// `LiquidBackdrop`: alive but quiet, never competing with content, and it stops animating under
/// Reduce Motion (the iOS analogue of the Android reduced-motion guard).
struct ChronosBackdrop: View {
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.colorScheme) private var colorScheme
    private var settings: ChronosSettings { .shared }

    /// Motion is suppressed when EITHER the OS setting or the in-app preference asks for it.
    private var reduceMotion: Bool { settings.motionDisabled(systemReduceMotion) }
    /// The user-selected backdrop palette (Appearance settings). `.off` renders flat.
    private var style: BackdropStyle { settings.backdrop }

    var body: some View {
        TimelineView(.animation(minimumInterval: reduceMotion ? .infinity : 1.0 / 30.0, paused: reduceMotion)) { timeline in
            Canvas { context, size in
                let t = reduceMotion ? 0 : timeline.date.timeIntervalSinceReferenceDate * 0.05
                draw(in: &context, size: size, t: t)
            }
            .ignoresSafeArea()
        }
        .background(baseGradient.ignoresSafeArea())
    }

    private var baseGradient: LinearGradient {
        LinearGradient(
            colors: colorScheme == .dark
                ? [Color(white: 0.06), Color(white: 0.10)]
                : [Color(white: 0.97), Color(white: 0.93)],
            startPoint: .top, endPoint: .bottom
        )
    }

    private func draw(in context: inout GraphicsContext, size: CGSize, t: Double) {
        // Three drifting blobs in the selected palette's tints. `.off` → no blobs (flat gradient).
        let tints = style.blobColors
        guard !tints.isEmpty else { return }
        let positions: [(CGFloat, CGFloat)] = [(0.25, 0.20), (0.78, 0.30), (0.5, 0.80)]
        let blobs: [(Color, CGFloat, CGFloat)] = positions.enumerated().map { idx, pos in
            (tints[idx % tints.count], pos.0, pos.1)
        }
        for (i, blob) in blobs.enumerated() {
            let drift = CGFloat(sin(t + Double(i) * 2.1)) * size.width * 0.06
            let drift2 = CGFloat(cos(t * 0.8 + Double(i))) * size.height * 0.05
            let center = CGPoint(x: size.width * blob.1 + drift, y: size.height * blob.2 + drift2)
            let radius = size.width * 0.6
            let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            var blur = context
            blur.addFilter(.blur(radius: 90))
            blur.fill(Circle().path(in: rect), with: .color(blob.0.opacity(0.22)))
        }
    }
}
