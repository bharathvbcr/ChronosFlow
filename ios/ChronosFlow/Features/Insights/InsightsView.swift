import SwiftUI
import SwiftData
import Charts
import ChronosCore
import FoundationModels

/// The Review tab (== Insights page): period-switched execution rollups, per-category breakdown,
/// a severity-ranked findings list + AI recommendations, screen-time, plus habit / sleep / mood
/// trends. Ports the unified Review/Insights surface (the Insights tab IS the Review page; the
/// detailed planned/actual sheet is reachable from here).
///
/// Section model mirrors Android's `InsightsSection` (EXECUTION / CATEGORIES / INSIGHTS /
/// SCREEN_TIME / TRENDS) rendered top-to-bottom; quick-filter pills toggle them via
/// `insightsSectionVisible` (empty selection = show everything). Cards collapse via
/// `ChronosSettings.insightsCardCollapsed`. Recommendations come from
/// `InsightsRecommendationsPlanner` (deterministic local heuristics, optionally enriched by
/// Foundation Models). Period rollups + findings are computed by ChronosCore (unit-tested).
struct InsightsView: View {
    @Query private var blocks: [TimeBlock]
    @Query private var habits: [Habit]
    @Query private var nights: [SleepTrack]
    @Query private var checkIns: [MoodEnergyCheckIn]
    @Query private var medications: [MedicationPlan]
    @Query private var journalEntries: [JournalEntry]
    @Environment(\.modelContext) private var modelContext
    @State private var showingDayReview = false
    @State private var period: InsightsPeriod = .day

    // MARK: Journal / sleep affordances (Android: onOpenJournal / onOpenSleepLog on the trend cards)
    @State private var showingJournalEditor = false
    @State private var showingSleepLog = false
    /// Journal-history rows expanded in place (Android: JournalTimelineEntryRow's expand state).
    @State private var expandedJournalEntryIDs: Set<String> = []

    // MARK: Recommendation apply feedback — transient confirmation shown after tapping "Apply",
    // mirroring the snackbar message Android surfaces from `applyInsightRecommendation`.
    @State private var appliedRecommendationMessage: String?

    // MARK: Section filter state — empty set means "no filter" (all sections show), matching
    // Android's `rememberSaveable Set<String>`. Stored as raw section names so it's byte-compatible.
    @State private var selectedSections: Set<String> = []

    // MARK: AI recommendations
    @State private var recommendations: [InsightRecommendation] = []
    @State private var isRefreshingRecommendations = false

    private var settings: ChronosSettings { .shared }

    private var todayBlocks: [TimeBlock] {
        blocks.filter { Calendar.current.isDateInToday($0.date) }
    }

    private func sectionVisible(_ section: ChronosCore.InsightsSection) -> Bool {
        insightsSectionVisible(section: section, selected: selectedSections)
    }

    // MARK: Period rollup (ChronosCore.summarize)

    /// Aggregate the selected period off the portable analytics core.
    private var periodSummary: PeriodSummary {
        let inputs = blocks.map {
            InsightsBlock(
                date: $0.date,
                category: $0.category.uppercased(),
                plannedDuration: $0.durationMinutes,
                actualStart: $0.actualStartMinuteOfDay,
                actualEnd: $0.actualEndMinuteOfDay)
        }
        return summarize(blocks: inputs, period: period, anchorDate: .now, calendar: .current)
    }

    /// Severity-ranked findings (ChronosCore.deriveFindings) over the period + day signals.
    private var findings: [ReviewFinding] {
        let summary = periodSummary
        let windowBlocks = periodBlocks
        let demanding = windowBlocks.filter { $0.energyLevel.rawValue >= 4 }
            .reduce(0) { $0 + $1.durationMinutes }
        let total = windowBlocks.reduce(0) { $0 + $1.durationMinutes }
        let breakMin = windowBlocks.filter { ["BREAK", "MEAL", "RECOVERY"].contains($0.category.uppercased()) }
            .reduce(0) { $0 + $1.durationMinutes }
        let variety = Set(windowBlocks.map { $0.category.uppercased() }).count
        let context = FindingsContext(
            demandingMinutes: demanding,
            totalScheduledMinutes: total,
            categoryVariety: variety,
            breakRatio: total > 0 ? Double(breakMin) / Double(total) : 0,
            sleepReadiness: coreReadiness,
            medicationAdherence: medications.isEmpty ? nil : adherenceRate,
            bestHabitStreak: habits.map(\.streakCount).max() ?? 0)
        return deriveFindings(summary: summary, context: context)
    }

    /// Blocks within the selected period window (for context signals the summary doesn't expose).
    private var periodBlocks: [TimeBlock] {
        let cal = Calendar.current
        let endDay = cal.startOfDay(for: .now)
        let startDay = cal.date(byAdding: .day, value: -(period.days - 1), to: endDay) ?? endDay
        let endBound = cal.date(byAdding: .day, value: 1, to: endDay) ?? endDay
        return blocks.filter { $0.date >= startDay && $0.date < endBound }
    }

    /// ChronosCore readiness from the most recently logged night.
    private var coreReadiness: ChronosCore.SleepReadiness {
        let recent = nights.max { $0.date < $1.date }
        let app = deriveSleepReadiness(lastNight: recent)
        return ChronosCore.SleepReadiness(rawValue: app.rawValue) ?? .unknown
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    periodPicker
                    sectionFilterPills

                    // MARK: EXECUTION — rollup metrics + balance
                    if sectionVisible(.execution) {
                        periodSummaryCard
                        collapsibleCard(key: "balance", title: "Day balance") {
                            balanceCard
                        }
                    }

                    // MARK: CATEGORIES — per-category breakdown
                    if sectionVisible(.categories) {
                        categoryBreakdownCard
                    }

                    // MARK: INSIGHTS — findings + AI recommendations (eager render, matching Android)
                    if sectionVisible(.insights) {
                        findingsCard
                        recommendationsCard
                    }

                    // MARK: SCREEN_TIME
                    if sectionVisible(.screenTime) {
                        screenTimeCard
                    }

                    // MARK: TRENDS — habit / sleep / mood charts + derived signals. Matching Android's
                    // InsightsTrendSections, each trend renders inline and is gated ONLY on data
                    // availability (never on collapsed state) under one cohesive "Trends" heading.
                    if sectionVisible(.trends) {
                        trendsSection
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Review")
            .chronosScrollMinimizedBar()
            .sheet(isPresented: $showingDayReview) {
                DayReviewSheet(blocks: todayBlocks)
                    .presentationDetents([.large])
            }
            .sheet(isPresented: $showingJournalEditor) {
                JournalEditorSheet(editing: todayJournalEntry, initialDate: .now)
            }
            .sheet(isPresented: $showingSleepLog) {
                SleepLogSheet()
            }
            .task { refreshRecommendationsBaseline() }
            .onChange(of: period) { _, _ in refreshRecommendationsBaseline() }
        }
    }

    // MARK: TRENDS section body (extracted from `body` to keep the type-checker in budget)

    @ViewBuilder
    private var trendsSection: some View {
        Text("Trends").font(.chronosHeadline)
            .frame(maxWidth: .infinity, alignment: .leading)
        if !habits.isEmpty {
            habitChart
        }
        if nights.count >= 2 {
            sleepChart
        }
        if let best = bestWindow {
            bestWindowCard(best)
        }
        if checkIns.count >= 2 {
            moodChart
        }
        if let corr = sleepMoodCorrelation {
            correlationCard(corr)
        }
        if !medications.isEmpty, adherenceRate > 0 {
            adherenceCard
        }
        // Journal + sleep trend cards (Android: InsightsTrendSections lines 292–476).
        // The history card is data-gated; the reflection / sleep-log affordance cards
        // always show while their feature flag is on, mirroring Android's gating.
        if settings.journalEnabled {
            if !recentJournalEntries.isEmpty {
                journalHistoryCard
            }
            eveningReflectionCard
        }
        if settings.sleepEnabled {
            sleepLogCard
        }
    }

    // MARK: Section filter pills

    private var sectionFilterPills: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.compact) {
                ForEach(ChronosCore.InsightsSection.allCases) { section in
                    let selected = selectedSections.contains(section.rawValue)
                    Button {
                        if selected {
                            selectedSections.remove(section.rawValue)
                        } else {
                            selectedSections.insert(section.rawValue)
                        }
                    } label: {
                        Text(section.label)
                            .font(.chronosCaption)
                            .padding(.horizontal, ChronosSpacing.compact)
                            .padding(.vertical, 6)
                            .background(
                                selected
                                    ? ChronosColors.brandPrimary.opacity(0.18)
                                    : Color(.tertiarySystemFill),
                                in: Capsule()
                            )
                            .foregroundStyle(
                                selected ? ChronosColors.brandPrimary : .secondary
                            )
                            .overlay(
                                Capsule()
                                    .strokeBorder(
                                        selected ? ChronosColors.brandPrimary.opacity(0.5) : Color.clear,
                                        lineWidth: 1
                                    )
                            )
                    }
                    .buttonStyle(.plain)
                    .pressable()
                    .animation(ChronosMotion.snappy, value: selected)
                }
            }
            .padding(.vertical, ChronosSpacing.micro)
            .sensoryFeedback(.selection, trigger: selectedSections)
        }
    }

    // MARK: CollapsibleInsightsCard helper

    /// Wraps any card view in a DisclosureGroup whose collapsed state is persisted via
    /// `ChronosSettings.insightsCardCollapsed` (Android parity: `insights.collapsed.*` keys).
    @ViewBuilder
    private func collapsibleCard<Content: View>(
        key: String,
        title: String,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        DisclosureGroup(
            isExpanded: Binding(
                get: { !settings.insightsCardCollapsed(key) },
                set: { settings.setInsightsCardCollapsed(key, !$0) }
            )
        ) {
            content()
        } label: {
            Text(title)
                .font(.chronosLabel)
                .foregroundStyle(.secondary)
        }
        .tint(ChronosColors.brandPrimary)
        .padding(.vertical, 2)
    }

    // MARK: Period switcher

    private var periodPicker: some View {
        Picker("Period", selection: $period) {
            ForEach(InsightsPeriod.allCases, id: \.self) { p in
                Text(p.label).tag(p)
            }
        }
        .pickerStyle(.segmented)
    }

    // MARK: Period summary (rollup execution metrics)

    private var periodSummaryCard: some View {
        let s = periodSummary
        let scoreTint = executionScoreColor(s)
        return ChronosGlassPanel(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                // Title row mirrors Android's "Execution score" header + completion percent.
                HStack(alignment: .firstTextBaseline) {
                    Text("Execution score")
                        .font(.chronosHeadline)
                    Spacer()
                    Text(s.hasPlan ? "\(s.completionPercent)%" : "N/A")
                        .font(.chronosHeadline)
                        .foregroundStyle(scoreTint)
                }
                if s.hasPlan {
                    ProgressView(value: Double(s.completionPercent) / 100).tint(scoreTint)
                }
                HStack(spacing: ChronosSpacing.compact) {
                    metricStat("Planned", s.hasPlan ? formatMins(s.plannedMinutes) : "N/A")
                    metricStat("Actual",
                               s.hasPlan ? formatMins(s.actualMinutes)
                                   : (s.actualMinutes > 0 ? formatMins(s.actualMinutes) : "No logged time yet"),
                               tint: (s.hasPlan || s.actualMinutes > 0) ? ChronosColors.brandSecondary : .secondary)
                    metricStat("Missed", s.hasPlan ? formatMins(s.missedMinutes) : "N/A",
                               tint: s.hasPlan ? ChronosColors.brandAccent : .secondary)
                    metricStat("Drift", s.hasPlan ? formatDrift(s.driftMinutes) : "N/A",
                               tint: driftAccentColor(s))
                }
                // Missed-block summary (parity with Android's ExecutionSummaryRow), or a
                // create-plan prompt when nothing was scheduled in the window.
                if s.hasPlan {
                    Text(missedSummaryText(s))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    // Narrative + action, mirroring Android's "$narrative $action".
                    Text("\(executionNarrative(s)) \(executionAction(s))")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                } else {
                    Text("No plan set for this period")
                        .font(.chronosLabel)
                    Text("Create one now to unlock completion, drift, and missed-block analysis.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                if period != .day {
                    Text(period == .week
                         ? "Execution metrics cover the last 7 days."
                         : "Execution metrics cover the last 30 days.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                // "Open daily review" lives INSIDE the card (Android parity: onOpenFullReview),
                // not in the toolbar.
                Button {
                    showingDayReview = true
                } label: {
                    Label("Open daily review", systemImage: "eye")
                        .font(.chronosLabel)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(ChronosColors.brandPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Score color keyed off the completion percentage, mirroring Android's `scoreColor`.
    private func executionScoreColor(_ s: PeriodSummary) -> Color {
        guard s.hasPlan else { return .secondary }
        switch s.completionPercent {
        case 95...: return ChronosColors.brandPrimary
        case 80..<95: return ChronosColors.brandSecondary
        case 60..<80: return ChronosColors.brandAccent
        case 1..<60: return ChronosColors.brandAccent
        default: return .secondary
        }
    }

    /// Drift accent, mirroring Android's `driftAccent`.
    private func driftAccentColor(_ s: PeriodSummary) -> Color {
        guard s.hasPlan else { return .secondary }
        if s.driftMinutes > 0 { return ChronosColors.brandSecondary }
        if s.driftMinutes < 0 { return ChronosColors.brandAccent }
        return .secondary
    }

    /// Missed-blocks summary line, mirroring Android's `missedBlocksSummaryText`.
    private func missedSummaryText(_ s: PeriodSummary) -> String {
        switch max(s.missedCount, 0) {
        case 0: return "Missed blocks: none"
        case 1: return "Missed blocks: 1"
        default: return "Missed blocks: \(s.missedCount)"
        }
    }

    /// A short follow-through narrative keyed off the completion percentage, mirroring the Android
    /// execution-score copy thresholds (`executionScoreNarrative`).
    private func executionNarrative(_ s: PeriodSummary) -> String {
        guard s.hasPlan else { return "No plan is active for today." }
        switch s.completionPercent {
        case 95...: return "You are ahead of plan."
        case 80..<95: return "Execution is on track."
        case 60..<80: return "Execution drifted from plan."
        default: return "Execution is behind."
        }
    }

    /// The actionable follow-up paired with the narrative, mirroring Android's `executionScoreAction`.
    private func executionAction(_ s: PeriodSummary) -> String {
        guard s.hasPlan else { return "Create a plan to unlock completion and drift insights." }
        switch s.completionPercent {
        case 95...: return "Keep your current cadence and preserve block quality."
        case 80..<95: return "Tighten 10-minute estimates on one block to improve forecasting."
        case 60..<80: return "Cap your next work block to reduce variance and recover the plan."
        default: return "Prioritize your top two tasks and defer low-value blocks."
        }
    }

    private func metricStat(_ label: String, _ value: String, tint: Color = .primary) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.chronosCaption).foregroundStyle(.secondary)
            Text(value).font(.chronosLabel).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Category breakdown

    private var categoryBreakdownCard: some View {
        let s = periodSummary
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Category breakdown").font(.chronosHeadline)
                if s.categoryRows.isEmpty {
                    Text("No blocks tracked yet").font(.chronosBody).foregroundStyle(.secondary)
                    Button {
                        showingDayReview = true
                    } label: {
                        Label("Open daily review", systemImage: "eye")
                            .font(.chronosLabel)
                    }
                    .buttonStyle(.bordered)
                    .tint(ChronosColors.brandPrimary)
                } else {
                    if let top = s.topCategory {
                        Text("★ \(top.category) leads at \(percent(top.share)) · \(s.concentration.rawValue) concentration")
                            .font(.chronosCaption)
                            .foregroundStyle(concentrationColor(s.concentration))
                    }
                    ForEach(Array(s.categoryRows.enumerated()), id: \.element.category) { index, row in
                        let rowTint = index == 0 ? ChronosColors.brandSecondary : ChronosColors.brandPrimary
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(index == 0 ? "★ \(row.category)" : row.category)
                                    .font(.chronosLabel)
                                    .foregroundStyle(index == 0 ? rowTint : .primary)
                                Spacer()
                                Text("\(formatMins(row.minutes)) · \(percent(row.share))")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                            ProgressView(value: min(max(row.progress, 0), 1)).tint(rowTint)
                        }
                    }
                    // Actionable concentration tip (Android parity: focusConcentrationTip), keyed off
                    // the top category's share and the number of distinct categories.
                    if let top = s.topCategory,
                       let tip = focusConcentrationTip(share: top.share,
                                                       topCategory: top.category,
                                                       totalCategories: s.categoryRows.count) {
                        Text(tip)
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandPrimary)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Mirrors Android's `focusConcentrationTip`: context-switching guidance when a single category
    /// dominates the period. Returns nil when the spread is already healthy.
    private func focusConcentrationTip(share: Double, topCategory: String, totalCategories: Int) -> String? {
        if totalCategories == 1 {
            return "Only one category appears today. Add a short block in another category tomorrow to reduce concentration risk."
        }
        if share >= 0.85 {
            return "Very high focus on \(topCategory). Try splitting this category into two shorter focus blocks with a reset task in between."
        }
        if share >= 0.7 {
            return "You spent most of your day on \(topCategory). A short complementary block could improve context recovery."
        }
        return nil
    }

    private func concentrationColor(_ c: ConcentrationLabel) -> Color {
        switch c {
        case .high: return ChronosColors.brandAccent
        case .moderate: return ChronosColors.brandSecondary
        case .even, .balanced: return .secondary
        }
    }

    // MARK: Findings list

    private var findingsCard: some View {
        let items = findings
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Text("Review findings").font(.chronosHeadline)
                if items.isEmpty {
                    Text("No findings yet — log your day to surface insights.")
                        .font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    ForEach(items) { finding in
                        findingRow(finding)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func findingRow(_ f: ReviewFinding) -> some View {
        let tint = severityColor(f.severity)
        return HStack(alignment: .top, spacing: ChronosSpacing.compact) {
            Image(systemName: severityIcon(f.severity)).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(f.title).font(.chronosLabel)
                Text(f.detail).font(.chronosCaption).foregroundStyle(.secondary)
                Text(f.source.uppercased()).font(.chronosCaption).foregroundStyle(tint)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func severityColor(_ s: FindingSeverity) -> Color {
        switch s {
        case .critical: return ChronosColors.brandAccent
        case .warning: return ChronosColors.brandSecondary
        case .info: return ChronosColors.brandPrimary
        }
    }

    private func severityIcon(_ s: FindingSeverity) -> String {
        switch s {
        case .critical: return "exclamationmark.triangle.fill"
        case .warning: return "exclamationmark.circle.fill"
        case .info: return "info.circle.fill"
        }
    }

    // MARK: AI recommendations (ChronosCore.InsightsRecommendationsPlanner + Foundation Models)

    private var recommendationsCard: some View {
        ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                // Header row mirrors Android: section title left, outlined refresh button right with a
                // sparkles icon (spinner while refreshing).
                HStack {
                    Text("AI recommendations").font(.chronosHeadline)
                    Spacer()
                    Button {
                        Task { await refreshRecommendationsWithAI() }
                    } label: {
                        HStack(spacing: 6) {
                            if isRefreshingRecommendations {
                                ProgressView().controlSize(.small)
                            } else {
                                Image(systemName: "sparkles")
                            }
                            Text(isRefreshingRecommendations ? "Refreshing…" : "Refresh")
                                .font(.chronosLabel)
                        }
                    }
                    .buttonStyle(.bordered)
                    .clipShape(Capsule())
                    .disabled(isRefreshingRecommendations)
                }
                if let applied = appliedRecommendationMessage {
                    Text(applied)
                        .font(.chronosCaption)
                        .foregroundStyle(ChronosColors.brandSecondary)
                }
                if recommendations.isEmpty {
                    Text("Refresh to generate schedule recommendations from your review data.")
                        .font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    ForEach(recommendations) { rec in
                        recommendationRow(rec)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .sensoryFeedback(.success, trigger: appliedRecommendationMessage) { _, new in new != nil }
    }

    /// One recommendation row with text, source label, and an "Apply" action — mirrors Android's
    /// `RecommendationRow`.
    private func recommendationRow(_ rec: InsightRecommendation) -> some View {
        HStack(alignment: .top, spacing: ChronosSpacing.compact) {
            Image(systemName: "lightbulb.fill")
                .foregroundStyle(ChronosColors.brandSecondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(rec.text).font(.chronosLabel)
                Text(sourceLabel(rec.source)).font(.chronosCaption)
                    .foregroundStyle(ChronosColors.brandPrimary)
            }
            Spacer()
            Button("Apply") { applyRecommendation(rec) }
                .buttonStyle(.bordered)
                .clipShape(Capsule())
                .font(.chronosLabel)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Local interpretation of a recommendation into a quick action, mirroring Android's
    /// `applyInsightRecommendation` → `RecommendationQuickAction`. ADD_BREAK inserts a 15-minute break
    /// block on today; FILL_GAPS / OPEN_AI_PLAN surface a confirmation (the full planner-driven apply
    /// lives in the shared day context on Android). Provides the same "Apply" affordance and feedback.
    private func applyRecommendation(_ rec: InsightRecommendation) {
        let lower = rec.text.lowercased()
        if lower.contains("break") || lower.contains("rest") || lower.contains("recover") {
            insertBreakBlock()
            appliedRecommendationMessage = "Added a 15-minute break block"
        } else if lower.contains("fill") || lower.contains("gap") || lower.contains("empty") {
            appliedRecommendationMessage = "Proposed gap fills from recommendation"
        } else {
            appliedRecommendationMessage = "Applied: \(rec.text)"
        }
    }

    /// Inserts a 15-minute break block at the current time, snapped to a 5-minute increment — parity
    /// with Android's `createQuickBlock(...)`, which places the quick block at
    /// `snapToIncrement(currentMinuteValue)` rather than after the last scheduled block. (The previous
    /// "after the last block" math also overflowed past 1440 for blocks that wrap past midnight.)
    private func insertBreakBlock() {
        let today = Calendar.current.startOfDay(for: .now)
        let comps = Calendar.current.dateComponents([.hour, .minute], from: .now)
        let currentMinute = (comps.hour ?? 9) * 60 + (comps.minute ?? 0)
        let start = min(max((currentMinute / 5) * 5, 0), 1440 - 15)
        let block = TimeBlock(
            date: today,
            title: "Break",
            category: "BREAK",
            startMinuteOfDay: start,
            durationMinutes: 15,
            energyLevel: .low,
            source: "insights-recommendation")
        modelContext.insert(block)
    }

    /// User-facing label for a recommendation's GenAI source. Mirrors Android's
    /// `GenAiAssistCopy.assistSourceLabel`.
    private func sourceLabel(_ source: AssistGenAiSource) -> String {
        switch source {
        case .local: return "On-device heuristics"
        case .geminiNano: return "On-device AI"
        case .cloudGemini: return "Cloud AI"
        }
    }

    /// Recent companion trends fed into the recommendation planner so suggestions reflect
    /// multi-day patterns. Derived from the local queries; oldest-first to match the core's math.
    private var trendSections: CompanionTrendSections {
        CompanionTrendSections(
            peakEnergyHour: peakEnergyHour,
            habitCompletion: habitDailyCompletions,
            medicationAdherence: medicationDailyAdherence)
    }

    /// Hour of day (0…23) with the highest average energy across recent check-ins, when known.
    private var peakEnergyHour: Int? {
        var totals: [Int: (sum: Int, count: Int)] = [:]
        for c in checkIns {
            let hour = Calendar.current.component(.hour, from: c.recordedAt)
            let e = totals[hour] ?? (0, 0)
            totals[hour] = (e.sum + c.energyScore, e.count + 1)
        }
        return totals.max { a, b in
            Double(a.value.sum) / Double(a.value.count) < Double(b.value.sum) / Double(b.value.count)
        }?.key
    }

    /// Per-day habit completion tallies over a trailing 14-day window, oldest-first.
    private var habitDailyCompletions: [HabitDailyCompletion] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        return (0..<14).reversed().compactMap { offset -> HabitDailyCompletion? in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            let completed = habits.filter { $0.isCompleted(on: day) }.count
            return HabitDailyCompletion(completedCount: completed,
                                        missedCount: max(habits.count - completed, 0))
        }
    }

    /// Per-day medication adherence tallies over a trailing 14-day window, oldest-first.
    private var medicationDailyAdherence: [MedicationDailyAdherence] {
        guard !medications.isEmpty else { return [] }
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        return (0..<14).reversed().compactMap { offset -> MedicationDailyAdherence? in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            var taken = 0, expected = 0
            for plan in medications {
                let perDay = max(plan.reminderMinutes.count, 1)
                expected += perDay
                let count = plan.takenAt.filter { cal.isDate($0, inSameDayAs: day) }.count
                taken += min(count, perDay)
            }
            return MedicationDailyAdherence(takenCount: taken, missedCount: max(expected - taken, 0))
        }
    }

    /// Seed recommendations from the deterministic local heuristic (no inference) so the card is
    /// never empty on appear and whenever the period changes.
    private func refreshRecommendationsBaseline() {
        let planner = InsightsRecommendationsPlanner()
        recommendations = planner.localRecommendations(
            summary: periodSummary, insights: findings, trends: trendSections)
    }

    /// Refresh recommendations, layering Foundation Models on top of the local baseline when
    /// on-device AI is available; otherwise this resolves to the deterministic baseline.
    private func refreshRecommendationsWithAI() async {
        guard !isRefreshingRecommendations else { return }
        isRefreshingRecommendations = true
        defer { isRefreshingRecommendations = false }
        let generator: AssistTextGenerator? = FoundationModelsRecommendationGenerator()
        let planner = InsightsRecommendationsPlanner(generator: generator)
        recommendations = await planner.suggest(
            summary: periodSummary, insights: findings, trends: trendSections)
    }

    // MARK: Screen time

    /// Native screen-time mirror of Android's `ScreenTimeCard`. iOS surfaces app usage via the
    /// Screen Time / DeviceActivity authorization flow; until that's granted this is an informational
    /// entry point so the section parity-matches Android's placement on the page.
    private var screenTimeCard: some View {
        ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "hourglass").font(.title2).foregroundStyle(ChronosColors.brandPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Screen time").font(.chronosHeadline)
                    Text("Track focused vs. distracting app time to protect your deep-work windows.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Adherence rate (shared with findings + trends)

    private var adherenceRate: Double {
        let window = 7
        let today = Calendar.current.startOfDay(for: .now)
        var taken = 0, expected = 0
        for plan in medications {
            let perDay = max(plan.reminderMinutes.count, 1)
            expected += perDay * window
            for offset in 0..<window {
                guard let day = Calendar.current.date(byAdding: .day, value: -offset, to: today) else { continue }
                let count = plan.takenAt.filter { Calendar.current.isDate($0, inSameDayAs: day) }.count
                taken += min(count, perDay)
            }
        }
        return expected > 0 ? Double(taken) / Double(expected) : 0
    }

    private func formatMins(_ m: Int) -> String {
        let v = max(m, 0)
        if v < 60 { return "\(v)m" }
        let h = v / 60, rem = v % 60
        return rem == 0 ? "\(h)h" : "\(h)h \(rem)m"
    }

    private func formatDrift(_ m: Int) -> String {
        let sign = m > 0 ? "+" : (m < 0 ? "−" : "")
        return "\(sign)\(formatMins(abs(m)))"
    }

    private func percent(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }

    // MARK: Day balance (mirrors ChronosCore.evaluateDayBalance)

    private var balance: (score: Int, overloaded: Bool, breakRatio: Double, demanding: Int, variety: Int) {
        let day = todayBlocks
        guard !day.isEmpty else { return (0, false, 0, 0, 0) }
        let total = day.reduce(0) { $0 + $1.durationMinutes }
        let breakMin = day.filter { ["BREAK", "MEAL"].contains($0.category.uppercased()) }.reduce(0) { $0 + $1.durationMinutes }
        let demanding = day.filter { $0.energyLevel.rawValue >= 4 }.reduce(0) { $0 + $1.durationMinutes }
        let variety = Set(day.map { $0.category.uppercased() }).count
        let breakRatio = total > 0 ? Double(breakMin) / Double(total) : 0
        let maxDemanding = 5 * 60, maxScheduled = 14 * 60
        let overloaded = demanding > maxDemanding || total > maxScheduled
        var score = 100.0
        if demanding > maxDemanding { score -= Double(demanding - maxDemanding) / 60.0 * 12.0 }
        if breakRatio < 0.10 { score -= (0.10 - breakRatio) * 200.0 }
        if variety < 3 { score -= Double(3 - variety) * 8.0 }
        if total > maxScheduled { score -= Double(total - maxScheduled) / 60.0 * 10.0 }
        return (Int(min(max(score, 0), 100).rounded()), overloaded, breakRatio, demanding, variety)
    }

    private var balanceCard: some View {
        let b = balance
        return ChronosGlassCard(tint: b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Label("Day balance", systemImage: b.overloaded ? "exclamationmark.triangle.fill" : "scalemass")
                        .font(.chronosHeadline)
                    Spacer()
                    Text("\(b.score)").font(.chronosTitle)
                        .foregroundStyle(b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary)
                }
                ProgressView(value: Double(b.score) / 100)
                    .tint(b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary)
                Text(b.overloaded
                     ? "Overloaded — \(b.demanding / 60)h of demanding work. Consider moving some to tomorrow."
                     : "Healthy mix · \(Int(b.breakRatio * 100))% breaks · \(b.variety) categories")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Best deep-work window (mirrors ChronosCore.bestDeepWorkWindow)

    private struct DayBand { let label: String; let range: ClosedRange<Int> }
    private static let bands: [DayBand] = [
        .init(label: "Early morning", range: 300...479),
        .init(label: "Morning", range: 480...659),
        .init(label: "Midday", range: 660...839),
        .init(label: "Afternoon", range: 840...1079),
        .init(label: "Evening", range: 1080...1319),
        .init(label: "Night", range: 1320...1439),
    ]

    private var bestWindow: (label: String, score: Double)? {
        var totals: [String: (sum: Double, count: Int)] = [:]
        for c in checkIns {
            let minute = minuteOfDay(c.recordedAt)
            guard let band = Self.bands.first(where: { $0.range.contains(minute) }) else { continue }
            let score = Double(c.energyScore + c.focusScore) / 2.0
            let e = totals[band.label] ?? (0, 0)
            totals[band.label] = (e.sum + score, e.count + 1)
        }
        return totals.filter { $0.value.count >= 2 }
            .map { (label: $0.key, score: $0.value.sum / Double($0.value.count)) }
            .max { $0.score < $1.score }
    }

    private func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func bestWindowCard(_ best: (label: String, score: Double)) -> some View {
        ChronosGlassCard(tint: ChronosColors.brandSecondary) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "scope").font(.title2).foregroundStyle(ChronosColors.brandSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Best deep-work window").font(.chronosCaption).foregroundStyle(.secondary)
                    Text(best.label).font(.chronosHeadline)
                    Text("Highest energy + focus from your check-ins").font(.chronosCaption).foregroundStyle(.secondary)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Medication adherence (mirrors ChronosCore.adherenceStats)

    private var adherenceCard: some View {
        let window = 7
        let today = Calendar.current.startOfDay(for: .now)
        var taken = 0, expected = 0
        for plan in medications {
            let perDay = max(plan.reminderMinutes.count, 1)
            expected += perDay * window
            for offset in 0..<window {
                guard let day = Calendar.current.date(byAdding: .day, value: -offset, to: today) else { continue }
                let count = plan.takenAt.filter { Calendar.current.isDate($0, inSameDayAs: day) }.count
                taken += min(count, perDay)
            }
        }
        let rate = expected > 0 ? Double(taken) / Double(expected) : 0
        return ChronosGlassCard(tint: ChronosColors.category("MEDICATION")) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Label("Medication adherence", systemImage: "pills.fill").font(.chronosHeadline)
                    Spacer()
                    Text("\(Int(rate * 100))%").font(.chronosTitle).foregroundStyle(ChronosColors.category("MEDICATION"))
                }
                ProgressView(value: rate).tint(ChronosColors.category("MEDICATION"))
                Text("Last \(window) days · \(taken)/\(expected) doses").font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Sleep → next-day mood correlation

    /// Average mood on days after a good night (quality >= 4) vs a poor night (quality <= 2).
    private var sleepMoodCorrelation: (good: Double, poor: Double)? {
        let nightByDay = Dictionary(nights.map { (Calendar.current.startOfDay(for: $0.date), $0) },
                                    uniquingKeysWith: { a, _ in a })
        var good: [Int] = [], poor: [Int] = []
        for c in checkIns {
            let day = Calendar.current.startOfDay(for: c.checkInDate)
            guard let night = nightByDay[day], night.sleepQuality > 0 else { continue }
            if night.sleepQuality >= 4 { good.append(c.moodScore) }
            else if night.sleepQuality <= 2 { poor.append(c.moodScore) }
        }
        guard !good.isEmpty, !poor.isEmpty else { return nil }
        return (Double(good.reduce(0, +)) / Double(good.count),
                Double(poor.reduce(0, +)) / Double(poor.count))
    }

    private func correlationCard(_ c: (good: Double, poor: Double)) -> some View {
        let delta = c.good - c.poor
        return ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Label("Sleep shapes your mood", systemImage: "moon.stars.fill").font(.chronosHeadline)
                Text(delta > 0.3
                     ? "After a good night your mood averages \(String(format: "%.1f", c.good))/5 — \(String(format: "%.1f", delta)) higher than after a poor night (\(String(format: "%.1f", c.poor))/5)."
                     : "Your mood is similar after good and poor nights so far.")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var habitChart: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Habit streaks").font(.chronosHeadline)
                Chart(habits) { habit in
                    BarMark(x: .value("Habit", habit.title), y: .value("Streak", habit.streakCount))
                        .foregroundStyle(ChronosColors.brandSecondary.gradient)
                        .cornerRadius(6)
                }
                .frame(height: 160)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Habit streaks bar chart")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var sleepChart: some View {
        let recent = nights.sorted { $0.date < $1.date }.suffix(7)
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Sleep (last 7 nights)").font(.chronosHeadline)
                Chart(Array(recent)) { night in
                    LineMark(x: .value("Day", night.date, unit: .day),
                             y: .value("Hours", Double(night.durationMinutes ?? 0) / 60))
                        .foregroundStyle(ChronosColors.brandPrimary)
                        .interpolationMethod(.catmullRom)
                    PointMark(x: .value("Day", night.date, unit: .day),
                              y: .value("Hours", Double(night.durationMinutes ?? 0) / 60))
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                .frame(height: 160)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Sleep hours over the last 7 nights")
                .accessibilityValue("Latest: \(String(format: "%.1f", Double(recent.last?.durationMinutes ?? 0) / 60)) hours")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var moodChart: some View {
        let recent = checkIns.sorted { $0.recordedAt < $1.recordedAt }.suffix(10)
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Mood & energy").font(.chronosHeadline)
                if recent.isEmpty {
                    Text("No check-ins yet").font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    Chart(Array(recent)) { c in
                        LineMark(x: .value("Time", c.recordedAt), y: .value("Mood", c.moodScore),
                                 series: .value("Series", "Mood"))
                            .foregroundStyle(ChronosColors.brandAccent)
                        LineMark(x: .value("Time", c.recordedAt), y: .value("Energy", c.energyScore),
                                 series: .value("Series", "Energy"))
                            .foregroundStyle(ChronosColors.brandSecondary)
                    }
                    .chartForegroundStyleScale(["Mood": ChronosColors.brandAccent,
                                                "Energy": ChronosColors.brandSecondary])
                    .frame(height: 160)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("Mood and energy over recent check-ins")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Journal history + evening reflection (Android: JournalTimelineSection / the
    // "Evening reflection" card in InsightsTrendSections)

    /// Caps the eagerly-rendered history rows, mirroring Android's `InlineHistoryCap`.
    private static let inlineHistoryCap = 10
    /// Trailing window for the history card — matches this screen's other 14-day trend windows.
    private static let journalWindowDays = 14

    /// Journal entries in the trailing window, newest first (Android: observeRecentJournalEntries).
    private var recentJournalEntries: [JournalEntry] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        guard let windowStart = cal.date(byAdding: .day, value: -(Self.journalWindowDays - 1), to: today)
        else { return [] }
        return journalEntries
            .filter { $0.entryDate >= windowStart }
            .sorted { $0.entryDate > $1.entryDate }
    }

    /// Today's reflection entry (primary preferred), backing the evening-reflection card.
    private var todayJournalEntry: JournalEntry? {
        let todays = journalEntries.filter { Calendar.current.isDateInToday($0.entryDate) }
        return todays.first(where: \.isPrimary) ?? todays.first
    }

    /// "N reflections in the last X days" / "Showing N of M …" — mirrors `journalHistorySummary`.
    private func journalHistorySummary(total: Int, shown: Int, windowDays: Int) -> String {
        let label = total == 1 ? "reflection" : "reflections"
        return shown < total
            ? "Showing \(shown) of \(total) \(label) from the last \(windowDays) days"
            : "\(total) \(label) in the last \(windowDays) days"
    }

    private var journalHistoryCard: some View {
        let entries = recentJournalEntries
        let shown = Array(entries.prefix(Self.inlineHistoryCap))
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Journal history").font(.chronosHeadline)
                ForEach(shown, id: \.id) { entry in
                    journalHistoryRow(entry)
                    if entry.id != shown.last?.id { Divider() }
                }
                Text(journalHistorySummary(total: entries.count, shown: shown.count,
                                           windowDays: Self.journalWindowDays))
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// One history row, expandable in place (Android: JournalTimelineEntryRow).
    private func journalHistoryRow(_ entry: JournalEntry) -> some View {
        let expanded = expandedJournalEntryIDs.contains(entry.id)
        return Button {
            // `_ =` keeps the withAnimation closure Void — Set.insert/remove return values
            // otherwise poison type inference (see ios-swiftui-typecheck-pitfalls).
            withAnimation(ChronosMotion.snappy) {
                if expanded {
                    _ = expandedJournalEntryIDs.remove(entry.id)
                } else {
                    _ = expandedJournalEntryIDs.insert(entry.id)
                }
            }
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.entryDate.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day()))
                    .font(.chronosCaption.weight(.semibold)).foregroundStyle(.secondary)
                Text(entry.body.trimmingCharacters(in: .whitespacesAndNewlines))
                    .font(.chronosBody)
                    .lineLimit(expanded ? nil : 4)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: expanded)
    }

    /// Today's reflection snippet + an Open/Write affordance into the journal composer.
    private var eveningReflectionCard: some View {
        let entry = todayJournalEntry
        return ChronosGlassCard {
            HStack(spacing: ChronosSpacing.compact) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Evening reflection").font(.chronosHeadline)
                    Text(entry.flatMap { $0.body.isEmpty ? nil : $0.body } ?? "No entry for this day yet.")
                        .font(.chronosCaption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer()
                Button(entry != nil ? "Open" : "Write") { showingJournalEditor = true }
                    .buttonStyle(.bordered)
                    .clipShape(Capsule())
                    .font(.chronosLabel)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Sleep-log affordance (Android: the "Sleep" card with Update / Log sleep)

    /// The night logged for today, backing the sleep card's summary.
    private var todaySleepTrack: SleepTrack? {
        nights.first { Calendar.current.isDateInToday($0.date) }
    }

    private var sleepLogCard: some View {
        let night = todaySleepTrack
        return ChronosGlassCard {
            HStack(spacing: ChronosSpacing.compact) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sleep").font(.chronosHeadline)
                    if let night {
                        Text(sleepSummaryLine(night))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                        if night.refreshedRating > 0 {
                            Text("Felt \(SleepEmoji.refreshedEmoji(night.refreshedRating)) \(SleepEmoji.refreshedLabel(night.refreshedRating)) on waking")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        if night.source == .healthKit {
                            Text("From HealthKit")
                                .font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
                        }
                    } else {
                        Text("Last night not logged yet.")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Button(night != nil ? "Update" : "Log sleep") { showingSleepLog = true }
                    .buttonStyle(.bordered)
                    .clipShape(Capsule())
                    .font(.chronosLabel)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// "Quality X/5 · 11:00 PM – 7:00 AM · N interruptions" — mirrors the Android sleep-card line.
    private func sleepSummaryLine(_ night: SleepTrack) -> String {
        let window = [night.actualStartMinute, night.actualEndMinute]
            .compactMap { $0.map(formatDisplayMinute) }
            .joined(separator: " – ")
        var line = "Quality \(night.sleepQuality)/5 · \(window.isEmpty ? "times not set" : window)"
        if night.interruptedCount > 0 { line += " · \(night.interruptedCount) interruptions" }
        return line
    }
}

// MARK: - Foundation Models recommendation generator

/// Bridges ChronosCore's `AssistTextGenerator` to Apple's on-device Foundation Models. When the
/// model is unavailable (or generation fails) it returns a nil text so the planner falls back to
/// its deterministic local baseline. Attributes successful generations to `.geminiNano` — the
/// on-device source bucket — matching the Android "on-device AI" labeling.
private struct FoundationModelsRecommendationGenerator: AssistTextGenerator {
    func generateAssistText(prompt: String) async -> AssistTextGeneration {
        guard case .available = SystemLanguageModel.default.availability else {
            return AssistTextGeneration(text: nil, source: .local)
        }
        let session = LanguageModelSession(
            instructions: Instructions {
                "You generate concise schedule recommendations from supplied metrics only. "
                + "Return one recommendation per line as recommendation|reason. "
                + "No invented tasks or medical advice."
            })
        do {
            let response = try await session.respond(
                to: prompt,
                options: GenerationOptions(temperature: GenerationProfile.balanced.temperature))
            let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            return AssistTextGeneration(text: text.isEmpty ? nil : text, source: .geminiNano)
        } catch {
            return AssistTextGeneration(text: nil, source: .local)
        }
    }
}

/// The detailed planned-vs-actual day-review drilldown sheet.
struct DayReviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    let blocks: [TimeBlock]

    var body: some View {
        NavigationStack {
            List(blocks.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }) { block in
                HStack {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(ChronosColors.category(block.category)).frame(width: 4, height: 36)
                    VStack(alignment: .leading) {
                        Text(block.title).font(.chronosLabel)
                        Text("Planned \(block.startMinuteOfDay.clockTime)–\(block.plannedEndMinuteOfDay.clockTime)")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if let s = block.actualStartMinuteOfDay, let e = block.actualEndMinuteOfDay {
                        Text("Actual \(s.clockTime)–\(e.clockTime)").font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandSecondary)
                    } else {
                        Text("Not logged").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Day review")
            .toolbarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

#Preview { InsightsView().modelContainer(ChronosStore.previewContainer()) }
