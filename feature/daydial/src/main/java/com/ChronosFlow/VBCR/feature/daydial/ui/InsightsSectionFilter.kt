package com.ChronosFlow.VBCR.feature.daydial.ui

/**
 * The filterable sections of the Review (Insights) page, in render order. The quick-filter pills
 * toggle these by [name]; [label] is the user-facing pill text. Order here is the pill order and is
 * asserted by tests, so keep it aligned with how the sections appear down the page.
 */
internal enum class InsightsSection(val label: String) {
    EXECUTION("Execution"),
    CATEGORIES("Categories"),
    INSIGHTS("Insights"),
    SCREEN_TIME("Screen time"),
    TRENDS("Trends")
}

/**
 * Whether [section] should render given the set of [selected] pill names. An empty selection means
 * "no filter" — every section shows; otherwise only the chosen sections appear.
 */
internal fun insightsSectionVisible(section: InsightsSection, selected: Set<String>): Boolean =
    selected.isEmpty() || section.name in selected
