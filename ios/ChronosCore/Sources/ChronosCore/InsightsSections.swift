import Foundation

// Insights section-filter model — Foundation-only port of Android's
// feature/daydial/ui/InsightsSectionFilter.kt. The Review (Insights) page is split into five
// filterable sections rendered top-to-bottom; quick-filter pills toggle them. The selection is a
// set of raw section names (mirrors Android's rememberSaveable Set<String>): an empty set means
// "no filter" so every section shows.

// MARK: - Section

/// The filterable sections of the Review (Insights) page, in render order. The pill order and the
/// `rawValue` strings match Android's `InsightsSection` enum constants (`.name`) one-for-one so the
/// persisted selection set is byte-compatible across platforms.
public enum InsightsSection: String, CaseIterable, Sendable, Identifiable {
    case execution = "EXECUTION"
    case categories = "CATEGORIES"
    case insights = "INSIGHTS"
    case screenTime = "SCREEN_TIME"
    case trends = "TRENDS"

    public var id: String { rawValue }

    /// User-facing pill text. Mirrors Android's `label`.
    public var label: String {
        switch self {
        case .execution: return "Execution"
        case .categories: return "Categories"
        case .insights: return "Insights"
        case .screenTime: return "Screen time"
        case .trends: return "Trends"
        }
    }
}

// MARK: - Visibility

/// Whether `section` should render given the set of `selected` pill names. An empty selection means
/// "no filter" — every section shows; otherwise only the chosen sections appear. Mirrors Android's
/// `insightsSectionVisible(section, selected)`.
///
/// `selected` is a set of raw section names (`InsightsSection.rawValue`); unknown names are simply
/// ignored, matching Android's `section.name in selected` membership check.
public func insightsSectionVisible(section: InsightsSection, selected: Set<String>) -> Bool {
    selected.isEmpty || selected.contains(section.rawValue)
}

/// Convenience overload that accepts a typed selection set.
public func insightsSectionVisible(section: InsightsSection, selected: Set<InsightsSection>) -> Bool {
    selected.isEmpty || selected.contains(section)
}
