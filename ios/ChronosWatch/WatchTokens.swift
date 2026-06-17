import SwiftUI

// MARK: - Watch design-token shims
//
// The iOS app's `ChronosTokens.swift` is NOT compiled into the watch target (the watch shares only
// `Models` + the snapshot DTOs). These are a minimal copy of the few brand/category values the watch
// UI uses, kept in sync by hand with `ChronosColors` in the iOS app. Keep this small.

enum WatchTokens {
    // Brand fallback palette (violet / teal / coral) — mirrors ChronosColors.
    static let brandPrimary = Color(red: 0.45, green: 0.36, blue: 0.93)   // violet
    static let brandSecondary = Color(red: 0.16, green: 0.72, blue: 0.69) // teal
    static let brandAccent = Color(red: 0.98, green: 0.45, blue: 0.40)    // coral

    /// Semantic block-category accents — mirrors `ChronosColors.category(_:)`.
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
}

enum WatchSpacing {
    static let micro: CGFloat = 4
    static let small: CGFloat = 8
    static let compact: CGFloat = 12
}
