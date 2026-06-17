import SwiftUI

// MARK: - Liquid Glass surfaces
//
// The Android app fakes "liquid glass" with Haze backdrop blur + a vibrancy border. On iOS 26+
// this is a first-class system material: `.glassEffect(_:in:)` and `GlassEffectContainer`.
// These wrappers are the iOS analogues of ChronosCardSurface / ChronosGlassPanel / ChronosGlassTopBar
// from core/ui — go through them, do not re-apply raw glass in screens (UX principles §3).

enum GlassTone {
    case quiet, standard, prominent
}

struct ChronosGlassCard<Content: View>: View {
    var tone: GlassTone = .standard
    var tint: Color? = nil
    @ViewBuilder var content: () -> Content

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: ChronosRadius.large, style: .continuous)
        content()
            .padding(ChronosSpacing.standard)
            .modifier(GlassBackground(tone: tone, tint: tint, shape: shape,
                                      reduceTransparency: reduceTransparency))
    }
}

/// A prominent floating panel (sheets, hero chrome).
struct ChronosGlassPanel<Content: View>: View {
    var tint: Color? = nil
    @ViewBuilder var content: () -> Content
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: ChronosRadius.extraLarge, style: .continuous)
        content()
            .padding(ChronosSpacing.medium)
            .modifier(GlassBackground(tone: .prominent, tint: tint, shape: shape,
                                      reduceTransparency: reduceTransparency))
    }
}

/// Applies the Liquid Glass effect with a graceful fallback when transparency is reduced
/// (the iOS equivalent of the Android high-contrast / glass-off path).
private struct GlassBackground<S: InsettableShape>: ViewModifier {
    let tone: GlassTone
    let tint: Color?
    let shape: S
    let reduceTransparency: Bool

    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(.regularMaterial, in: shape)
                .overlay(shape.strokeBorder(.separator, lineWidth: 1))
        } else {
            content
                .glassEffect(glass, in: shape)
        }
    }

    private var glass: Glass {
        var g: Glass = tone == .prominent ? .regular.interactive() : .regular
        if let tint { g = g.tint(tint.opacity(tone == .quiet ? 0.10 : 0.18)) }
        return g
    }
}

/// Tactile press-and-release feedback — the signature "everything you touch reacts" behaviour.
struct PressableScale: ViewModifier {
    @State private var pressed = false
    func body(content: Content) -> some View {
        content
            .scaleEffect(pressed ? 0.96 : 1)
            .animation(ChronosMotion.bouncy, value: pressed)
            .onLongPressGesture(minimumDuration: 0, pressing: { pressed = $0 }, perform: {})
    }
}

extension View {
    func pressable() -> some View { modifier(PressableScale()) }
}
