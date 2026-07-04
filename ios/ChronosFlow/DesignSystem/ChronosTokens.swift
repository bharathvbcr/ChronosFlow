import SwiftUI

// MARK: - Design tokens
//
// Ports the canonical token system described in docs/UX_PRINCIPLES.md. On iOS the
// "Material You first" rule maps to the system's semantic colors + the Liquid Glass
// surface treatment; the brand palette is the fallback / semantic-accent source, exactly
// as on Android. NEVER hardcode a hex literal in a view — add a token here instead.

enum ChronosSpacing {
    static let micro: CGFloat = 4
    static let small: CGFloat = 8
    static let compact: CGFloat = 12
    static let standard: CGFloat = 16
    static let medium: CGFloat = 24
    static let large: CGFloat = 32
    static let hero: CGFloat = 48
}

enum ChronosRadius {
    static let extraSmall: CGFloat = 8
    static let small: CGFloat = 12
    static let medium: CGFloat = 16
    static let large: CGFloat = 20
    static let extraLarge: CGFloat = 28
}

enum ChronosColors {
    // Brand fallback palette (violet / teal / coral) — see UX principles §2.
    static let brandPrimary = Color(red: 0.45, green: 0.36, blue: 0.93)   // violet
    static let brandSecondary = Color(red: 0.16, green: 0.72, blue: 0.69) // teal
    static let brandAccent = Color(red: 0.98, green: 0.45, blue: 0.40)    // coral

    /// Semantic block-category accents (consistent meaning across the whole app).
    static func category(_ name: String) -> Color {
        switch name.uppercased() {
        case "SLEEP": Color(red: 0.36, green: 0.40, blue: 0.78)
        case "WORK": Color(red: 0.20, green: 0.55, blue: 0.86)
        case "STUDY": Color(red: 0.46, green: 0.36, blue: 0.85)
        case "FOCUS": brandPrimary
        case "BREAK": Color(red: 0.30, green: 0.74, blue: 0.55)
        case "MEAL": Color(red: 0.95, green: 0.62, blue: 0.24)
        case "ROUTINE": brandSecondary
        case "HABIT": Color(red: 0.90, green: 0.36, blue: 0.58)
        case "MEDICATION": Color(red: 0.86, green: 0.27, blue: 0.34)
        case "EXERCISE", "WORKOUT": Color(red: 0.92, green: 0.45, blue: 0.20)
        default: brandAccent
        }
    }

    /// Semantic status indicators (connection / health / supply dots) — one meaning app-wide.
    static let success = Color(red: 0.30, green: 0.74, blue: 0.45) // green — online / healthy / done
    static let warning = Color(red: 0.95, green: 0.62, blue: 0.24) // amber — degraded / low supply
    static let danger = Color(red: 0.86, green: 0.27, blue: 0.34)  // red — offline / error / critical

    /// Readable on-color for brand-tinted fills (selected tab text, FAB glyph, filter chips).
    static let onBrand = Color.white

    /// Ambient "night" shading for the dial's sleep window — a quiet, desaturated cool slate so it
    /// reads as dimmed time-of-day rather than a third brand hue. Deliberately distinct from the
    /// SLEEP *category* indigo, which can sit on the same arc as an actual sleep block.
    static let nightBand = Color(red: 0.28, green: 0.31, blue: 0.44)

    /// Shared elevation shadow for floating shell chrome (nav bar, quick-add menu).
    static let shellShadow = Color.black.opacity(0.18)

    /// Provenance tints — where a block came from (manual/routine/ai/calendar…).
    static func provenance(_ p: BlockProvenance) -> Color {
        switch p {
        case .ai: brandPrimary
        case .calendar: Color(red: 0.20, green: 0.55, blue: 0.86)
        case .routine: brandSecondary
        case .medication: Color(red: 0.86, green: 0.27, blue: 0.34)
        default: .secondary
        }
    }
}

extension Font {
    // Type roles mirror ChronosTypography; built on the system Dynamic Type scale for a11y.
    static let chronosTitleLarge = Font.system(.largeTitle, design: .rounded, weight: .bold)
    static let chronosTitle = Font.system(.title2, design: .rounded, weight: .semibold)
    static let chronosHeadline = Font.system(.headline, design: .rounded)
    static let chronosBody = Font.system(.body, design: .rounded)
    static let chronosLabel = Font.system(.subheadline, design: .rounded, weight: .medium)
    static let chronosCaption = Font.system(.caption, design: .rounded)
}

// MARK: - Motion (bouncy springs — the iOS "liquid-glass" personality)

enum ChronosMotion {
    /// Smooth, no-overshoot spring for layout/page transitions.
    static let smooth = Animation.smooth(duration: 0.45)
    /// Bouncy spring for tactile press-and-release and emphasis.
    static let bouncy = Animation.bouncy(duration: 0.5, extraBounce: 0.15)
    /// Snappy spring for quick state flips (toggles, selection).
    static let snappy = Animation.snappy(duration: 0.32)
}
